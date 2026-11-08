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
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import org.jetbrains.annotations.Nullable;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

/**
 * The recipe table of the Bedrock server, needed to craft from a Java result slot.
 * <p>
 * Bedrock has no "click the result slot" action: the client tells the server <i>which recipe</i> it
 * wants to craft ({@code CraftRecipe} with the recipe's network id) and the server performs the
 * crafting, putting the result into the virtual {@code CreatedOutput} container. To keep the Java
 * crafting table / player 2x2 grid usable, the recipe network ids therefore have to be known on the
 * client - which is exactly what the {@code CraftingData} packet (id 31) contains. ViaBedrock ignores
 * that packet, so it is parsed here.
 * <p>
 * Only shaped and shapeless recipes (vanilla crafting table and the player 2x2 grid) are kept. The
 * other recipe types of the packet (furnace, multi, user/shulker, chemistry, smithing), the potion and
 * container mixes and the material reducers are parsed only far enough to keep the packet stream
 * aligned; they are driven by their own screens and never produced by a grid click.
 * <p>
 * Packet layout for <b>protocol 1001 / Bedrock 1.26.30</b> (Cloudburst Protocol 3.0: the newest
 * overrides below 2168, i.e. {@code CraftingDataSerializer_v465} for the packet itself,
 * {@code v685}/{@code v748} for shaped / shapeless):
 * <pre>
 *   recipes      array&lt;unsigned var int&gt; of { type var int (zig-zag), type specific body }
 *   potionMix    array&lt;unsigned var int&gt; of 6 var ints
 *   containerMix array&lt;unsigned var int&gt; of 3 var ints
 *   reducers     array&lt;unsigned var int&gt; of { var int input id, array of {var int, var int} }
 *   cleanRecipes boolean
 *
 *   shaped (type 1, 7):   id string, width var int, height var int, width*height ingredients INLINE,
 *                         results array, uuid (16 bytes), tag string, priority var int,
 *                         assumeSymmetry boolean, [requirement], network id unsigned var int
 *   shapeless (0, 5, 6):  id string, ingredients array, results array, uuid, tag, priority,
 *                         [requirement], network id
 * </pre>
 * Note the differences to the 1.26.4x+ layout: the ingredients of a shaped recipe are not an array
 * (their count is {@code width * height}), the recipe unlocking requirement has no presence flag but
 * simply follows for {@code SHAPED} / {@code SHAPELESS} / {@code SHULKER_BOX}, and the types are not
 * split over separate arrays.
 * <p>
 * Matching is done by Bedrock item identifier plus data value, because that is what a recipe
 * requirement contains ({@link BedrockItemData.Ingredient}). At protocol 1001 the descriptor only
 * carries the numeric item runtime id, so it is resolved to an identifier with ViaBedrock's
 * {@code ItemRewriter} while parsing (see {@link #read(ByteBuf)}). The data value is treated as a
 * wildcard when the recipe uses Bedrock's {@code 0x7fff} marker, and Java stacks always report data
 * value 0 - Bedrock flattens the varieties of an item into separate identifiers on the versions
 * ViaBedrock supports, so identifier equality is enough for all non-legacy items.
 */
public final class BedrockRecipeBook extends StoredObject {

    private static final int MAX_RECIPES = 20_000;

    private static volatile boolean registered = false;

    private final List<ShapedRecipe> shapedRecipes = new ArrayList<>();
    private final List<ShapelessRecipe> shapelessRecipes = new ArrayList<>();
    private boolean loaded = false;
    private boolean parsingDisabled = false;

    public BedrockRecipeBook(final UserConnection user) {
        super(user);
    }

    /**
     * A shaped (crafting table / 2x2 grid) recipe.
     *
     * @param networkId   the recipe network id used by the {@code CraftRecipe} action
     * @param width       the recipe width in slots
     * @param height      the recipe height in slots
     * @param ingredients the requirements in row-major order ({@code width * height} entries)
     */
    public record ShapedRecipe(int networkId, int width, int height, BedrockItemData.Ingredient[] ingredients) {

        public ShapedRecipe {
            ingredients = ingredients.clone();
        }

    }

    /**
     * A shapeless recipe (only the multiset of ingredients matters).
     *
     * @param networkId   the recipe network id used by the {@code CraftRecipe} action
     * @param ingredients the requirements, one entry per used grid slot
     */
    public record ShapelessRecipe(int networkId, BedrockItemData.Ingredient[] ingredients) {

