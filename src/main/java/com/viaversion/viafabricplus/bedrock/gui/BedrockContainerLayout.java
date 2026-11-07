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

import java.util.function.IntFunction;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;

/**
 * Describes how the slots of a vanilla Java container menu are laid out from Bedrock's point of view.
 * <p>
 * Bedrock addresses inventory slots by a container name ({@link ContainerEnumName}, the same enum as
 * {@code ContainerSlotType} in Protocol-3.0) plus an index inside that container, and every container
 * kind has its own container name and - annoyingly - its own slot numbering. The tables below are
 * derived from the vanilla {@code AbstractContainerMenu} slot order of Minecraft and from the slot
 * mapping Geyser uses for the same containers in the reverse direction
 * ({@code org.geysermc.geyser.translator.inventory.*InventoryTranslator#javaSlotToBedrockContainer}).
 * <p>
 * A layout only describes the <b>block/entity</b> part of a menu; the player inventory part
 * (main inventory, hotbar) is identical for every menu and handled by {@link BedrockGuiSlots}.
 * <p>
 * The result slots are the interesting ones: Bedrock keeps every machine result in the container named
 * {@code …ResultPreview} at <b>slot 50</b> - the very same index in every container - and crafted or
 * creative items appear in the virtual {@code CreatedOutput} container, again at slot 50. Those slots
 * cannot be written by the client, they are produced by the server (see {@link BedrockCrafting}).
 */
public final class BedrockContainerLayout {

    /**
     * The slot index Bedrock uses for the result of a container UI (and for the created output of a
     * crafting / creative action). Every {@code …ResultPreview} container and {@code CreatedOutput}
     * uses this single index.
     */
    public static final int RESULT_SLOT = 50;

    /**
     * A slot inside the container part of a menu.
     *
     * @param container the Bedrock container name
     * @param slot      the slot index inside that Bedrock container
     * @param result    whether this is an output slot the client can only take items out of
     */
    public record ContainerSlot(ContainerEnumName container, int slot, boolean result) {

        public ContainerSlot(final ContainerEnumName container, final int slot) {
            this(container, slot, false);
        }

    }

    private final int containerSize;
    private final IntFunction<ContainerSlot> mapper;

    private BedrockContainerLayout(final int containerSize, final IntFunction<ContainerSlot> mapper) {
        this.containerSize = containerSize;
        this.mapper = mapper;
    }

    /**
     * The amount of slots the container part of the menu has. Every slot with an index greater or
     * equal to this value belongs to the player inventory.
     */
    public int containerSize() {
        return this.containerSize;
    }

    /**
     * Maps the given Java slot index to a Bedrock container slot, or {@code null} if that slot can't
     * be addressed through an item stack request.
     */
    public ContainerSlot containerSlot(final int javaSlot) {
        return this.mapper.apply(javaSlot);
    }

