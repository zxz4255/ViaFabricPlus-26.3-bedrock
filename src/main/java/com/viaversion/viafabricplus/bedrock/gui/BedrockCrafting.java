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
import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the Bedrock actions for the two interactions that do not move an already existing stack:
 * crafting from a crafting grid and cloning an item in creative mode.
 * <p>
 * Both work the same way on Bedrock: the client asks the server to create an item, the server puts it
 * into the virtual {@code CreatedOutput} container at slot 50, and the client moves it from there to
 * where it belongs. That is exactly the action sequence a Bedrock client sends - see Geyser's
 * {@code InventoryTranslator#translateCraftingRequest} and
 * {@code PlayerInventoryTranslator#translateCreativeRequest}, which only accept a request whose
 * transfer action takes from {@code CREATED_OUTPUT}:
 * <pre>
 *   crafting:  CraftRecipe(recipe network id, crafts)  -> Take(CreatedOutput[50] -> destination)
 *   creative:  CraftCreative(creative item net id, 1)  -> Take(CreatedOutput[50] -> destination)
 * </pre>
 * The number of crafts is limited by the grid contents (see {@link BedrockRecipeBook#find}), the amount
 * taken is limited by the destination slot and by the vanilla stack size.
 */
public final class BedrockCrafting {

    private static final int MAX_STACK_SIZE = 64;

    private BedrockCrafting() {
    }

    /**
     * The crafting grid of a menu.
     *
     * @param items  the grid contents in row-major order
     * @param width  the grid width
     * @param height the grid height
     */
    public record Grid(ItemStack[] items, int width, int height) {
    }

    /**
     * @return the crafting grid of the given menu, or {@code null} if the menu has no crafting grid
     */
    public static @Nullable Grid gridOf(final AbstractContainerMenu menu) {
        if (menu instanceof CraftingMenu) {
            // Java crafting table: slot 0 = result, slots 1-9 = the 3x3 grid
            return grid(menu, 1, 3, 3);
        }
        if (menu instanceof InventoryMenu) {
            // Player inventory: slot 0 = result, slots 1-4 = the 2x2 grid
            return grid(menu, 1, 2, 2);
        }
        return null;
    }

    private static Grid grid(final AbstractContainerMenu menu, final int firstSlot, final int width, final int height) {
        final ItemStack[] items = new ItemStack[width * height];
        for (int index = 0; index < items.length; index++) {
            final int javaSlot = firstSlot + index;
            items[index] = javaSlot < menu.slots.size() ? menu.slots.get(javaSlot).getItem() : ItemStack.EMPTY;
        }
        return new Grid(items, width, height);
    }

    /**
     * Translates a click on a crafting result slot.
     *
     * @param user        the connection to the Bedrock server
     * @param menu        the open menu
     * @param resultSlot  the Java slot index of the result slot that was clicked
     * @param crafts      how many times to craft ({@code 1} for a normal click, more for a shift click)
     * @param destination where the crafted items should end up
     * @return the actions to send, or {@code null} if the grid matches no recipe the server announced
     */
    public static @Nullable List<BedrockItemStackRequest.Action> craft(final UserConnection user, final AbstractContainerMenu menu,
                                                                       final int resultSlot, final int crafts,
                                                                       final BedrockStackRef destination) {
        final Grid grid = gridOf(menu);
        if (grid == null) {
            return null;
        }

        final ItemStack result = resultSlot < menu.slots.size() ? menu.slots.get(resultSlot).getItem() : ItemStack.EMPTY;
        final int resultCount = result.isEmpty() ? 1 : result.getCount();

        final BedrockRecipeBook.Match match = BedrockRecipeBook.of(user).find(grid.items(), grid.width(), grid.height());
        if (match == null) {
            return null;
        }

        final int craftCount = Math.max(1, Math.min(crafts, match.crafts()));
        final int amount = Math.max(1, Math.min(craftCount * resultCount, MAX_STACK_SIZE));
        final BedrockStackRef created = new BedrockStackRef(ContainerEnumName.CreatedOutputContainer, BedrockContainerLayout.RESULT_SLOT, 0);

        return List.of(
            new BedrockItemStackRequest.CraftRecipe(match.networkId(), craftCount),
            new BedrockItemStackRequest.Take(amount, created, destination)
        );
    }

    /**
     * Translates a creative mode middle click.
     *
     * @param user        the connection to the Bedrock server
     * @param item        the item to clone
     * @param amount      how many items should end up on the cursor
     * @param destination where the created items should end up
     * @return the actions to send, or {@code null} if the server did not announce that creative item
     */
    public static @Nullable List<BedrockItemStackRequest.Action> cloneCreative(final UserConnection user, final ItemStack item,
                                                                              final int amount, final BedrockStackRef destination) {
        if (item.isEmpty()) {
            return null;
        }

        final Integer creativeNetId = BedrockCreativeItems.of(user).creativeNetId(item);
        if (creativeNetId == null) {
            return null;
        }

        final BedrockStackRef created = new BedrockStackRef(ContainerEnumName.CreatedOutputContainer, BedrockContainerLayout.RESULT_SLOT, 0);
        final int createdAmount = Math.max(1, Math.min(amount, item.getMaxStackSize()));

        return List.of(
            new BedrockItemStackRequest.CraftCreative(creativeNetId, 1),
            new BedrockItemStackRequest.Take(createdAmount, created, destination)
        );
    }

}
