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
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemStackRequestActionType;

/**
 * A single Bedrock item stack request ({@code ItemStackRequest}, packet id 147).
 * <p>
 * Bedrock is fully server authoritative for inventory manipulations: instead of the Java
 * "click on window id X slot Y with button Z" packet, the client sends a list of elementary
 * stack actions (take from a slot, place into a slot, swap two slots, drop, ...). The server
 * validates every action against its own inventory state and answers with an
 * {@code ItemStackResponse} plus refreshed container contents.
 * <p>
 * The fields and the serialization order mirror Cloudburst Protocol's
 * {@code ItemStackRequest} (Protocol-3.0, {@code BedrockCodecHelper#writeItemStackRequest}):
 * <pre>
 *   requestId        var int (zig-zag)
 *   actions          array&lt;unsigned var int&gt; of { actionType unsigned var int, action data }
 *   filterStrings    array&lt;unsigned var int&gt; of string
 *   origin           int LE (-1 = none)
 * </pre>
 */
public record BedrockItemStackRequest(int requestId, List<Action> actions, List<String> filterStrings, int textProcessingEventOrigin) {

    public BedrockItemStackRequest {
        actions = List.copyOf(actions);
        filterStrings = List.copyOf(filterStrings);
    }

    public BedrockItemStackRequest(final int requestId, final List<Action> actions) {
        this(requestId, actions, List.of(), -1);
    }

    /**
     * Every action Bedrock knows about for inventory manipulation. Only the subset that can be
     * produced by translating a Java container click is implemented, the remaining action types
     * (crafting, beacon payment, lab table, ...) are driven by their own screens.
     */
    public sealed interface Action permits Take, Place, Swap, Drop, Consume, Destroy, CraftCreative, CraftRecipe {

        ItemStackRequestActionType type();

    }

    /**
     * Moves {@code amount} items from {@code source} to {@code destination}
     * (Java: left/right click on a slot with an empty cursor, half stacks, ...).
     */
    public record Take(int amount, BedrockStackRef source, BedrockStackRef destination) implements Action {

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Take;
        }

    }

    /**
     * Moves {@code amount} items from {@code source} to {@code destination}
     * (Java: clicking a slot while holding items with the cursor).
     */
    public record Place(int amount, BedrockStackRef source, BedrockStackRef destination) implements Action {

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Place;
        }

    }

    /**
     * Swaps the contents of two slots (Java: swapping with a hotbar slot or with the offhand).
     */
    public record Swap(BedrockStackRef source, BedrockStackRef destination) implements Action {

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Swap;
        }

    }

    /**
     * Throws {@code amount} items out of a slot (Java: {@code Q} / {@code Ctrl + Q}).
     */
    public record Drop(int amount, BedrockStackRef source, boolean randomly) implements Action {

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Drop;
        }

    }

    /**
     * Consumes items out of a slot without them appearing anywhere else (used by item use, e.g.
     * eating or placing blocks). Kept for completeness of the click translation (drag crafting).
     */
    public record Consume(int amount, BedrockStackRef source) implements Action {

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Consume;
        }

    }

    /**
     * Destroys items (creative mode).
     */
    public record Destroy(int amount, BedrockStackRef source) implements Action {

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Destroy;
        }

    }

    /**
     * Creates a creative mode item (Java: middle click in creative mode).
     * <p>
     * The item does not appear in a real container: the server puts it into the virtual
     * {@code CreatedOutput} container (slot 50), so the request always has to be followed by a
     * {@link Take} from that container - exactly what the Bedrock client does.
     *
     * @param itemNetworkId the creative item network id from the {@code CreativeContent} packet
     * @param numberOfCrafts how many items to create (1 = a full stack of the item)
     */
    public record CraftCreative(int itemNetworkId, int numberOfCrafts) implements Action {

        public CraftCreative(final int itemNetworkId) {
            this(itemNetworkId, 1);
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.CraftCreative;
        }

    }

    /**
     * Crafts a recipe from the crafting grid (Java: clicking the result slot of a crafting table or of
     * the player 2x2 grid).
     * <p>
     * Bedrock does not let the client move the result out of the crafting screen: the server performs
     * the crafting itself, consumes the ingredients and puts the result into the virtual
     * {@code CreatedOutput} container, which is why a {@link Take} from that container has to follow in
     * the same request.
     *
     * @param recipeNetworkId the recipe's network id from the {@code CraftingData} packet
     * @param numberOfCrafts  how often the recipe should be crafted
     */
    public record CraftRecipe(int recipeNetworkId, int numberOfCrafts) implements Action {

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.CraftRecipe;
        }

    }

}
