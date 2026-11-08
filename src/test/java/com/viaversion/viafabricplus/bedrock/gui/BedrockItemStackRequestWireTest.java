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
import java.util.List;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the hand written {@code ItemStackRequest} serializer produces exactly the byte layout of
 * Bedrock protocol 1001 (1.26.30), as described by Cloudburst Protocol
 * ({@code BedrockCodecHelper_v554#writeItemStackRequest} + {@code Bedrock_v712#ITEM_STACK_REQUEST_TYPES}),
 * and that it can be decoded again.
 * <p>
 * Note the action ids and the slot layout, both of which differ from the 1.26.4x+ numbering:
 * <ul>
 *     <li>the action type is a single <b>byte</b>, and the wire numbering skips the two container actions
 *     of the server's internal enum, so {@code CraftRecipe} is sent as 10 and {@code CraftCreative} as
 *     12 - not the raw enum values 12 / 14,</li>
 *     <li>the stack network id of a slot is a <b>zig-zag var int</b> at 1001 (it only became an
 *     {@code int LE} in protocol 2168).</li>
 * </ul>
 */
public final class BedrockItemStackRequestWireTest {

    @Test
    void serializesLikeCloudburst() {
        final BedrockStackRef chestSlot = new BedrockStackRef(ContainerEnumName.LevelEntityContainer, 7, 1234);
        final BedrockStackRef cursor = BedrockStackRef.cursor(99);
        final BedrockItemStackRequest request = new BedrockItemStackRequest(5, List.of(
            new BedrockItemStackRequest.Take(3, chestSlot, cursor),
            new BedrockItemStackRequest.Swap(chestSlot, cursor),
            new BedrockItemStackRequest.Drop(1, cursor, false),
            new BedrockItemStackRequest.CraftCreative(42),
            new BedrockItemStackRequest.CraftRecipe(3, 2)
        ));

        final ByteBuf buffer = Unpooled.buffer();
        BedrockGuiTypes.ITEM_STACK_REQUEST_ARRAY.write(buffer, new BedrockItemStackRequest[]{request});

        // Container ids come from ViaBedrock's generated ContainerEnumName (1.26.30 data), the rest is
        // hand written from the Cloudburst layout
        final byte level = (byte) ContainerEnumName.LevelEntityContainer.getValue();
        final byte cursorContainer = (byte) ContainerEnumName.CursorContainer.getValue();

        final byte[] expected = {
            0x01,                                                       // 1 request
            0x0A,                                                       // request id 5 (zig-zag)
            0x05,                                                       // 5 actions
            0x00, 0x03,                                                 // Take, amount 3
            level, 0x00, 0x07, (byte) 0xA4, 0x13,                       // source: LevelEntityContainer[7], netId 1234
            cursorContainer, 0x00, 0x00, (byte) 0xC6, 0x01,             // destination: CursorContainer[0], netId 99
            0x02,                                                       // Swap
            level, 0x00, 0x07, (byte) 0xA4, 0x13,
            cursorContainer, 0x00, 0x00, (byte) 0xC6, 0x01,
            0x03, 0x01,                                                 // Drop, amount 1
            cursorContainer, 0x00, 0x00, (byte) 0xC6, 0x01,             // source: CursorContainer[0], netId 99
            0x00,                                                       // randomly = false
            0x0C, 0x2A, 0x01,                                           // CraftCreative (wire id 12), item network id 42, 1 craft
            0x0A, 0x03, 0x02,                                           // CraftRecipe (wire id 10), recipe network id 3, 2 crafts
            0x00,                                                       // no filter strings
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF             // text processing event origin = -1
        };

        final byte[] actual = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), actual);
        assertArrayEquals(expected, actual);
    }

    @Test
    void roundTrips() {
        final BedrockStackRef chestSlot = new BedrockStackRef(ContainerEnumName.LevelEntityContainer, 0, 0);
        final BedrockStackRef hotbar = new BedrockStackRef(ContainerEnumName.HotbarContainer, 3, 77);
        final BedrockItemStackRequest request = new BedrockItemStackRequest(0, List.of(
            new BedrockItemStackRequest.Place(5, hotbar, chestSlot),
            new BedrockItemStackRequest.Consume(1, chestSlot),
            new BedrockItemStackRequest.Destroy(64, chestSlot),
            new BedrockItemStackRequest.CraftRecipe(1234, 8),
            new BedrockItemStackRequest.CraftCreative(7, 1)
        ), List.of("filter"), 2);

        final ByteBuf buffer = Unpooled.buffer();
        BedrockGuiTypes.ITEM_STACK_REQUEST_ARRAY.write(buffer, new BedrockItemStackRequest[]{request});

        final BedrockItemStackRequest decoded = BedrockGuiTypes.ITEM_STACK_REQUEST_ARRAY.read(buffer)[0];
        assertEquals(request, decoded);
        assertEquals(0, buffer.readableBytes());
    }

}
