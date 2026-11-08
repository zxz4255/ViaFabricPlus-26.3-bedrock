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

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import org.jetbrains.annotations.Nullable;

/**
 * Translates Java container clicks ("window click" packets) into Bedrock item stack requests.
 * <p>
 * Bedrock has no notion of a "click": the client is expected to describe the resulting stack movements
 * as a list of elementary actions ({@code Take}, {@code Place}, {@code Swap}, {@code Drop}, ...) which
 * reference concrete Bedrock container slots and the stack network ids the client assumes to be there.
 * The server validates those actions and answers with the new slot contents.
 * <p>
 * ViaBedrock translates the Bedrock -> Java direction (container contents, cursor item, ...), but it
 * deliberately does not synthesise requests for Java clicks ({@code Container#handleClick} is a stub
 * that always returns {@code false}), so with the vanilla packet path every click in a Bedrock chest,
 * barrel, shulker box, ... is silently dropped. This class fills that gap:
 * <ol>
 *     <li>{@link BedrockGuiSlots} resolves the clicked Java slot into a Bedrock container slot + stack
 *     network id and gives access to the item the client currently shows there,</li>
 *     <li>the click strategy below turns the Java {@link ContainerInput} into Bedrock stack actions,</li>
 *     <li>{@link BedrockGuiSender} serialises and sends them as one {@code ItemStackRequest}.</li>
 * </ol>
 * Because the request is built from the state ViaBedrock keeps in sync with the server, the stack
 * network ids sent to the server match what it expects, which is what makes the request pass validation.
 * <p>
 * Three interactions cannot be expressed by moving an existing stack and are delegated:
 * output slots (crafting table, furnace, anvil, ...) to {@link BedrockCrafting}, creative mode middle
 * clicks to {@link BedrockCrafting#cloneCreative} (backed by {@link BedrockCreativeItems}), and the
 * recipe network ids they need to {@link BedrockRecipeBook}.
 */
public final class BedrockGuiBridge {

    /**
     * Value used by Minecraft for "no slot" (drag start / drag end events).
     */
    private static final int NO_SLOT = -999;

    private BedrockGuiBridge() {
    }

    /**
     * @return whether the Bedrock GUI translation is currently active (connected to a Bedrock server
     * and enabled in the settings)
     */
    public static boolean isActive() {
        if (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return false;
        }
        return ViaFabricPlusBedrock.impl() == null || ViaFabricPlusBedrock.impl().settings().containerTranslation().isActive();
    }

    /**
     * Entry point called from the {@code MultiPlayerGameMode#handleContainerInput} mixin.
     *
     * @param containerId the Java container (window) id
     * @param slotId      the clicked slot
     * @param button      the mouse button / hotbar index, depending on {@code input}
     * @param input       the click type
     * @return {@code true} if the click was translated into a Bedrock request and the vanilla packet
     * has to be suppressed
     */
    public static boolean handleContainerInput(final int containerId, final int slotId, final int button, final ContainerInput input) {
        if (!isActive()) {
            return false;
        }

        // Idempotent: makes sure the packets the translation depends on are actually read even if the
        // registration at startup could not reach the protocol yet.
        BedrockGuiSender.ensurePacketTypeRegistered();
        BedrockRecipeBook.ensureRegistered();
        BedrockCreativeItems.ensureRegistered();
        BedrockResponseTracker.ensureRegistered();

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }

        final AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu == null || menu.containerId != containerId) {
            return false;
        }

        final UserConnection user = ViaFabricPlus.api().userConnection();
        if (user == null || user.get(InventoryTracker.class) == null) {
            return false;
        }

        if (input == ContainerInput.QUICK_CRAFT) {
            // A drag is reported as a sequence of clicks: start (phase 0), one click per slot (phase 1)
            // and end (phase 2). Bedrock knows no drag, so the actions are only sent when it ends.
            final List<BedrockItemStackRequest.Action> actions = drag(user, menu, slotId, button);
            if (actions == null || actions.isEmpty()) {
                return false;
            }
            BedrockGuiSender.send(user, session(user), actions);
            return true;
        }

        if (slotId != NO_SLOT) {
            final BedrockGuiSlots.ResolvedSlot target = BedrockGuiSlots.resolve(user, menu, slotId);
            if (target == null) {
                return false; // unsupported menu
            }

            if (target.result()) {
                // Output slots: the item is created by the server, never moved out of the slot directly
                final List<BedrockItemStackRequest.Action> outputActions = outputSlot(user, menu, slotId, target, button, input);
                if (outputActions == null || outputActions.isEmpty()) {
                    return false;
                }
                BedrockGuiSender.send(user, session(user), outputActions);
                return true;
            }

            final List<BedrockItemStackRequest.Action> actions = switch (input) {
                case PICKUP -> pickup(user, menu, target, button);
                case QUICK_MOVE -> quickMove(user, menu, slotId, target);
                case SWAP -> swap(user, menu, target, button);
                case THROW -> throwItems(user, target, button);
                case PICKUP_ALL -> collectToCursor(user, menu, target);
                case QUICK_CRAFT -> null; // handled above, a drag is split over several clicks
                case CLONE -> cloneCreative(user, menu, target);
            };
            if (actions != null && !actions.isEmpty()) {
                BedrockGuiSender.send(user, session(user), actions);
                return true;
            }
        }

        return false;
    }

    /**
     * {@code PICKUP}: left/right click on a slot. Either the whole/half stack is picked up into the
     * cursor, items are placed into the slot, or (when the cursor holds a different item) both slots
     * are swapped.
     */
    private static @Nullable List<BedrockItemStackRequest.Action> pickup(final UserConnection user, final AbstractContainerMenu menu, final BedrockGuiSlots.ResolvedSlot target, final int button) {
        final BedrockGuiSlots.ResolvedSlot cursor = BedrockGuiSlots.resolveCursor(user, menu);
        final boolean rightClick = button == 1;

        if (cursor.isEmpty()) {
            if (target.isEmpty()) {
                return null;
            }
            // Left click takes the whole stack (capped at the max stack size), right click takes half of it
            final int amount = rightClick ? (target.count() + 1) / 2 : Math.min(target.count(), target.maxStackSize());
            return List.of(new BedrockItemStackRequest.Take(amount, target.ref(), cursor.ref()));
        }

        if (target.isEmpty()) {
            final int amount = rightClick ? 1 : Math.min(cursor.count(), target.maxStackSize());
            return List.of(new BedrockItemStackRequest.Place(amount, cursor.ref(), target.ref()));
        }

        if (ItemStack.isSameItemSameComponents(cursor.item(), target.item())) {
            final int space = target.maxStackSize() - target.count();
            if (space <= 0) {
                return null;
            }
            final int amount = rightClick ? 1 : Math.min(cursor.count(), space);
            return List.of(new BedrockItemStackRequest.Place(amount, cursor.ref(), target.ref()));
        }

        // Different items: the click is a swap between the cursor and the slot
        return List.of(new BedrockItemStackRequest.Swap(cursor.ref(), target.ref()));
    }

    /**
     * Translates a click on an output slot (crafting result, furnace result, anvil result, ...).
     * <p>
     * Those slots are never written by the client: Bedrock creates their content on the server. Two
     * cases have to be told apart:
     * <ul>
     *     <li>a crafted result ({@code craftable}): the crafting has to be requested with the recipe
     *     network id ({@link BedrockCrafting#craft}), then the result is taken from {@code CreatedOutput},</li>
     *     <li>a machine result (furnace, anvil, stonecutter, ...): the server already produced the item,
     *     so it is taken from the result container at slot 50 with the same {@code Take} action.</li>
     * </ul>
     */
    private static @Nullable List<BedrockItemStackRequest.Action> outputSlot(final UserConnection user, final AbstractContainerMenu menu, final int slotId,
                                                                             final BedrockGuiSlots.ResolvedSlot target, final int button, final ContainerInput input) {
        if (target.isEmpty()) {
            return null;
        }

        final BedrockGuiSlots.ResolvedSlot cursor = BedrockGuiSlots.resolveCursor(user, menu);

        if (input == ContainerInput.PICKUP) {
            if (!cursor.isEmpty() && !ItemStack.isSameItemSameComponents(cursor.item(), target.item())) {
                return null; // the cursor holds something else, a result slot cannot be swapped
            }
            if (target.craftable()) {
                return BedrockCrafting.craft(user, menu, slotId, 1, cursor.ref());
            }
            final int amount = button == 1 ? 1 : target.count();
            return List.of(new BedrockItemStackRequest.Take(Math.min(amount, target.count()), target.ref(), cursor.ref()));
        }

        if (input == ContainerInput.QUICK_MOVE) {
            // Shift click: move the result into the player inventory
            final BedrockGuiSlots.ResolvedSlot destination = firstInventorySlotFor(user, menu, target.item());
            if (destination == null) {
                return null;
            }
            final int space = destination.maxStackSize() - destination.count();
            if (space <= 0) {
                return null;
            }

            if (target.craftable()) {
                // Craft exactly as much as fits into the destination slot
                final int crafts = Math.max(1, space / Math.max(1, target.count()));
                return BedrockCrafting.craft(user, menu, slotId, crafts, destination.ref());
            }
            return List.of(new BedrockItemStackRequest.Take(Math.min(space, target.count()), target.ref(), destination.ref()));
        }

        return null; // throwing items out of a result slot / double clicking it is not a result interaction
    }

    /**
     * Finds the first player inventory slot (main inventory, then hotbar) that can hold the given item.
     */
    private static @Nullable BedrockGuiSlots.ResolvedSlot firstInventorySlotFor(final UserConnection user, final AbstractContainerMenu menu, final ItemStack item) {
        final BedrockContainerLayout layout = BedrockContainerLayout.of(menu);
        final int firstSlot;
        if (menu instanceof InventoryMenu) {
            firstSlot = 9;
        } else if (layout != null) {
            firstSlot = layout.containerSize();
        } else {
            return null;
        }

        BedrockGuiSlots.ResolvedSlot empty = null;
        for (int slot = firstSlot; slot < firstSlot + 36 && slot < menu.slots.size(); slot++) {
            final BedrockGuiSlots.ResolvedSlot candidate = BedrockGuiSlots.resolve(user, menu, slot);
            if (candidate == null) {
                continue;
            }
            if (!candidate.isEmpty()) {
                if (ItemStack.isSameItemSameComponents(item, candidate.item()) && candidate.count() < candidate.maxStackSize()) {
                    return candidate;
                }
                continue;
            }
            if (empty == null) {
                empty = candidate;
            }
        }
        return empty;
    }

    /**
     * {@code QUICK_MOVE}: shift click. Moves as much of the stack as possible into the other half of
     * the menu (container -> player inventory, player inventory -> container), filling up existing
     * stacks before using empty slots, exactly like the vanilla client does.
     */
    private static @Nullable List<BedrockItemStackRequest.Action> quickMove(final UserConnection user, final AbstractContainerMenu menu, final int slotId, final BedrockGuiSlots.ResolvedSlot source) {
        if (source.isEmpty()) {
            return null;
        }

        final List<Integer> candidates = quickMoveCandidates(menu, slotId);
        if (candidates.isEmpty()) {
            return null;
        }

        final List<BedrockGuiSlots.ResolvedSlot> resolvedCandidates = new ArrayList<>(candidates.size());
        for (final int candidateSlot : candidates) {
            final BedrockGuiSlots.ResolvedSlot resolved = BedrockGuiSlots.resolve(user, menu, candidateSlot);
            if (resolved != null) {
                resolvedCandidates.add(resolved);
            }
        }
        if (resolvedCandidates.isEmpty()) {
            return null;
        }

        final List<BedrockItemStackRequest.Action> actions = new ArrayList<>();
        int remaining = source.count();

        // 1) Top off stacks of the same item
        for (final BedrockGuiSlots.ResolvedSlot candidate : resolvedCandidates) {
            if (remaining <= 0) {
                break;
            }
            if (candidate.isEmpty() || !ItemStack.isSameItemSameComponents(source.item(), candidate.item())) {
                continue;
            }
            final int space = candidate.maxStackSize() - candidate.count();
            if (space <= 0) {
                continue;
            }
            final int amount = Math.min(remaining, space);
            actions.add(new BedrockItemStackRequest.Place(amount, source.ref(), candidate.ref()));
            remaining -= amount;
        }

        // 2) Then use empty slots
        for (final BedrockGuiSlots.ResolvedSlot candidate : resolvedCandidates) {
            if (remaining <= 0) {
                break;
            }
            if (!candidate.isEmpty()) {
                continue;
            }
            final int amount = Math.min(remaining, candidate.maxStackSize());
            actions.add(new BedrockItemStackRequest.Place(amount, source.ref(), candidate.ref()));
            remaining -= amount;
        }

        return actions;
    }

    /**
     * Computes the slot indices a shift click moves items into: everything that is not part of the
     * source half of the menu.
     */
    private static List<Integer> quickMoveCandidates(final AbstractContainerMenu menu, final int sourceSlot) {
        final List<Integer> candidates = new ArrayList<>(36);
        if (menu instanceof InventoryMenu) {
            // Player inventory only: move between main inventory and hotbar
            final boolean fromHotbar = sourceSlot >= 36;
            final int start = fromHotbar ? 9 : 36;
            final int end = fromHotbar ? 36 : 45;
            for (int slot = start; slot < end; slot++) {
                candidates.add(slot);
            }
            for (int slot = 9; slot < 45; slot++) {
                if (!candidates.contains(slot) && slot != sourceSlot) {
                    candidates.add(slot);
                }
            }
            candidates.remove(Integer.valueOf(sourceSlot));
            return candidates;
        }

        final BedrockContainerLayout layout = BedrockContainerLayout.of(menu);
        if (layout == null) {
            return candidates;
        }

        final int containerSize = layout.containerSize();
        if (sourceSlot < containerSize) {
            // container -> player inventory (main inventory first, then the hotbar)
            for (int slot = containerSize; slot < containerSize + 36; slot++) {
                candidates.add(slot);
            }
        } else {
            // player inventory -> container
            for (int slot = 0; slot < containerSize; slot++) {
                candidates.add(slot);
            }
        }
        return candidates;
    }

    /**
     * {@code SWAP}: a hotbar slot (number keys 1-9) or the offhand ({@code F}) is swapped with the
     * clicked slot. This is the "hotbar translation" of the GUI: Java sends
     * {@code ContainerInput.SWAP} with the hotbar index as button, Bedrock needs an explicit
     * {@code Swap} action between the hotbar slot and the clicked slot.
     */
    private static @Nullable List<BedrockItemStackRequest.Action> swap(final UserConnection user, final AbstractContainerMenu menu, final BedrockGuiSlots.ResolvedSlot target, final int button) {
        final BedrockGuiSlots.ResolvedSlot swapSlot;
        if (button >= 0 && button <= 8) {
            swapSlot = BedrockGuiSlots.resolveHotbar(user, menu, button);
        } else if (button == 40) {
            swapSlot = BedrockGuiSlots.resolveOffhand(user);
        } else {
            return null;
        }
        if (swapSlot == null) {
            return null;
        }

        if (swapSlot.ref().container() == target.ref().container() && swapSlot.ref().slot() == target.ref().slot()) {
            return null;
        }
        return List.of(new BedrockItemStackRequest.Swap(swapSlot.ref(), target.ref()));
    }

    /**
     * {@code THROW}: {@code Q} drops a single item, {@code Ctrl + Q} the whole stack.
     */
    private static @Nullable List<BedrockItemStackRequest.Action> throwItems(final UserConnection user, final BedrockGuiSlots.ResolvedSlot target, final int button) {
        if (target.isEmpty()) {
            return null;
        }
        final int amount = button == 0 ? 1 : target.count();
        return List.of(new BedrockItemStackRequest.Drop(amount, target.ref(), false));
    }

    /**
     * {@code PICKUP_ALL}: double click. Collects all matching stacks of the player inventory into the
     * cursor, which is what the Java client does for a double click on a slot while holding items.
     */
    private static @Nullable List<BedrockItemStackRequest.Action> collectToCursor(final UserConnection user, final AbstractContainerMenu menu, final BedrockGuiSlots.ResolvedSlot target) {
        if (target.isEmpty()) {
            return null;
        }

        final BedrockGuiSlots.ResolvedSlot cursor = BedrockGuiSlots.resolveCursor(user, menu);
        int space = cursor.isEmpty() ? target.maxStackSize() : cursor.maxStackSize() - cursor.count();
        if (space <= 0) {
            return null;
        }

        final List<BedrockItemStackRequest.Action> actions = new ArrayList<>();
        for (int slot = 9; slot < 45 && space > 0; slot++) { // main inventory + hotbar
            final BedrockGuiSlots.ResolvedSlot candidate = BedrockGuiSlots.resolve(user, menu, slot);
            if (candidate == null || candidate.isEmpty() || !ItemStack.isSameItemSameComponents(target.item(), candidate.item())) {
                continue;
            }
            final int amount = Math.min(space, candidate.count());
            actions.add(new BedrockItemStackRequest.Take(amount, candidate.ref(), cursor.ref()));
            space -= amount;
        }
        return actions;
    }

    /**
     * {@code CLONE}: middle click in creative mode. Java copies the clicked item (or, if the cursor
     * already holds items, refills the cursor) - Bedrock has to create the item on the server first,
     * see {@link BedrockCrafting#cloneCreative}.
     */
    private static @Nullable List<BedrockItemStackRequest.Action> cloneCreative(final UserConnection user, final AbstractContainerMenu menu, final BedrockGuiSlots.ResolvedSlot target) {
        final BedrockGuiSlots.ResolvedSlot cursor = BedrockGuiSlots.resolveCursor(user, menu);
        final ItemStack item = cursor.isEmpty() ? target.item() : cursor.item();
        if (item.isEmpty()) {
            return null;
        }

        final int amount = cursor.isEmpty() || !ItemStack.isSameItemSameComponents(cursor.item(), item)
            ? item.getMaxStackSize()
            : item.getMaxStackSize() - cursor.count();
        return BedrockCrafting.cloneCreative(user, item, Math.max(1, amount), cursor.ref());
    }

    /**
     * {@code QUICK_CRAFT}: dragging items over several slots. The Java client sends one click per slot
     * with a button that encodes the drag type and the drag phase:
     * <pre>
     *   phase = button &amp; 3   (0 = start, 1 = add, 2 = end)
     *   type  = button &gt;&gt; 2  (0 = distribute evenly, 1 = one item per slot, 2 = creative clone)
     * </pre>
     * Bedrock has no drag at all, so the movement is only sent when the drag ends, as one {@code Place}
     * action per visited slot.
     */
    private static @Nullable List<BedrockItemStackRequest.Action> drag(final UserConnection user, final AbstractContainerMenu menu, final int slotId, final int button) {
        final int phase = button & 3;
        final int type = (button >> 2) & 3;
        final BedrockGuiSession session = session(user);

        switch (phase) {
            case 0 -> { // drag start
                session.startDrag(type);
                if (slotId != NO_SLOT) {
                    session.addDragSlot(slotId);
                }
                return null; // nothing to send yet
            }
            case 1 -> { // one click per visited slot
                if (slotId != NO_SLOT) {
                    session.addDragSlot(slotId);
                }
                return null; // nothing to send yet
            }
            case 2 -> { // drag end
                final BedrockGuiSession.DragState state = session.endDrag();
                if (state == null || state.slots().isEmpty()) {
                    return null;
                }
                return distribute(user, menu, state);
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Turns a finished drag into {@code Place} actions, one per slot the drag visited.
     */
    private static @Nullable List<BedrockItemStackRequest.Action> distribute(final UserConnection user, final AbstractContainerMenu menu, final BedrockGuiSession.DragState state) {
        final BedrockGuiSlots.ResolvedSlot cursor = BedrockGuiSlots.resolveCursor(user, menu);
        if (cursor.isEmpty() || state.type() == 2) {
            return null; // creative clone drags would need one CraftCreative per slot
        }

        final List<BedrockGuiSlots.ResolvedSlot> targets = new ArrayList<>(state.slots().size());
        for (final int javaSlot : state.slots()) {
            final BedrockGuiSlots.ResolvedSlot target = BedrockGuiSlots.resolve(user, menu, javaSlot);
            if (target == null || target.result() || !target.isEmpty() && !ItemStack.isSameItemSameComponents(cursor.item(), target.item())) {
                continue;
            }
            targets.add(target);
        }
        if (targets.isEmpty()) {
            return null;
        }

        final List<BedrockItemStackRequest.Action> actions = new ArrayList<>(targets.size());
        if (state.type() == 1) { // one item per slot
            for (final BedrockGuiSlots.ResolvedSlot target : targets) {
                final int space = target.isEmpty() ? target.maxStackSize() : target.maxStackSize() - target.count();
                if (space > 0) {
                    actions.add(new BedrockItemStackRequest.Place(1, cursor.ref(), target.ref()));
                }
            }
            return actions;
        }

        // distribute evenly, the remainder goes to the first slots (vanilla behaviour)
        int remaining = cursor.count();
        int slotsLeft = targets.size();
        for (final BedrockGuiSlots.ResolvedSlot target : targets) {
            final int space = target.isEmpty() ? target.maxStackSize() : target.maxStackSize() - target.count();
            final int amount = Math.min(space, Math.min((remaining + slotsLeft - 1) / slotsLeft, remaining));
            slotsLeft--;
            if (amount <= 0) {
                continue;
            }
            actions.add(new BedrockItemStackRequest.Place(amount, cursor.ref(), target.ref()));
            remaining -= amount;
        }
        return actions;
    }

    private static BedrockGuiSession session(final UserConnection user) {
        BedrockGuiSession session = user.get(BedrockGuiSession.class);
        if (session == null) {
            session = new BedrockGuiSession(user);
            user.put(session);
        }
        return session;
    }

}
