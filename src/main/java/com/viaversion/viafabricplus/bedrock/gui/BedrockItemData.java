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

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.jetbrains.annotations.Nullable;

/**
 * Readers for the Bedrock item structures that appear inside {@code CraftingData} and
 * {@code CreativeContent}.
 * <p>
 * Target protocol: <b>Bedrock 1.26.30 / protocol 1001</b> (ViaBedrock 0.0.29). The layouts are the
 * ones Cloudburst Protocol 3.0 uses for that protocol, i.e. the newest overrides that are still
 * {@code <= 1001}:
 * <ul>
 *     <li>{@code BedrockCodecHelper_v431#readItemInstance} - crafting / creative item stacks,</li>
 *     <li>{@code BedrockCodecHelper_v554#readItemDescriptor} + {@code #readIngredient} - recipe
 *     requirements.</li>
 * </ul>
 * Two details are easy to get wrong and are therefore spelled out again:
 * <ul>
 *     <li>a {@code runtimeId} of {@code 0} (air) ends the structure - <b>no further bytes follow</b>,</li>
 *     <li>the item descriptor of a recipe ingredient is a <b>number</b> (runtime id) at 1001; it only
 *     became a string identifier in the 1.26.4x line.</li>
 * </ul>
 * Only the fields the GUI translation needs are materialised; the item user data (NBT + can place /
 * can break lists) is skipped as one length prefixed slice, so the reader stays aligned without
 * having to understand NBT.
 */
public final class BedrockItemData {

    /**
     * A recipe requirement: the item the recipe expects plus how many of it a single craft consumes.
     *
     * @param identifier the Bedrock item identifier (for example {@code minecraft:oak_planks}),
     *                   {@code null} when the descriptor did not name a concrete item
     * @param aux        the required data value; {@code 0x7fff} is Bedrock's "any data value" wildcard
     * @param count      how many items one craft consumes
     * @param wildcard   {@code true} for descriptors that match any non-empty slot (molang expressions,
     *                   item tags and the invalid descriptor); {@code false} means the slot has to be
     *                   empty when {@code identifier} is {@code null}
     */
    public record Ingredient(@Nullable String identifier, int aux, int count, boolean wildcard) {

        /**
         * Bedrock's wildcard data value, meaning "any variant of this item".
         */
        public static final int WILDCARD_AUX = 0x7FFF;

        /**
         * @return an ingredient that only asks for an empty slot
         */
        public static Ingredient empty(final int count) {
            return new Ingredient(null, 0, count, false);
        }

        /**
         * @return an ingredient that accepts any non-empty item (molang / tag / invalid descriptor)
         */
        public static Ingredient any(final int count) {
            return new Ingredient(null, 0, count, true);
        }

        /**
         * @param itemIdentifier the identifier of the stack in the slot, {@code null} for an empty slot
         * @param itemAux        the data value of that stack
         * @return whether the stack satisfies this requirement
         */
        public boolean matches(final @Nullable String itemIdentifier, final int itemAux) {
            if (this.wildcard) {
                return itemIdentifier != null;
            }
            if (this.identifier == null) {
                return itemIdentifier == null; // "air" requirement: the slot has to be empty
            }
            if (itemIdentifier == null || !equalsIdentifier(this.identifier, itemIdentifier)) {
                return false;
            }
            return this.aux == WILDCARD_AUX || this.aux == itemAux;
        }

    }

    /**
     * A materialised item stack of a Bedrock item structure.
     *
     * @param runtimeId the Bedrock item runtime id (0 = air)
     * @param count     the stack size
     * @param aux       the data value
     * @param netId     the stack network id, 0 if the structure carries none
     */
    public record Instance(int runtimeId, int count, int aux, int netId) {

        /**
         * Air, which is also what an all-zero structure decodes to.
         */
        public static final Instance AIR = new Instance(0, 0, 0, 0);

        public boolean isEmpty() {
            return this.runtimeId == 0 || this.count <= 0;
        }

    }

    private BedrockItemData() {
    }

