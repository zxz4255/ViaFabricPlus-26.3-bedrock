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

import com.viaversion.viaversion.api.connection.UserConnection;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a Java menu slot index into the Bedrock side state needed to build an item stack request:
 * the {@link BedrockStackRef} (Bedrock container + index + stack network id that the server expects)
 * together with the current {@link ItemStack} the client shows in that slot.
 * <p>
 * The Java item stack is used for all "game logic" decisions of the click translation (stack sizes,
 * item equality, ...), while the Bedrock reference provides the wire level information. Both sides
 * are kept in sync by ViaBedrock: it forwards every Bedrock {@code InventorySlot} /
 * {@code InventoryContent} packet to the Java menu, so the Java state is the mirror of the server
 * state and the stack network ids come from the tracked Bedrock items.
 */
public final class BedrockGuiSlots {

    /**
     * A Java menu slot resolved to both worlds.
     *
     * @param ref       the Bedrock slot reference (container, index and stack network id)
     * @param item      the item stack the client shows in that slot
     * @param result    whether this is an output slot (take only)
     * @param craftable whether the slot's content is produced by a crafting action ({@code CraftRecipe})
     *                  instead of already being present in a result container
     */
    public record ResolvedSlot(BedrockStackRef ref, ItemStack item, boolean result, boolean craftable) {

        public boolean isEmpty() {
            return this.item.isEmpty();
        }

        public int count() {
            return this.item.getCount();
        }

        public int maxStackSize() {
            return this.item.getMaxStackSize();
        }

    }

    private BedrockGuiSlots() {
    }

    /**
     * @return the resolved slot, or {@code null} if the slot can't be translated (unsupported menus)
     */
    public static @Nullable ResolvedSlot resolve(final UserConnection user, final AbstractContainerMenu menu, final int javaSlot) {
        if (javaSlot < 0 || javaSlot >= menu.slots.size()) {
            return null;
        }

        final InventoryTracker tracker = user.get(InventoryTracker.class);
        final ItemStack item = menu.slots.get(javaSlot).getItem();
        if (menu instanceof InventoryMenu) {
            return resolvePlayerInventorySlot(user, tracker, javaSlot, item);
        }

        final BedrockContainerLayout layout = BedrockContainerLayout.of(menu);
        if (layout == null) {
            return null;
        }

        if (javaSlot >= layout.containerSize()) {
            // Player inventory part of the menu: [containerSize, containerSize + 27) = main inventory,
            // [containerSize + 27, containerSize + 36) = hotbar
            final int relative = javaSlot - layout.containerSize();
            if (relative < 27) {
                return resolveInventorySlot(user, tracker, relative + 9, item);
            } else if (relative < 36) {
                return resolveHotbarSlot(user, tracker, relative - 27, item);
            }
            return null;
        }

        final BedrockContainerLayout.ContainerSlot containerSlot = layout.containerSlot(javaSlot);
        if (containerSlot == null) {
            return null;
        }

        final Container container = tracker.getContainerServerbound((byte) menu.containerId);
        if (container == null) {
            return null;
        }

        final boolean craftable = menu instanceof net.minecraft.world.inventory.CraftingMenu && javaSlot == 0;
        return new ResolvedSlot(slotRef(user, containerSlot.container(), containerSlot.slot(), container),
            item, containerSlot.result(), craftable);
    }

    /**
     * Resolves a slot of the player inventory screen (the {@code InventoryMenu} that opens when pressing
     * {@code E}). Its slot order is: 0 = crafting output, 1-4 = 2x2 crafting grid, 5-8 = armor,
     * 9-35 = main inventory, 36-44 = hotbar, 45 = offhand.
     */
    private static @Nullable ResolvedSlot resolvePlayerInventorySlot(final UserConnection user, final InventoryTracker tracker, final int javaSlot, final ItemStack item) {
        if (javaSlot == 0) {
            // 2x2 crafting output: created through a CraftRecipe action and taken from CreatedOutput[50]
            return new ResolvedSlot(slotRef(user, ContainerEnumName.CreatedOutputContainer, BedrockContainerLayout.RESULT_SLOT, tracker.getHudContainer()),
                item, true, true);
        }
        if (javaSlot >= 1 && javaSlot <= 4) {
            // Bedrock keeps the 2x2 crafting grid inside the player only UI (HUD) container, slots 28-31
            return new ResolvedSlot(slotRef(user, ContainerEnumName.CraftingInputContainer, 28 + (javaSlot - 1), tracker.getHudContainer()), item, false, false);
        }
        if (javaSlot >= 5 && javaSlot <= 8) {
            return new ResolvedSlot(slotRef(user, ContainerEnumName.ArmorContainer, javaSlot - 5, tracker.getArmorContainer()), item, false, false);
        }
        if (javaSlot >= 9 && javaSlot <= 35) {
            return resolveInventorySlot(user, tracker, javaSlot, item);
        }
        if (javaSlot >= 36 && javaSlot <= 44) {
            return resolveHotbarSlot(user, tracker, javaSlot - 36, item);
        }
        if (javaSlot == 45) {
            return new ResolvedSlot(slotRef(user, ContainerEnumName.OffhandContainer, 0, tracker.getOffhandContainer()), item, false, false);
        }
        return null;
    }

