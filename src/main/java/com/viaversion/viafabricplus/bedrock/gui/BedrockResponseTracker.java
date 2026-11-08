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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks the server's {@code ItemStackResponse} answers (packet id 148) for the container translation.
 * <p>
 * ViaBedrock does not read this packet, but it is the only source for two things the GUI translation
 * needs:
 * <ul>
 *     <li>the stack network id of slots ViaBedrock cannot track (result slots such as the anvil result,
 *     and everything the server creates - crafted or creative output), because those live outside the
 *     tracked container arrays;</li>
 *     <li>the confirmation that a request was accepted - and, for crafting / creative actions, the moment
 *     the created output actually exists, so the follow-up {@code Take} can reference its new network id.</li>
 * </ul>
 * Everything here is defensive: if the packet can't be parsed (protocol revision mismatch), tracking is
 * disabled for the session and the translation falls back to the network ids from ViaBedrock's containers.
 */
public final class BedrockResponseTracker extends StoredObject {

    private static final long PENDING_TIMEOUT_MS = 5_000L;

    private static volatile boolean registered = false;

    private final Map<SlotKey, Integer> stackNetworkIds = new HashMap<>();
    private final Map<Integer, PendingCraft> pendingCrafts = new HashMap<>();
    private boolean parsingDisabled = false;

    public BedrockResponseTracker(final UserConnection user) {
        super(user);
    }

    /**
     * Registers a handler for {@code ItemStackResponse} in the Bedrock protocol. Idempotent.
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

            protocol.registerClientbound(ClientboundBedrockPackets.ITEM_STACK_RESPONSE, null, wrapper -> {
                final BedrockItemStackResponse[] responses;
                try {
                    responses = wrapper.read(BedrockGuiTypes.ITEM_STACK_RESPONSE_ARRAY);
                } catch (final Throwable throwable) {
                    final BedrockResponseTracker tracker = of(wrapper.user());
                    if (!tracker.parsingDisabled) {
                        tracker.parsingDisabled = true;
                        ViaFabricPlusBedrock.impl().logger().warn("Failed to parse Bedrock ItemStackResponse, result slot tracking is disabled", throwable);
                    }
                    return;
                }

                of(wrapper.user()).handle(responses);
            });
            registered = true;
        } catch (final Throwable throwable) {
            ViaFabricPlusBedrock.impl().logger().warn("Failed to register the Bedrock ItemStackResponse handler", throwable);
        }
    }

    public static BedrockResponseTracker of(final UserConnection user) {
        BedrockResponseTracker tracker = user.get(BedrockResponseTracker.class);
        if (tracker == null) {
            tracker = new BedrockResponseTracker(user);
            user.put(tracker);
        }
        return tracker;
    }

    /**
     * @return the stack network id the server reported for the given slot, or {@code null} if unknown
     */
    public @Nullable Integer stackNetworkId(final ContainerEnumName container, final int slot) {
        if (this.parsingDisabled) {
            return null;
        }
        return this.stackNetworkIds.get(new SlotKey(container, slot));
    }

    /**
     * Queues the follow-up action for a crafting / creative request. It is sent as soon as the server
     * confirmed that request and reported the freshly created output stack.
     *
     * @param requestId the id of the request that creates the item
     * @param pending   the follow-up description
     */
    public void addPendingCraft(final int requestId, final PendingCraft pending) {
        this.pendingCrafts.put(requestId, pending);
    }

    private void handle(final BedrockItemStackResponse[] responses) {
        for (final BedrockItemStackResponse response : responses) {
            if (response.status() != BedrockItemStackResponse.Status.OK) {
                // Rejected: we never predict clicks locally, so nothing has to be rolled back. Only the
                // cached network ids of the involved containers may be stale now.
                this.pendingCrafts.remove(response.requestId());
                if (isDebug()) {
                    ViaFabricPlusBedrock.impl().logger().info("Bedrock item stack request #{} was rejected", response.requestId());
                }
                continue;
            }

            for (final BedrockItemStackResponse.ContainerEntry container : response.containers()) {
                for (final BedrockItemStackResponse.SlotEntry slot : container.slots()) {
                    final SlotKey key = new SlotKey(container.container().name(), slot.slot());
                    if (slot.isEmpty()) {
                        this.stackNetworkIds.remove(key);
                    } else {
                        this.stackNetworkIds.put(key, slot.stackNetworkId());
                    }
                }
            }

            final PendingCraft pending = this.pendingCrafts.remove(response.requestId());
            if (pending != null) {
                this.runPendingCraft(pending, response);
            }
        }

        // Drop follow-ups the server never answered for
        final long now = System.currentTimeMillis();
        final Iterator<Map.Entry<Integer, PendingCraft>> iterator = this.pendingCrafts.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().createdAt() > PENDING_TIMEOUT_MS) {
                iterator.remove();
            }
        }
    }

    private void runPendingCraft(final PendingCraft pending, final BedrockItemStackResponse response) {
        final BedrockItemStackResponse.SlotEntry created = findSlot(response, pending.sourceContainer(), pending.sourceSlot());
        if (created == null || created.isEmpty()) {
            if (isDebug()) {
                ViaFabricPlusBedrock.impl().logger().info("No created output found in response #{} for {}", response.requestId(), pending.sourceContainer());
            }
            return;
        }

        final int amount = Math.max(1, Math.min(pending.count(), created.count()));
        final BedrockStackRef source = new BedrockStackRef(pending.sourceContainer(), pending.sourceSlot(), created.stackNetworkId());
        BedrockGuiSender.send(this.user(), pending.session(), List.of(new BedrockItemStackRequest.Take(amount, source, pending.destination())));
    }

    private static @Nullable BedrockItemStackResponse.SlotEntry findSlot(final BedrockItemStackResponse response, final ContainerEnumName container, final int slot) {
        for (final BedrockItemStackResponse.ContainerEntry entry : response.containers()) {
            if (entry.container().name() != container) {
                continue;
            }
            for (final BedrockItemStackResponse.SlotEntry slotEntry : entry.slots()) {
                if (slotEntry.slot() == slot) {
                    return slotEntry;
                }
            }
        }
        return null;
    }

    private static boolean isDebug() {
        return ViaFabricPlusBedrock.impl() != null && ViaFabricPlusBedrock.impl().settings().containerTranslationDebug().isActive();
    }

    private record SlotKey(ContainerEnumName container, int slot) {
    }

    /**
     * A follow-up request that is only valid once the server created the output stack: the created stack
     * gets a brand new stack network id, so it can only be referenced after the response arrived.
     *
     * @param sourceContainer the container the created stack appears in (result / created output)
     * @param sourceSlot      the slot inside that container (usually 50)
     * @param destination     where the created stack should end up (cursor or a player inventory slot)
     * @param count           how many items to take
     * @param session         the session to send the follow-up with
     * @param createdAt       creation timestamp, used to expire lost follow-ups
     */
    public record PendingCraft(ContainerEnumName sourceContainer, int sourceSlot, BedrockStackRef destination, int count,
                               BedrockGuiSession session, long createdAt) {

        public static PendingCraft of(final ContainerEnumName sourceContainer, final int sourceSlot, final BedrockStackRef destination,
                                      final int count, final BedrockGuiSession session) {
            return new PendingCraft(sourceContainer, sourceSlot, destination, count, session, System.currentTimeMillis());
        }

    }

}