        public ShapelessRecipe {
            ingredients = ingredients.clone();
        }

    }

    /**
     * Registers the {@code CraftingData} handler in the Bedrock protocol. Idempotent.
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

            protocol.registerClientbound(ClientboundBedrockPackets.CRAFTING_DATA, null, wrapper -> {
                final BedrockRecipeBook book = of(wrapper.user());
                if (book.parsingDisabled) {
                    return;
                }
                try {
                    // The packet is not handled by ViaBedrock at all, so the raw payload is read here
                    // and parsed on its own buffer. Types.REMAINING_BYTES gives us exactly the bytes of
                    // the packet body without depending on any (version specific) packet layout.
                    book.read(Unpooled.wrappedBuffer(wrapper.read(Types.REMAINING_BYTES)));
                } catch (final Throwable throwable) {
                    book.parsingDisabled = true;
                    ViaFabricPlusBedrock.impl().logger().warn("Failed to parse Bedrock CraftingData, crafting output translation is disabled", throwable);
                }
            });
            registered = true;
        } catch (final Throwable throwable) {
            ViaFabricPlusBedrock.impl().logger().warn("Failed to register the Bedrock CraftingData handler", throwable);
        }
    }

    public static BedrockRecipeBook of(final UserConnection user) {
        BedrockRecipeBook book = user.get(BedrockRecipeBook.class);
        if (book == null) {
            book = new BedrockRecipeBook(user);
            user.put(book);
        }
        return book;
    }

    /**
     * Parses the packet (layout described in the class javadoc, protocol 1001).
     */
    public void read(final ByteBuf buffer) {
        this.read(buffer, this.runtimeIdentifiers());
    }

    /**
     * Parses the packet with an explicit runtime id to identifier lookup, which keeps the parser
     * independent of ViaBedrock's item palette (and testable).
     *
     * @param runtimeIds runtime id to identifier lookup, may return {@code null} for unknown ids
     */
    public void read(final ByteBuf buffer, final IntFunction<String> runtimeIds) {
        this.shapedRecipes.clear();
        this.shapelessRecipes.clear();

        // recipes: one array, the type decides the body
        final int entryCount = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
        for (int entry = 0; entry < entryCount; entry++) {
            final int type = BedrockTypes.VAR_INT.read(buffer); // zig-zag CraftingDataType id
            switch (type) {
                case 0 -> readShapelessRecipe(buffer, runtimeIds, true); // SHAPELESS (2x2 / 3x3 grid)
                case 5 -> skipShapelessRecipe(buffer, runtimeIds, true); // SHULKER_BOX (has requirement)
                case 6 -> skipShapelessRecipe(buffer, runtimeIds, false); // SHAPELESS_CHEMISTRY
                case 1 -> readShapedRecipe(buffer, runtimeIds, true); // SHAPED (2x2 / 3x3 grid)
                case 7 -> skipShapedRecipe(buffer, runtimeIds); // SHAPED_CHEMISTRY
                case 2 -> { // FURNACE (deprecated since 975)
                    BedrockTypes.VAR_INT.read(buffer); // input id
                    BedrockItemData.skipItemInstance(buffer); // result
                    BedrockTypes.STRING.read(buffer); // tag
                }
                case 3 -> { // FURNACE_DATA (deprecated since 975)
                    BedrockTypes.VAR_INT.read(buffer); // input id
                    BedrockTypes.VAR_INT.read(buffer); // input data
                    BedrockItemData.skipItemInstance(buffer); // result
                    BedrockTypes.STRING.read(buffer); // tag
                }
                case 4 -> { // MULTI
                    buffer.skipBytes(16); // recipe uuid
                    BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer); // network id
                }
                case 8 -> skipSmithingTransformRecipe(buffer, runtimeIds);
                case 9 -> skipSmithingTrimRecipe(buffer, runtimeIds);
                default -> throw new IllegalStateException("Unknown crafting data type: " + type);
            }
        }