    /**
     * @return the layout for the given menu, or {@code null} if the menu isn't supported yet
     */
    public static BedrockContainerLayout of(final AbstractContainerMenu menu) {
        // Chest-like containers keep their slot numbering (0..size-1). Chests, hoppers, droppers and
        // dispensers use the generic "level entity" container, barrels and shulker boxes have their own.
        if (menu instanceof ChestMenu) {
            // Chest menus are 27 (single), 54 (double) or 9 (one row) slots plus the 36 player slots;
            // deriving the size from the slot list keeps this correct for every chest-like screen
            final int size = menu.slots.size() - 36;
            return new BedrockContainerLayout(size, slot -> slot < size ? new ContainerSlot(ContainerEnumName.LevelEntityContainer, slot) : null);
        }
        if (menu instanceof ShulkerBoxMenu) {
            return new BedrockContainerLayout(27, slot -> slot < 27 ? new ContainerSlot(ContainerEnumName.ShulkerBoxContainer, slot) : null);
        }
        if (menu instanceof HopperMenu) {
            return new BedrockContainerLayout(5, slot -> slot < 5 ? new ContainerSlot(ContainerEnumName.LevelEntityContainer, slot) : null);
        }
        if (menu instanceof DispenserMenu) {
            return new BedrockContainerLayout(9, slot -> slot < 9 ? new ContainerSlot(ContainerEnumName.LevelEntityContainer, slot) : null);
        }
        if (menu instanceof FurnaceMenu) {
            return furnace(ContainerEnumName.FurnaceIngredientContainer, ContainerEnumName.FurnaceResultContainer);
        }
        if (menu instanceof BlastFurnaceMenu) {
            return furnace(ContainerEnumName.BlastFurnaceIngredientContainer, ContainerEnumName.FurnaceResultContainer);
        }
        if (menu instanceof SmokerMenu) {
            return furnace(ContainerEnumName.SmokerIngredientContainer, ContainerEnumName.FurnaceResultContainer);
        }
        if (menu instanceof BrewingStandMenu) {
            return new BedrockContainerLayout(5, slot -> switch (slot) {
                case 0, 1, 2 -> new ContainerSlot(ContainerEnumName.BrewingStandResultContainer, slot);
                case 3 -> new ContainerSlot(ContainerEnumName.BrewingStandInputContainer, 0);
                case 4 -> new ContainerSlot(ContainerEnumName.BrewingStandFuelContainer, 4);
                default -> null;
            });
        }
        if (menu instanceof CraftingMenu) {
            return new BedrockContainerLayout(10, slot -> {
                if (slot == 0) {
                    // Crafted output: created by the server through a CraftRecipe action, the client
                    // takes it out of the CreatedOutput container at slot 50
                    return new ContainerSlot(ContainerEnumName.CreatedOutputContainer, RESULT_SLOT, true);
                } else if (slot >= 1 && slot <= 9) {
                    return new ContainerSlot(ContainerEnumName.CraftingInputContainer, slot + 31); // Bedrock starts at 32
                }
                return null;
            });
        }
        if (menu instanceof AnvilMenu) {
            return new BedrockContainerLayout(3, slot -> switch (slot) {
                case 0 -> new ContainerSlot(ContainerEnumName.AnvilInputContainer, 1);
                case 1 -> new ContainerSlot(ContainerEnumName.AnvilMaterialContainer, 2);
                case 2 -> new ContainerSlot(ContainerEnumName.AnvilResultPreviewContainer, RESULT_SLOT, true);
                default -> null;
            });
        }
        if (menu instanceof EnchantmentMenu) {
            return new BedrockContainerLayout(2, slot -> switch (slot) {
                case 0 -> new ContainerSlot(ContainerEnumName.EnchantingInputContainer, 14);
                case 1 -> new ContainerSlot(ContainerEnumName.EnchantingMaterialContainer, 15); // lapis
                default -> null;
            });
        }
        if (menu instanceof BeaconMenu) {
            return new BedrockContainerLayout(1, slot -> slot == 0 ? new ContainerSlot(ContainerEnumName.BeaconPaymentContainer, 27) : null);
        }
        if (menu instanceof StonecutterMenu) {
            return new BedrockContainerLayout(2, slot -> switch (slot) {
                case 0 -> new ContainerSlot(ContainerEnumName.StonecutterInputContainer, 3);
                case 1 -> new ContainerSlot(ContainerEnumName.StonecutterResultPreviewContainer, RESULT_SLOT, true);
                default -> null;
            });
        }
        if (menu instanceof SmithingMenu) {
            return new BedrockContainerLayout(4, slot -> switch (slot) {
                case 0 -> new ContainerSlot(ContainerEnumName.SmithingTableTemplateContainer, 53);
                case 1 -> new ContainerSlot(ContainerEnumName.SmithingTableInputContainer, 51);
                case 2 -> new ContainerSlot(ContainerEnumName.SmithingTableMaterialContainer, 52);
                case 3 -> new ContainerSlot(ContainerEnumName.SmithingTableResultPreviewContainer, RESULT_SLOT, true);
                default -> null;
            });
        }
        if (menu instanceof CartographyTableMenu) {
            return new BedrockContainerLayout(3, slot -> switch (slot) {
                case 0 -> new ContainerSlot(ContainerEnumName.CartographyInputContainer, 12);
                case 1 -> new ContainerSlot(ContainerEnumName.CartographyAdditionalContainer, 13);
                case 2 -> new ContainerSlot(ContainerEnumName.CartographyResultPreviewContainer, RESULT_SLOT, true);
                default -> null;
            });
        }
        if (menu instanceof LoomMenu) {
            return new BedrockContainerLayout(4, slot -> switch (slot) {
                case 0 -> new ContainerSlot(ContainerEnumName.LoomInputContainer, 9);
                case 1 -> new ContainerSlot(ContainerEnumName.LoomDyeContainer, 10);
                case 2 -> new ContainerSlot(ContainerEnumName.LoomMaterialContainer, 11);
                case 3 -> new ContainerSlot(ContainerEnumName.LoomResultPreviewContainer, RESULT_SLOT, true);
                default -> null;
            });
        }
        if (menu instanceof GrindstoneMenu) {
            return new BedrockContainerLayout(3, slot -> switch (slot) {
                case 0 -> new ContainerSlot(ContainerEnumName.GrindstoneInputContainer, 16);
                case 1 -> new ContainerSlot(ContainerEnumName.GrindstoneAdditionalContainer, 17);
                case 2 -> new ContainerSlot(ContainerEnumName.GrindstoneResultPreviewContainer, RESULT_SLOT, true);
                default -> null;
            });
        }
        if (menu instanceof MerchantMenu) {
            return new BedrockContainerLayout(3, slot -> switch (slot) {
                case 0 -> new ContainerSlot(ContainerEnumName.Trade2Ingredient1Container, 4);
                case 1 -> new ContainerSlot(ContainerEnumName.Trade2Ingredient2Container, 5);
                case 2 -> new ContainerSlot(ContainerEnumName.Trade2ResultPreviewContainer, RESULT_SLOT, true);
                default -> null;
            });
        }
        return null;
    }

    private static BedrockContainerLayout furnace(final ContainerEnumName ingredientContainer, final ContainerEnumName resultContainer) {
        return new BedrockContainerLayout(3, slot -> switch (slot) {
            case 0 -> new ContainerSlot(ingredientContainer, 0);
            case 1 -> new ContainerSlot(ContainerEnumName.FurnaceFuelContainer, 1);
            case 2 -> new ContainerSlot(resultContainer, RESULT_SLOT, true);
            default -> null;
        });
    }

}
