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
import java.util.List;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@code ItemStackResponse} layout of Bedrock protocol 1001 (1.26.30) and the recipe
 * ingredient descriptor of the same protocol.
 * <p>
 * The response layout is the one Cloudburst Protocol 3.0 uses for 1001
 * ({@code ItemStackResponseSerializer_v419} + {@code BedrockCodecHelper_v766#readItemEntry}) and it is
 * very different from the 1.26.4x+ layout, which is why this is asserted byte by byte:
 * <ul>
 *     <li>containers are only present when the status is OK - there is <b>no</b> "has containers" flag,</li>
 *     <li>a slot entry always carries its network id (<b>no</b> optional flag) as a zig-zag var int,</li>
 *     <li>both names are plain strings instead of optional ones.</li>
 * </ul>
 * An implementation that writes the newer layout makes the server read the first name byte as a flag and
 * silently drop every request, so these bytes matter.
 */
public final class BedrockItemStackResponseWireTest {

    @Test
    void serializesLikeProtocol1001() {
        final BedrockItemStackResponse response = new BedrockItemStackResponse(
            BedrockItemStackResponse.Status.OK,
            9,
            List.of(new BedrockItemStackResponse.ContainerEntry(
                new FullContainerName(ContainerEnumName.CursorContainer, null),
                List.of(new BedrockItemStackResponse.SlotEntry(50, 64, 4242))
            ))
        );

        final ByteBuf buffer = Unpooled.buffer();
        BedrockGuiTypes.ITEM_STACK_RESPONSE_ARRAY.write(buffer, new BedrockItemStackResponse[]{response});

        final byte[] expected = {
            0x01,                                   // 1 response
            0x00,                                   // status OK
            0x12,                                   // request id 9 (zig-zag)
            0x01,                                   // 1 container (no "has containers" flag at 1001)
            (byte) ContainerEnumName.CursorContainer.getValue(), 0x00, // FullContainerName, no dynamic id
            0x01,                                   // 1 slot
            0x32,                                   // slot 50
            0x00,                                   // hotbar slot
            0x40,                                   // count 64
            (byte) 0xA4, 0x42,                      // stack network id 4242 (zig-zag, unconditional)
            0x00,                                   // custom name (empty)
            0x00,                                   // filtered custom name (empty, not optional)
            0x00                                    // durability correction
        };

        final byte[] actual = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), actual);
        assertArrayEquals(expected, actual);

        // and back
        final BedrockItemStackResponse decoded = BedrockGuiTypes.ITEM_STACK_RESPONSE_ARRAY.read(buffer)[0];
        assertEquals(BedrockItemStackResponse.Status.OK, decoded.status());
        assertEquals(9, decoded.requestId());
        assertEquals(4242, decoded.containers().get(0).slots().get(0).stackNetworkId());
        assertEquals(64, decoded.containers().get(0).slots().get(0).count());
        assertEquals(ContainerEnumName.CursorContainer, decoded.containers().get(0).container().name());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void rejectedResponsesCarryNoContainers() {
        final ByteBuf buffer = Unpooled.buffer();
        writeVarInt(buffer, 0x01); // one response
        buffer.writeByte(0x01); // status ERROR
        writeVarInt(buffer, 4 << 1); // request id 4, zig-zag

        final BedrockItemStackResponse decoded = BedrockGuiTypes.ITEM_STACK_RESPONSE_ARRAY.read(buffer)[0];
        assertEquals(BedrockItemStackResponse.Status.ERROR, decoded.status());
        assertEquals(4, decoded.requestId());
        assertTrue(decoded.containers().isEmpty());
        assertEquals(0, buffer.readableBytes()); // nothing follows a rejected response
    }

    @Test
    void readsNumericIngredients() {
        // protocol 1001: descriptor type 1 (default) = short LE runtime id + short LE data value,
        // followed by the stack size as a zig-zag var int
        final ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1); // descriptor type DEFAULT
        buffer.writeShortLE(35); // item runtime id
        buffer.writeShortLE(14); // data value
        writeVarInt(buffer, 2); // stack size 1 (zig-zag)

        final BedrockItemData.Ingredient ingredient = BedrockItemData.readIngredient(buffer, runtimeId -> runtimeId == 35 ? "minecraft:wool" : null);
        assertEquals("minecraft:wool", ingredient.identifier());
        assertEquals(14, ingredient.aux());
        assertEquals(1, ingredient.count());
        assertTrue(ingredient.matches("minecraft:wool", 14));
        assertTrue(!ingredient.matches("minecraft:wool", 0));
        assertTrue(!ingredient.matches(null, 0));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void readsAirIngredients() {
        // runtime id 0 = air: the data value is not part of the packet, and the slot has to stay empty
        final ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1); // descriptor type DEFAULT
        buffer.writeShortLE(0); // air
        writeVarInt(buffer, 2); // stack size 1

        final BedrockItemData.Ingredient ingredient = BedrockItemData.readIngredient(buffer, runtimeId -> null);
        assertNull(ingredient.identifier());
        assertTrue(ingredient.matches(null, 0)); // empty slot
        assertTrue(!ingredient.matches("minecraft:stone", 0));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void readsItemInstances() {
        final ByteBuf buffer = Unpooled.buffer();
        writeVarInt(buffer, 5 << 1); // runtime id 5 (zig-zag)
        buffer.writeShortLE(3); // count
        writeVarInt(buffer, 2); // data value
        writeVarInt(buffer, 0); // block runtime id
        writeVarInt(buffer, 0); // item user data length

        final BedrockItemData.Instance instance = BedrockItemData.readItemInstance(buffer);
        assertEquals(5, instance.runtimeId());
        assertEquals(3, instance.count());
        assertEquals(2, instance.aux());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void airItemInstanceEndsTheStructure() {
        final ByteBuf buffer = Unpooled.buffer();
        writeVarInt(buffer, 0); // runtime id 0 = air, nothing follows
        writeVarInt(buffer, 0x7F); // sentinel that must not be consumed

        final BedrockItemData.Instance instance = BedrockItemData.readItemInstance(buffer);
        assertTrue(instance.isEmpty());
        assertEquals(1, buffer.readableBytes());
    }

    private static void writeVarInt(final ByteBuf buffer, final int value) {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            buffer.writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        buffer.writeByte(remaining);
    }

    static void writeString(final ByteBuf buffer, final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buffer, bytes.length);
        buffer.writeBytes(bytes);
    }

}
