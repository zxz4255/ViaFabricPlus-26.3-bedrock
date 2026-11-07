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

import com.viaversion.viaversion.api.connection.StoredObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Per-connection state of the Bedrock GUI translation, stored in the {@link UserConnection}.
 * <p>
 * Every item stack request needs a request id that is unique per connection and increases
 * monotonically - the server uses it to match the {@code ItemStackResponse} to the request.
 * <p>
 * The session additionally keeps the state of an ongoing item drag: Java reports a drag as a series of
 * {@code QUICK_CRAFT} clicks (start, one click per visited slot, end), while Bedrock has no drag concept
 * at all - the client sends the individual {@code Place} actions once the drag finished. Collecting the
 * visited slots here is therefore mandatory for a correct drag translation.
 */
public final class BedrockGuiSession extends StoredObject {

    private int requestId = 0;
    private @Nullable DragState dragState;

    public BedrockGuiSession(final UserConnection user) {
        super(user);
    }

    /**
     * @return the next free request id
     */
    public int nextRequestId() {
        return this.requestId++;
    }

    /**
     * Starts (or restarts) a drag.
     *
     * @param type the Java drag type: 0 = distribute evenly, 1 = one item per slot, 2 = creative clone
     */
    public void startDrag(final int type) {
        this.dragState = new DragState(type);
    }

    /**
     * Records a slot the drag passed over.
     *
     * @return the current drag state, or {@code null} if no drag is running
     */
    public @Nullable DragState addDragSlot(final int javaSlot) {
        if (this.dragState == null) {
            return null;
        }
        this.dragState.slots().add(javaSlot);
        return this.dragState;
    }

    /**
     * @return the drag state and clears it
     */
    public @Nullable DragState endDrag() {
        final DragState state = this.dragState;
        this.dragState = null;
        return state;
    }

    /**
     * @return the running drag, or {@code null} if no drag is running
     */
    public @Nullable DragState dragState() {
        return this.dragState;
    }

    /**
     * State of an ongoing drag.
     *
     * @param type  the Java drag type (see {@link #startDrag(int)})
     * @param slots the Java slot indices the drag passed over, in click order
     */
    public record DragState(int type, Set<Integer> slots) {

        public DragState(final int type) {
            this(type, new LinkedHashSet<>());
        }

    }

}
