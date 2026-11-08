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

import java.util.List;
import net.raphimc.viabedrock.protocol.model.FullContainerName;

/**
 * The server's answer to one {@link BedrockItemStackRequest} ({@code ItemStackResponse}, packet id 148).
 * <p>
 * Every request is answered with one response that either acknowledges the request ({@link Status#OK})
 * or rejects it. For acknowledged requests the response contains the containers that changed, including
 * the new stack network ids - which is the only way to learn the network id of items the server created
 * (crafted / creative output) or of slots ViaBedrock does not track (e.g. the anvil result slot).
 * <p>
 * Wire format (Cloudburst Protocol 3.0, protocol 1001 / 1.26.30: {@code ItemStackResponseSerializer_v419}
 * and {@code BedrockCodecHelper_v766#readItemEntry}). At this protocol the containers are only present
 * when the status is OK (there is no "has containers" flag), and a slot entry always carries its network
 * id and both names:
 * <pre>
 *   array&lt;unsigned var int&gt;:
 *     status                 byte
 *     requestId              var int (zig-zag)
 *     [containers]           array&lt;unsigned var int&gt;  (only when status is OK)
 *         container          full container name
 *         slots              array&lt;unsigned var int&gt;:
 *             slot               byte
 *             hotbarSlot         byte
 *             count              byte
 *             stackNetworkId     var int (zig-zag)
 *             customName         string
 *             filteredName       string
 *             durabilityCorrection var int (zig-zag)
 * </pre>
 */
public record BedrockItemStackResponse(Status status, int requestId, List<ContainerEntry> containers) {

    public BedrockItemStackResponse {
        containers = List.copyOf(containers);
    }

    public enum Status {
        OK,
        ERROR
    }

    public record ContainerEntry(FullContainerName container, List<SlotEntry> slots) {

        public ContainerEntry {
            slots = List.copyOf(slots);
        }

    }

    public record SlotEntry(int slot, int count, int stackNetworkId) {

        public boolean isEmpty() {
            return this.count <= 0 || this.stackNetworkId <= 0;
        }

    }

}