    private static ResolvedSlot resolveInventorySlot(final UserConnection user, final InventoryTracker tracker, final int bedrockSlot, final ItemStack item) {
        return new ResolvedSlot(slotRef(user, ContainerEnumName.InventoryContainer, bedrockSlot, tracker.getInventoryContainer()), item, false, false);
    }

    private static ResolvedSlot resolveHotbarSlot(final UserConnection user, final InventoryTracker tracker, final int bedrockSlot, final ItemStack item) {
        return new ResolvedSlot(slotRef(user, ContainerEnumName.HotbarContainer, bedrockSlot, tracker.getInventoryContainer()), item, false, false);
    }

    /**
     * Resolves the slot the player currently holds with the cursor ("carried" item in Java terms).
     */
    public static ResolvedSlot resolveCursor(final UserConnection user, final AbstractContainerMenu menu) {
        final InventoryTracker tracker = user.get(InventoryTracker.class);
        return new ResolvedSlot(slotRef(user, ContainerEnumName.CursorContainer, 0, tracker.getHudContainer()), menu.getCarried(), false, false);
    }

    /**
     * Resolves a hotbar slot (0-8) of the currently open menu. Every container menu has the hotbar as its
     * last nine slots, so the Java slot index can be derived from the menu layout. This avoids touching
     * the (version dependent) player inventory API.
     *
     * @return the resolved slot, or {@code null} if the menu has no hotbar slot for that index
     */
    public static @Nullable ResolvedSlot resolveHotbar(final UserConnection user, final AbstractContainerMenu menu, final int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return null;
        }

        final int javaSlot;
        if (menu instanceof InventoryMenu) {
            javaSlot = 36 + hotbarSlot;
        } else {
            final BedrockContainerLayout layout = BedrockContainerLayout.of(menu);
            if (layout == null) {
                return null;
            }
            javaSlot = layout.containerSize() + 27 + hotbarSlot;
        }
        return resolve(user, menu, javaSlot);
    }

    /**
     * Resolves the offhand slot of the player inventory.
     */
    public static ResolvedSlot resolveOffhand(final UserConnection user) {
        final InventoryTracker tracker = user.get(InventoryTracker.class);
        final net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        final ItemStack offhandItem = player != null ? player.getOffhandItem() : ItemStack.EMPTY;
        return new ResolvedSlot(slotRef(user, ContainerEnumName.OffhandContainer, 0, tracker.getOffhandContainer()), offhandItem, false, false);
    }

    /**
     * Builds a reference to a Bedrock slot, taking the stack network id from the tracked Bedrock item.
     * The server rejects requests whose stack network ids don't match its own state, so this value has
     * to come from the items ViaBedrock keeps in sync instead of being guessed.
     */
    private static BedrockStackRef slotRef(final UserConnection user, final ContainerEnumName containerName, final int slot, final Container container) {
        return new BedrockStackRef(containerName, slot, stackNetworkId(user, containerName, slot, container));
    }

    private static int stackNetworkId(final UserConnection user, final ContainerEnumName containerName, final int slot, final @Nullable Container container) {
        if (containerName == ContainerEnumName.CreatedOutputContainer) {
            // A virtual container: the stack does not exist yet when the request is built, so the id is
            // always 0 (the server fills in the created stack itself, see BedrockCrafting).
            return 0;
        }

        if (container != null && slot >= 0 && slot < container.size()) {
            final BedrockItem item = container.getItem(slot);
            if (item != null && !item.isEmpty() && item.netId() != null) {
                return item.netId();
            }
        }

        // Result slots of machine UIs are not always part of the tracked container array; the server
        // reports their stack network ids in the ItemStackResponse instead.
        final Integer reported = BedrockResponseTracker.of(user).stackNetworkId(containerName, slot);
        return reported != null ? reported : 0;
    }

}
