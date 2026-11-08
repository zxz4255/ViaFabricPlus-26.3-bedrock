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

import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.model.FullContainerName;

/**
 * A single Bedrock inventory slot as referenced by an item stack request.
 * <p>
 * This is the Bedrock equivalent of Minecraft's "slot index": the pair of a container (as
 * {@link ContainerEnumName}, which is the same enum as Cloudburst Protocol's {@code ContainerSlotType}
 * from Protocol-3.0) and the slot index inside that container. Every slot also has to carry the
 * stack network id the client assumes to be present in that slot - the server rejects the whole
 * request if it doesn't match its own state.
 *
 * @param container       the container the slot belongs to
 * @param slot            the slot index inside the container
 * @param stackNetworkId  the stack network id the client assumes (0 for empty slots)
 */
public record BedrockStackRef(ContainerEnumName container, int slot, int stackNetworkId) {

    /**
     * The slot of the item the player is currently holding with the cursor. In Bedrock this is
     * slot 0 of the "player only UI" (HUD) container.
     */
    public static BedrockStackRef cursor(final int stackNetworkId) {
        return new BedrockStackRef(ContainerEnumName.CursorContainer, 0, stackNetworkId);
    }

    public static BedrockStackRef of(final ContainerEnumName container, final int slot) {
        return new BedrockStackRef(container, slot, 0);
    }

    public FullContainerName containerName() {
        return new FullContainerName(this.container, null);
    }

    public BedrockStackRef withStackNetworkId(final int stackNetworkId) {
        return new BedrockStackRef(this.container, this.slot, stackNetworkId);
    }

    @Override
    public String toString() {
        return this.container.name() + "[" + this.slot + "]@" + this.stackNetworkId;
    }

}
