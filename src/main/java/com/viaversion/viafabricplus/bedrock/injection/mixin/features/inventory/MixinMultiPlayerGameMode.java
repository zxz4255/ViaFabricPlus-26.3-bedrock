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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.inventory;

import com.viaversion.viafabricplus.bedrock.gui.BedrockGuiBridge;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts every container click while connected to a Bedrock server and translates it into a Bedrock
 * {@code ItemStackRequest}. When the translation succeeds the vanilla {@code ServerboundContainerClickPacket}
 * is never sent (and therefore also never translated/dropped by ViaBedrock), otherwise the vanilla
 * behaviour is kept.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void translateToBedrockItemStackRequest(final int containerId, final int slotNum, final int buttonNum, final ContainerInput containerInput, final Player player, final CallbackInfo ci) {
        if (BedrockGuiBridge.handleContainerInput(containerId, slotNum, buttonNum, containerInput)) {
            ci.cancel();
        }
    }

}
