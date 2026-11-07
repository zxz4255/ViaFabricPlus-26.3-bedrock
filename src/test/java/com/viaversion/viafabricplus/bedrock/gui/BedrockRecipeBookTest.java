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
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the hand written {@code CraftingData} parser walks the packet of Bedrock protocol 1001
 * (1.26.30) exactly, and that the parsed recipes can be matched against a Java crafting grid.
 * <p>
 * The packet is built here byte by byte following Cloudburst Protocol 3.0 - the newest overrides below
 * 2168, i.e. {@code CraftingDataSerializer_v465} (packet), {@code v685}/{@code v748} (shaped /
 * shapeless). The differences to the 1.26.4x+ layout that the parser has to get right are:
 * <ul>
 *     <li>all recipes live in <b>one</b> array, each prefixed by its type id,</li>
 *     <li>a shaped recipe stores its {@code width * height} ingredients <b>inline</b> instead of as an
 *     array,</li>
 *     <li>the recipe unlocking requirement has no presence flag - it simply follows for
 *     {@code SHAPED} / {@code SHAPELESS} / {@code SHULKER_BOX},</li>
 *     <li>there is no separate shaped/shapeless chemistry or smithing array any more.</li>
 * </ul>
 */
public final class BedrockRecipeBookTest {

    private static final int SHAPED_NETWORK_ID = 7;
    private static final int SHAPELESS_NETWORK_ID = 9;

    /**
     * Runtime ids used by the packet and the identifiers the lookup resolves them to.
     */
    private static final Map<Integer, String> ITEMS = Map.of(
        5, "minecraft:oak_planks",
        6, "minecraft:stick",
        7, "minecraft:coal"
    );

    private static final IntFunction<String> LOOKUP = ITEMS::get;

    @Test
    void parsesAndMatchesRecipes() {
        final BedrockRecipeBook book = new BedrockRecipeBook(null);
        final ByteBuf packet = buildCraftingData();
        book.read(packet, LOOKUP);

        assertTrue(book.isLoaded());
        assertEquals(0, packet.readableBytes()); // the parser consumed the packet exactly

        // 1x1 shaped recipe, one matching stack of four -> four crafts
        final BedrockRecipeBook.Match shaped = book.find(new String[]{
            "minecraft:oak_planks", null,
            null, null
        }, new int[]{4, 0, 0, 0}, 2, 2);
        assertNotNull(shaped);
        assertEquals(SHAPED_NETWORK_ID, shaped.networkId());
        assertEquals(4, shaped.crafts());

        // the same recipe placed in the bottom right corner of a 3x3 grid still matches (offset search)
        final BedrockRecipeBook.Match offset = book.find(new String[]{
            null, null, null,
            null, null, null,
            null, null, "minecraft:oak_planks"
        }, new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1}, 3, 3);
        assertNotNull(offset);
        assertEquals(SHAPED_NETWORK_ID, offset.networkId());
        assertEquals(1, offset.crafts());

        // shapeless recipe: the scarcest ingredient limits the amount of crafts
        final BedrockRecipeBook.Match shapeless = book.find(new String[]{
            "minecraft:coal", "minecraft:stick",
            null, null
        }, new int[]{3, 2, 0, 0}, 2, 2);
        assertNotNull(shapeless);
        assertEquals(SHAPELESS_NETWORK_ID, shapeless.networkId());
        assertEquals(2, shapeless.crafts());

