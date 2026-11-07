/*
 * This file is part of ViaFabricPlus - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.gui;

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.StoredObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import org.jetbrains.annotations.Nullable;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

/**
 * Maps item identifiers to the creative item network ids the Bedrock server announced in
 * {@code CreativeContent} (packet id 145).
 * <p>
 * The Java creative mode middle click ({@code ContainerInput.CLONE}) has no direct Bedrock
 * counterpart: the Bedrock client asks the server to <i>create</i> the item with a {@code CraftCreative}
 * action that references the creative item network id of the item, after which the server puts the new
 * stack into the virtual {@code CreatedOutput} container and the client takes it from there (that is
 * exactly the action sequence {@code PlayerInventoryTranslator#translateCreativeRequest} of Geyser
 * expects from a Bedrock client). ViaBedrock ignores {@code CreativeContent}, so the ids are collected
 * here.
 * <p>
 * Packet layout (Cloudburst Protocol {@code CreativeContentSerializer_v776}, protocol 1001):
 * <pre>
 *   groups   array&lt;unsigned var int&gt; of { category int LE, name string, icon item instance }
 *   contents array&lt;unsigned var int&gt; of { creative net id unsigned var int, item item instance, group id unsigned var int }
 * </pre>
 */
public final class BedrockCreativeItems extends StoredObject {

    private static volatile boolean registered = false;

    /**
     * Item identifier (without namespace) to creative network ids, keyed by data value and ordered by
     * descending stack size so that the "best" entry for an item is found first.
     */
    private final Map<String, Map<Integer, Integer>> creativeIds = new HashMap<>();
    private boolean loaded = false;
    private boolean parsingDisabled = false;

    public BedrockCreativeItems(final UserConnection user) {
        super(user);
    }

    /**
     * Registers the {@code CreativeContent} handler in the Bedrock protocol. Idempotent.
     */
    public static synchronized void ensureRegistered() {
        if (registered) {
            return;
        }

        try {
            final BedrockProtocol protocol = Via.getManager().getProtocolManager().getProtocol(BedrockProtocol.class);
            if (protocol == null) {
                return;
            }

            protocol.registerClientbound(ClientboundBedrockPackets.CREATIVE_CONTENT, null, wrapper -> {
                final BedrockCreativeItems creativeItems = of(wrapper.user());
                if (creativeItems.parsingDisabled) {
                    return;
                }
                try {
                    creativeItems.read(Unpooled.wrappedBuffer(wrapper.read(Types.REMAINING_BYTES)), wrapper.user());
                } catch (final Throwable throwable) {
                    creativeItems.parsingDisabled = true;
                    ViaFabricPlusBedrock.impl().logger().warn("Failed to parse Bedrock CreativeContent, creative item cloning is disabled", throwable);
                }
            });
            registered = true;
        } catch (final Throwable throwable) {
            ViaFabricPlusBedrock.impl().logger().warn("Failed to register the Bedrock CreativeContent handler", throwable);
        }
    }

    public static BedrockCreativeItems of(final UserConnection user) {
        BedrockCreativeItems items = user.get(BedrockCreativeItems.class);
        if (items == null) {
            items = new BedrockCreativeItems(user);
            user.put(items);
        }
        return items;
    }

    private void read(final ByteBuf buffer, final UserConnection user) {
        final ItemRewriter rewriter = user.get(ItemRewriter.class);
        final Map<Integer, String> runtimeIdToIdentifier = new HashMap<>();
        if (rewriter != null) {
            rewriter.getItems().forEach((identifier, runtimeId) -> runtimeIdToIdentifier.put(runtimeId, identifier));
        }

        this.creativeIds.clear();

        // Groups: only relevant for the Bedrock creative screen itself
        final int groupCount = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
        for (int group = 0; group < groupCount; group++) {
            buffer.readIntLE(); // category (CreativeItemCategory ordinal, int LE at protocol 1001)
            BedrockTypes.STRING.read(buffer); // name
            skipItemInstance(buffer); // icon
        }

        final int itemCount = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
        for (int index = 0; index < itemCount; index++) {
            final int creativeNetId = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
            final BedrockItemData.Instance item = BedrockItemData.readItemInstance(buffer);
            BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer); // group id

            if (item.isEmpty()) {
                continue;
            }

            String identifier = runtimeIdToIdentifier.get(item.runtimeId());
            if (identifier == null) {
                continue; // item not part of ViaBedrock's item palette
            }
            if (identifier.startsWith("minecraft:")) {
                identifier = identifier.substring("minecraft:".length());
            }

            this.creativeIds.computeIfAbsent(identifier, key -> new HashMap<>()).put(item.aux(), creativeNetId);
        }

        this.loaded = true;
        if (ViaFabricPlusBedrock.impl() != null && ViaFabricPlusBedrock.impl().settings().containerTranslationDebug().isActive()) {
            ViaFabricPlusBedrock.impl().logger().info("Parsed {} creative item identifiers from Bedrock CreativeContent", this.creativeIds.size());
        }
    }

    /**
     * @return the creative item network id of the given Java stack, or {@code null} if the server did
     * not announce that item (or the packet was not received yet)
     */
    public @Nullable Integer creativeNetId(final ItemStack stack) {
        if (!this.loaded || this.parsingDisabled || stack.isEmpty()) {
            return null;
        }

        String identifier = BedrockRecipeBook.identifier(stack);
        if (identifier.startsWith("minecraft:")) {
            identifier = identifier.substring("minecraft:".length());
        }

        final Map<Integer, Integer> byAux = this.creativeIds.get(identifier);
        if (byAux == null) {
            return null;
        }

        final Integer exact = byAux.get(0); // Java stacks always use data value 0
        if (exact != null) {
            return exact;
        }
        return byAux.values().stream().findFirst().orElse(null);
    }

    public boolean isLoaded() {
        return this.loaded && !this.parsingDisabled;
    }

    private static void skipItemInstance(final ByteBuf buffer) {
        BedrockItemData.skipItemInstance(buffer);
    }

    @Override
    public String toString() {
        return "BedrockCreativeItems[identifiers=" + this.creativeIds.size() + ", loaded=" + this.loaded + "]";
    }

}