        skipArray(buffer, buf -> { // potion mix
            for (int i = 0; i < 6; i++) {
                BedrockTypes.VAR_INT.read(buf);
            }
        });
        skipArray(buffer, buf -> { // container mix
            for (int i = 0; i < 3; i++) {
                BedrockTypes.VAR_INT.read(buf);
            }
        });
        skipArray(buffer, buf -> { // material reducer
            BedrockTypes.VAR_INT.read(buf); // input id
            final int length = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buf);
            for (int i = 0; i < length; i++) {
                BedrockTypes.VAR_INT.read(buf); // definition id
                BedrockTypes.VAR_INT.read(buf); // count
            }
        });
        buffer.readBoolean(); // clean recipes

        this.loaded = true;
        if (isDebug()) {
            ViaFabricPlusBedrock.impl().logger().info("Parsed {} shaped and {} shapeless Bedrock recipes",
                this.shapedRecipes.size(), this.shapelessRecipes.size());
        }
    }

    public boolean isLoaded() {
        return this.loaded && !this.parsingDisabled;
    }

    /**
     * Looks up the recipe that matches the given crafting grid.
     *
     * @param grid   the grid contents in row-major order, empty stacks for empty slots
     * @param width  the grid width (2 for the player grid, 3 for the crafting table)
     * @param height the grid height
     * @return the matching recipe, or {@code null} if the server sent no matching recipe
     */
    public @Nullable Match find(final ItemStack[] grid, final int width, final int height) {
        final String[] identifiers = new String[grid.length];
        final int[] counts = new int[grid.length];
        for (int slot = 0; slot < grid.length; slot++) {
            final ItemStack stack = grid[slot];
            if (stack != null && !stack.isEmpty()) {
                identifiers[slot] = identifier(stack);
                counts[slot] = stack.getCount();
            }
        }
        return this.find(identifiers, counts, width, height);
    }

    /**
     * Looks up the recipe that matches the given crafting grid. This overload works on item identifiers
     * only, which keeps the matching independent of Minecraft's item registry (and testable).
     *
     * @param identifiers the Bedrock/Java item identifier per grid slot, {@code null} for an empty slot
     * @param counts      the stack size per grid slot
     * @param width       the grid width
     * @param height      the grid height
     * @return the matching recipe, or {@code null} if the server sent no matching recipe
     */
    public @Nullable Match find(final String[] identifiers, final int[] counts, final int width, final int height) {
        if (!this.isLoaded()) {
            return null;
        }

        for (final ShapedRecipe recipe : this.shapedRecipes) {
            if (matchesShaped(recipe, identifiers, width, height)) {
                return new Match(recipe.networkId(), countCrafts(recipe.ingredients(), identifiers, counts));
            }
        }

        for (final ShapelessRecipe recipe : this.shapelessRecipes) {
            if (matchesShapeless(recipe, identifiers, width, height)) {
                return new Match(recipe.networkId(), countCrafts(recipe.ingredients(), identifiers, counts));
            }
        }
        return null;
    }

    /**
     * The result of a successful recipe lookup.
     *
     * @param networkId the recipe network id
     * @param crafts    how many times the recipe can be crafted with the current grid
     */
    public record Match(int networkId, int crafts) {
    }

    private static boolean matchesShaped(final ShapedRecipe recipe, final String[] grid, final int width, final int height) {
        if (recipe.width() > width || recipe.height() > height) {
            return false;
        }

        for (int offsetX = 0; offsetX <= width - recipe.width(); offsetX++) {
            for (int offsetY = 0; offsetY <= height - recipe.height(); offsetY++) {
                if (matchesShapedAt(recipe, grid, width, height, offsetX, offsetY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesShapedAt(final ShapedRecipe recipe, final String[] grid, final int width, final int height,
                                            final int offsetX, final int offsetY) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final String identifier = grid[y * width + x];
                final boolean inRecipe = x >= offsetX && x < offsetX + recipe.width() && y >= offsetY && y < offsetY + recipe.height();
                if (!inRecipe) {
                    if (identifier != null) {
                        return false; // a slot outside the pattern has to be empty
                    }
                    continue;
                }

                final BedrockItemData.Ingredient ingredient = recipe.ingredients()[(y - offsetY) * recipe.width() + (x - offsetX)];
                if (!ingredient.matches(identifier, 0)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesShapeless(final ShapelessRecipe recipe, final String[] grid, final int width, final int height) {
        final boolean[] used = new boolean[recipe.ingredients().length];
        int filled = 0;

        for (int slot = 0; slot < width * height; slot++) {
            final String identifier = grid[slot];
            if (identifier == null) {
                continue;
            }
            filled++;

            boolean matched = false;
            for (int i = 0; i < recipe.ingredients().length; i++) {
                if (used[i] || !recipe.ingredients()[i].matches(identifier, 0)) {
                    continue;
                }
                used[i] = true;
                matched = true;
                break;
            }
            if (!matched) {
                return false;
            }
        }
        return filled == recipe.ingredients().length;
    }

    /**
     * @return how often the recipe can be crafted with the given grid (limited by the scarcest
     * ingredient and by the stack sizes of the grid slots)
     */
    private static int countCrafts(final BedrockItemData.Ingredient[] ingredients, final String[] grid, final int[] counts) {
        int crafts = Integer.MAX_VALUE;
        for (final BedrockItemData.Ingredient ingredient : ingredients) {
            final int required = Math.max(1, ingredient.count());
            int available = 0;
            for (int slot = 0; slot < grid.length; slot++) {
                if (grid[slot] != null && ingredient.matches(grid[slot], 0)) {
                    available += Math.max(1, counts[slot]);
                }
            }
            crafts = Math.min(crafts, available / required);
        }
        return crafts == Integer.MAX_VALUE || crafts <= 0 ? 1 : crafts;
    }

    /**
     * @return the Bedrock item identifier of a Java stack
     */
    public static String identifier(final ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /**
     * @return runtime id to identifier lookup based on ViaBedrock's item palette
     */
    private IntFunction<String> runtimeIdentifiers() {
        final ItemRewriter rewriter = this.user() != null ? this.user().get(ItemRewriter.class) : null;
        if (rewriter == null) {
            return runtimeId -> null;
        }

        final Map<Integer, String> runtimeIds = new HashMap<>();
        rewriter.getItems().forEach((identifier, runtimeId) -> runtimeIds.put(runtimeId, identifier));
        return runtimeIds::get;
    }

    /**
     * Reads a shaped recipe and keeps it when it fits a 3x3 grid.
     *
     * @param keep whether the recipe is usable from a crafting grid (false for the chemistry variants)
     */
    private void readShapedRecipe(final ByteBuf buffer, final IntFunction<String> runtimeIds, final boolean keep) {
        BedrockTypes.STRING.read(buffer); // recipe id
        final int width = BedrockTypes.VAR_INT.read(buffer);
        final int height = BedrockTypes.VAR_INT.read(buffer);

        if (width <= 0 || height <= 0 || width > 3 || height > 3 || width * height > 9) {
            // Still have to consume the recipe, so read the ingredients into a throwaway array
            for (int i = 0; i < Math.max(0, width * height); i++) {
                BedrockItemData.readIngredient(buffer, runtimeIds);
            }
            skipResultsAndMetadata(buffer);
            buffer.readBoolean(); // assume symmetry
            skipRequirement(buffer, runtimeIds);
            BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer); // network id
            return;
        }

        final BedrockItemData.Ingredient[] ingredients = new BedrockItemData.Ingredient[width * height];
        for (int ingredient = 0; ingredient < ingredients.length; ingredient++) {
            ingredients[ingredient] = BedrockItemData.readIngredient(buffer, runtimeIds);
        }

        skipResultsAndMetadata(buffer);
        buffer.readBoolean(); // assume symmetry
        skipRequirement(buffer, runtimeIds);
        final int networkId = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);

        if (keep && this.shapedRecipes.size() < MAX_RECIPES) {
            this.shapedRecipes.add(new ShapedRecipe(networkId, width, height, ingredients));
        }
    }

    /**
     * Skips a shaped recipe that is never craftable from a grid (chemistry).
     */
    private static void skipShapedRecipe(final ByteBuf buffer, final IntFunction<String> runtimeIds) {
        BedrockTypes.STRING.read(buffer); // recipe id
        final int width = BedrockTypes.VAR_INT.read(buffer);
        final int height = BedrockTypes.VAR_INT.read(buffer);
        for (int i = 0; i < Math.max(0, width * height); i++) {
            BedrockItemData.readIngredient(buffer, runtimeIds);
        }
        skipResultsAndMetadata(buffer);
        buffer.readBoolean(); // assume symmetry
        skipRequirement(buffer, runtimeIds);
        BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer); // network id
    }

    /**
     * Reads a shapeless recipe and keeps it when it fits a 3x3 grid.
     *
     * @param requirement whether the recipe carries a recipe unlocking requirement
     */
    private void readShapelessRecipe(final ByteBuf buffer, final IntFunction<String> runtimeIds, final boolean requirement) {
        BedrockTypes.STRING.read(buffer); // recipe id

        final int ingredientCount = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
        final BedrockItemData.Ingredient[] ingredients = new BedrockItemData.Ingredient[ingredientCount];
        for (int ingredient = 0; ingredient < ingredientCount; ingredient++) {
            ingredients[ingredient] = BedrockItemData.readIngredient(buffer, runtimeIds);
        }

        skipResultsAndMetadata(buffer);
        if (requirement) {
            skipRequirement(buffer, runtimeIds);
        }
        final int networkId = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);

        if (ingredients.length > 0 && ingredients.length <= 9 && this.shapelessRecipes.size() < MAX_RECIPES) {
            this.shapelessRecipes.add(new ShapelessRecipe(networkId, ingredients));
        }
    }

    /**
     * Skips a shapeless recipe that is never craftable from a grid (shulker box colouring, chemistry).
     */
    private static void skipShapelessRecipe(final ByteBuf buffer, final IntFunction<String> runtimeIds, final boolean requirement) {
        BedrockTypes.STRING.read(buffer); // recipe id
        skipArray(buffer, buf -> BedrockItemData.readIngredient(buf, runtimeIds));
        skipResultsAndMetadata(buffer);
        if (requirement) {
            skipRequirement(buffer, runtimeIds);
        }
        BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer); // network id
    }

    /**
     * Skips the part every recipe shares: results, recipe uuid, crafting tag and priority.
     */
    private static void skipResultsAndMetadata(final ByteBuf buffer) {
        final int resultCount = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
        for (int i = 0; i < resultCount; i++) {
            BedrockItemData.skipItemInstance(buffer);
        }
        buffer.skipBytes(16); // recipe uuid (two little endian longs)
        BedrockTypes.STRING.read(buffer); // crafting tag
        BedrockTypes.VAR_INT.read(buffer); // priority
    }

    /**
     * Skips the recipe unlocking requirement. At protocol 1001 it has no presence flag: it simply
     * follows for {@code SHAPED}, {@code SHAPELESS} and {@code SHULKER_BOX} recipes. The context is a
     * byte, and only {@code NONE} (0) is followed by an ingredient list.
     */
    private static void skipRequirement(final ByteBuf buffer, final IntFunction<String> runtimeIds) {
        if (buffer.readByte() == 0) { // NONE: the unlock condition is a list of ingredients
            skipArray(buffer, buf -> BedrockItemData.readIngredient(buf, runtimeIds));
        }
    }

    private static void skipSmithingTransformRecipe(final ByteBuf buffer, final IntFunction<String> runtimeIds) {
        BedrockTypes.STRING.read(buffer); // recipe id
        BedrockItemData.readIngredient(buffer, runtimeIds); // template
        BedrockItemData.readIngredient(buffer, runtimeIds); // base
        BedrockItemData.readIngredient(buffer, runtimeIds); // addition
        BedrockItemData.skipItemInstance(buffer); // result
        BedrockTypes.STRING.read(buffer); // tag
        BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer); // network id
    }

    private static void skipSmithingTrimRecipe(final ByteBuf buffer, final IntFunction<String> runtimeIds) {
        BedrockTypes.STRING.read(buffer); // recipe id
        BedrockItemData.readIngredient(buffer, runtimeIds); // template
        BedrockItemData.readIngredient(buffer, runtimeIds); // base
        BedrockItemData.readIngredient(buffer, runtimeIds); // addition
        BedrockTypes.STRING.read(buffer); // tag, trim recipes have no result stack
        BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer); // network id
    }

    private static void skipArray(final ByteBuf buffer, final Consumer<ByteBuf> reader) {
        final int length = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
        for (int i = 0; i < length; i++) {
            reader.accept(buffer);
        }
    }

    private static boolean isDebug() {
        return ViaFabricPlusBedrock.impl() != null && ViaFabricPlusBedrock.impl().settings().containerTranslationDebug().isActive();
    }

    @Override
    public String toString() {
        return "BedrockRecipeBook[shaped=" + this.shapedRecipes.size() + ", shapeless=" + this.shapelessRecipes.size()
            + ", loaded=" + this.loaded + "]";
    }

}