    /**
     * Reads a {@code NetworkItemInstanceDescriptor} (crafting results, creative content) as
     * {@code BedrockCodecHelper_v431#readItemInstance} does for protocol 1001.
     */
    public static Instance readItemInstance(final ByteBuf buffer) {
        final int runtimeId = BedrockTypes.VAR_INT.read(buffer);
        if (runtimeId == 0) {
            // Air ends the structure, nothing else follows
            return Instance.AIR;
        }

        final int count = buffer.readUnsignedShortLE();
        final int aux = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
        BedrockTypes.VAR_INT.read(buffer); // block runtime id
        skipUserData(buffer);
        return new Instance(runtimeId, count, aux, 0);
    }

    /**
     * Skips a {@code NetworkItemInstanceDescriptor} without materialising it.
     */
    public static void skipItemInstance(final ByteBuf buffer) {
        if (BedrockTypes.VAR_INT.read(buffer) == 0) {
            return; // air
        }
        buffer.skipBytes(2); // count
        BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer); // aux
        BedrockTypes.VAR_INT.read(buffer); // block runtime id
        skipUserData(buffer);
    }

    /**
     * Reads a recipe ingredient ({@code ItemDescriptorWithCount}) as
     * {@code BedrockCodecHelper_v554#readIngredient} does for protocol 1001.
     * <p>
     * The descriptor starts with an <b>unsigned byte</b> selecting the descriptor type
     * ({@code ItemDescriptorType}: 0 invalid, 1 default/name, 2 molang, 3 item tag, 4 deferred,
     * 5 complex alias) followed by the descriptor body and finally the stack size as a zig-zag var int.
     *
     * @param runtimeIds runtime id to identifier lookup, used to resolve the numeric item id of a
     *                   default descriptor (may return {@code null} for unknown ids)
     */
    public static Ingredient readIngredient(final ByteBuf buffer, final IntFunction<@Nullable String> runtimeIds) {
        final int descriptorType = buffer.readUnsignedByte();

        switch (descriptorType) {
            case 1 -> { // DEFAULT: numeric runtime id + data value
                final int runtimeId = buffer.readShortLE();
                final int aux = runtimeId != 0 ? buffer.readShortLE() : 0;
                final int count = BedrockTypes.VAR_INT.read(buffer);
                if (runtimeId == 0) {
                    return Ingredient.empty(count); // "minecraft:air" requirement
                }
                final String identifier = runtimeIds.apply(runtimeId);
                // Unknown runtime ids never match instead of silently matching everything
                return new Ingredient(identifier != null ? identifier : "#unknown:" + runtimeId, aux, count, false);
            }
            case 2 -> { // MOLANG: expression + version
                BedrockTypes.STRING.read(buffer);
                buffer.readUnsignedByte();
                return Ingredient.any(BedrockTypes.VAR_INT.read(buffer));
            }
            case 3 -> { // ITEM_TAG
                BedrockTypes.STRING.read(buffer);
                return Ingredient.any(BedrockTypes.VAR_INT.read(buffer));
            }
            case 4 -> { // DEFERRED: identifier + aux
                BedrockTypes.STRING.read(buffer);
                buffer.readShortLE();
                return Ingredient.any(BedrockTypes.VAR_INT.read(buffer));
            }
            case 5 -> { // COMPLEX_ALIAS
                BedrockTypes.STRING.read(buffer);
                return Ingredient.any(BedrockTypes.VAR_INT.read(buffer));
            }
            default -> { // 0 = INVALID: only carries an aux value and matches everything
                BedrockTypes.VAR_INT.read(buffer);
                return Ingredient.any(BedrockTypes.VAR_INT.read(buffer));
            }
        }
    }

    /**
     * Compares two Bedrock item identifiers, ignoring a leading {@code minecraft:} namespace.
     */
    public static boolean equalsIdentifier(final String first, final String second) {
        return normalize(first).equals(normalize(second));
    }

    private static String normalize(final String identifier) {
        return identifier.startsWith("minecraft:") ? identifier.substring("minecraft:".length()) : identifier;
    }

    private static void skipUserData(final ByteBuf buffer) {
        final int length = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
        buffer.skipBytes(length);
    }

}