        // a grid that matches neither recipe
        assertNull(book.find(new String[]{
            "minecraft:oak_planks", "minecraft:stick",
            null, null
        }, new int[]{1, 1, 0, 0}, 2, 2));
    }

    @Test
    void doesNotMatchWhenTheLookupFails() {
        final BedrockRecipeBook book = new BedrockRecipeBook(null);
        book.read(buildCraftingData(), runtimeId -> null); // no item palette available

        // an unresolved runtime id must never match, otherwise every grid would "find" a recipe
        assertNull(book.find(new String[]{
            "minecraft:oak_planks", null,
            null, null
        }, new int[]{1, 0, 0, 0}, 2, 2));
    }

    @Test
    void readsEmptyIngredientsAsEmptySlots() {
        assertTrue(BedrockItemData.Ingredient.empty(1).matches(null, 0));
        assertTrue(!BedrockItemData.Ingredient.empty(1).matches("minecraft:stone", 0));
        assertTrue(BedrockItemData.Ingredient.any(1).matches("minecraft:stone", 0));
        assertTrue(!BedrockItemData.Ingredient.any(1).matches(null, 0));
    }

    /**
     * Builds a minimal {@code CraftingData} packet: one 1x1 shaped recipe, one shapeless recipe with
     * two ingredients, empty mix/reducer arrays and the clean recipes flag.
     */
    private static ByteBuf buildCraftingData() {
        final ByteBuf buffer = Unpooled.buffer();

        writeVarInt(buffer, 2); // recipes

        // shaped recipe: type 1
        writeVarInt(buffer, 1 << 1); // CraftingDataType.SHAPED (zig-zag)
        writeString(buffer, "test:shaped");
        writeVarInt(buffer, 1 << 1); // width 1
        writeVarInt(buffer, 1 << 1); // height 1
        writeIngredient(buffer, 5, 0, 1); // one inline ingredient (width * height = 1)
        writeVarInt(buffer, 1); // results
        writeItemInstance(buffer, 1, 1, 0);
        buffer.writeZero(16); // recipe uuid
        writeString(buffer, ""); // crafting tag
        writeVarInt(buffer, 0); // priority 0
        buffer.writeBoolean(false); // assume symmetry
        buffer.writeByte(0); // unlocking context NONE
        writeVarInt(buffer, 0); // no requirement ingredients
        writeVarInt(buffer, SHAPED_NETWORK_ID); // network id

        // shapeless recipe: type 0
        writeVarInt(buffer, 0); // CraftingDataType.SHAPELESS
        writeString(buffer, "test:shapeless");
        writeVarInt(buffer, 2); // ingredient count
        writeIngredient(buffer, 6, 0, 1);
        writeIngredient(buffer, 7, 0, 1);
        writeVarInt(buffer, 0); // results
        buffer.writeZero(16); // recipe uuid
        writeString(buffer, ""); // crafting tag
        writeVarInt(buffer, 0); // priority 0
        buffer.writeByte(0); // unlocking context NONE (shapeless recipes carry it too at 1001)
        writeVarInt(buffer, 0); // no requirement ingredients
        writeVarInt(buffer, SHAPELESS_NETWORK_ID); // network id

        writeVarInt(buffer, 0); // potion mix entries
        writeVarInt(buffer, 0); // container mix entries
        writeVarInt(buffer, 0); // material reducers
        buffer.writeBoolean(true); // clean recipes
        return buffer;
    }

    /**
     * Writes a default item descriptor ({@code ItemDescriptorWithCount}) with a numeric runtime id.
     */
    private static void writeIngredient(final ByteBuf buffer, final int runtimeId, final int aux, final int count) {
        buffer.writeByte(1); // ItemDescriptorType.DEFAULT
        buffer.writeShortLE(runtimeId);
        buffer.writeShortLE(aux);
        writeVarInt(buffer, count << 1); // stack size (zig-zag)
    }

    private static void writeItemInstance(final ByteBuf buffer, final int runtimeId, final int count, final int aux) {
        writeVarInt(buffer, runtimeId << 1); // runtime id (zig-zag)
        buffer.writeShortLE(count);
        writeVarInt(buffer, aux); // data value (unsigned)
        writeVarInt(buffer, 0); // block runtime id
        writeVarInt(buffer, 0); // item user data length
    }

    private static void writeString(final ByteBuf buffer, final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buffer, bytes.length);
        buffer.writeBytes(bytes);
    }

    private static void writeVarInt(final ByteBuf buffer, final int value) {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            buffer.writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        buffer.writeByte(remaining);
    }

}
