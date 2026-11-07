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
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import java.util.List;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;

/**
 * Sends {@code ItemStackRequest} packets to the Bedrock server.
 * <p>
 * ViaBedrock never sends this packet (its inventory handling is receive-only for item stack requests),
 * which is why the packet type has to be registered into {@link BedrockProtocol} at runtime before the
 * first request is sent - otherwise the protocol pipeline can't resolve the packet type when encoding.
 * The registration is idempotent and safe to call from any thread.
 */
public final class BedrockGuiSender {

    private static volatile boolean packetTypeRegistered = false;

    private BedrockGuiSender() {
    }

    /**
     * Registers the {@code ItemStackRequest} packet type in the Bedrock protocol. This is a no-op after
     * the first successful call.
     */
    public static synchronized void ensurePacketTypeRegistered() {
        if (packetTypeRegistered) {
            return;
        }

        try {
            final BedrockProtocol protocol = Via.getManager().getProtocolManager().getProtocol(BedrockProtocol.class);
            if (protocol == null) {
                return; // Protocol not loaded yet, try again on the next request
            }

            // Register the packet id as a pass-through so that the protocol pipeline knows the packet
            // when encoding it. The handler is a no-op: this direction is only ever used for sending.
            final int packetId = ServerboundBedrockPackets.ITEM_STACK_REQUEST.getId();
            protocol.registerServerbound(State.PLAY, packetId, packetId, wrapper -> {
            }, false);
            packetTypeRegistered = true;
        } catch (final Throwable throwable) {
            // Not fatal: sending will then simply be skipped instead of spamming the log with errors
            ViaFabricPlusBedrock.impl().logger().warn("Failed to register the Bedrock ItemStackRequest packet type", throwable);
        }
    }

    /**
     * Sends the given actions as one item stack request.
     *
     * @param user    the connection to the Bedrock server
     * @param session the per-connection GUI session, used for the request id
     * @param actions the actions to send; no packet is sent when the list is empty
     */
    public static void send(final UserConnection user, final BedrockGuiSession session, final List<BedrockItemStackRequest.Action> actions) {
        if (actions.isEmpty()) {
            return;
        }

        ensurePacketTypeRegistered();
        if (!packetTypeRegistered) {
            return;
        }

        final BedrockItemStackRequest request = new BedrockItemStackRequest(session.nextRequestId(), actions);
        if (ViaFabricPlusBedrock.impl() != null && ViaFabricPlusBedrock.impl().settings().containerTranslationDebug().isActive()) {
            ViaFabricPlusBedrock.impl().logger().info("Bedrock item stack request #{}: {}", request.requestId(), actions);
        }

        final PacketWrapper wrapper = PacketWrapper.create(ServerboundBedrockPackets.ITEM_STACK_REQUEST, user);
        wrapper.write(BedrockGuiTypes.ITEM_STACK_REQUEST_ARRAY, new BedrockItemStackRequest[]{request});
        wrapper.sendToServer(BedrockProtocol.class);
    }

}
