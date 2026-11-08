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

import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemStackRequestActionType;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

/**
 * Wire types for {@code ItemStackRequest} / {@code ItemStackResponse} packets.
 * <p>
 * Target protocol: <b>Bedrock 1.26.30 / protocol 1001</b> (ViaBedrock 0.0.29). ViaBedrock never sends
 * item stack requests and never parses responses, so both serializers live here. The layouts below are
 * taken from Cloudburst Protocol 3.0 - the newest overrides that are still {@code <= 1001}:
 * <ul>
 *     <li>{@code BedrockCodecHelper_v554#writeItemStackRequest} (+ {@code v712} slot info),</li>
 *     <li>{@code ItemStackResponseSerializer_v419} + {@code BedrockCodecHelper_v766#readItemEntry}.</li>
 * </ul>
 * <pre>
 * ItemStackRequest
 *   requestId                var int (zig-zag)
 *   actions                  array&lt;unsigned var int&gt;:
 *       actionType           byte                      <- not a var int at 1001
 *       Take / Place         byte amount, slot source, slot destination
 *       Swap                 slot source, slot destination
 *       Drop                 byte amount, slot source, boolean randomly
 *       Consume / Destroy    byte amount, slot source
 *       CraftRecipe          unsigned var int recipe network id, byte number of crafts
 *       CraftCreative        unsigned var int creative network id, byte number of crafts
 *   filterStrings            array&lt;unsigned var int&gt; of string (empty)
 *   textProcessingEventOrigin int LE (-1)
 *
 * slot
 *   FullContainerName{ byte container, bool hasDynamicId, [int LE dynamicId] }
 *   byte slot
 *   var int (zig-zag) stack network id       <- not int LE at 1001
 *
 * ItemStackResponse
 *   array&lt;unsigned var int&gt;:
 *       status               byte (0 = OK, 1 = ERROR)
 *       requestId            var int (zig-zag)
 *       [status == OK] containers array&lt;unsigned var int&gt;:   <- no "has containers" flag at 1001
 *           container        FullContainerName
 *           slots            array&lt;unsigned var int&gt;:
 *               slot           byte
 *               hotbarSlot     byte
 *               count          byte
 *               stackNetworkId var int (zig-zag)   <- unconditional, not optional
 *               customName     string
 *               filteredName   string             <- unconditional, not optional
 *               durability     var int (zig-zag)
 * </pre>
 * The response side is the part that changed most across the 1.26.x line: the "one optional network
 * id / one container flag" layout only exists since protocol 2193, and the identifier string based
 * item descriptors since protocol 2168. Writing those bytes against a 1.26.30 server would make it
 * drop every request.
 */
public final class BedrockGuiTypes {

    /**
     * Array of item stack requests, which is the payload of a single {@code ItemStackRequest} packet.
     */
    public static final Type<BedrockItemStackRequest[]> ITEM_STACK_REQUEST_ARRAY = new ItemStackRequestArrayType();

    /**
     * Array of item stack responses, which is the payload of a single {@code ItemStackResponse} packet.
     */
    public static final Type<BedrockItemStackResponse[]> ITEM_STACK_RESPONSE_ARRAY = new ItemStackResponseArrayType();

    private BedrockGuiTypes() {
    }

    /**
     * The id an {@code ItemStackRequest} action is sent under.
     * <p>
     * ViaBedrock's generated {@link ItemStackRequestActionType} values are the ones of the Bedrock
     * server's <b>internal</b> enum, which still contains the two packed container actions
     * ({@code PlaceInItemContainer} / {@code TakeFromItemContainer}) that were dropped from the wire in
     * protocol 712. Cloudburst models that with {@code Bedrock_v712#ITEM_STACK_REQUEST_TYPES}
     * ({@code Bedrock_v686#ITEM_STACK_REQUEST_TYPES.toBuilder().remove(7).remove(8)}), which shifts
     * everything above them down by two:
     * <pre>
     *   Take 0, Place 1, Swap 2, Drop 3, Destroy 4, Consume 5, Create 6,
     *   LabTableCombine 7, BeaconPayment 8, MineBlock 9,
     *   CraftRecipe 10, CraftRecipeAuto 11, CraftCreative 12, CraftRecipeOptional 13,
     *   CraftRepairAndDisenchant 14, CraftLoom 15,
     *   CraftNonImplemented 16, CraftResults 17
     * </pre>
     * {@code CraftReservedAction} does not exist at 1001 - it was added for protocol 2223 and only
     * appears in the 1.26.60 numbering, where it takes 16 and pushes the last two to 17 and 18.
     */
    public static int wireId(final ItemStackRequestActionType type) {
        return switch (type) {
            case Take -> 0;
            case Place -> 1;
            case Swap -> 2;
            case Drop -> 3;
            case Destroy -> 4;
            case Consume -> 5;
            case Create -> 6;
            case ScreenLabTableCombine -> 7;
            case ScreenBeaconPayment -> 8;
            case ScreenHUDMineBlock -> 9;
            case CraftRecipe -> 10;
            case CraftRecipeAuto -> 11;
            case CraftCreative -> 12;
            case CraftRecipeOptional -> 13;
            case CraftRepairAndDisenchant -> 14;
            case CraftLoom -> 15;
            case CraftNonImplemented_DEPRECATEDASKTYLAING -> 16;
            case CraftResults_DEPRECATEDASKTYLAING -> 17;
            default -> throw new IllegalArgumentException("Action is never sent by a client: " + type);
        };
    }

    /**
     * Inverse of {@link #wireId(ItemStackRequestActionType)}.
     */
    public static ItemStackRequestActionType fromWireId(final int id) {
        return switch (id) {
            case 0 -> ItemStackRequestActionType.Take;
            case 1 -> ItemStackRequestActionType.Place;
            case 2 -> ItemStackRequestActionType.Swap;
            case 3 -> ItemStackRequestActionType.Drop;
            case 4 -> ItemStackRequestActionType.Destroy;
            case 5 -> ItemStackRequestActionType.Consume;
            case 6 -> ItemStackRequestActionType.Create;
            case 7 -> ItemStackRequestActionType.ScreenLabTableCombine;
            case 8 -> ItemStackRequestActionType.ScreenBeaconPayment;
            case 9 -> ItemStackRequestActionType.ScreenHUDMineBlock;
            case 10 -> ItemStackRequestActionType.CraftRecipe;
            case 11 -> ItemStackRequestActionType.CraftRecipeAuto;
            case 12 -> ItemStackRequestActionType.CraftCreative;
            case 13 -> ItemStackRequestActionType.CraftRecipeOptional;
            case 14 -> ItemStackRequestActionType.CraftRepairAndDisenchant;
            case 15 -> ItemStackRequestActionType.CraftLoom;
            case 16 -> ItemStackRequestActionType.CraftNonImplemented_DEPRECATEDASKTYLAING;
            case 17 -> ItemStackRequestActionType.CraftResults_DEPRECATEDASKTYLAING;
            default -> throw new IllegalStateException("Unknown item stack request action id: " + id);
        };
    }

    private static final class ItemStackResponseArrayType extends Type<BedrockItemStackResponse[]> {

        private ItemStackResponseArrayType() {
            super(BedrockItemStackResponse[].class);
        }

        @Override
        public BedrockItemStackResponse[] read(final ByteBuf buffer) {
            final int length = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
            final BedrockItemStackResponse[] responses = new BedrockItemStackResponse[length];
            for (int i = 0; i < length; i++) {
                responses[i] = readResponse(buffer);
            }
            return responses;
        }

        @Override
        public void write(final ByteBuf buffer, final BedrockItemStackResponse[] responses) {
            BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(buffer, responses.length);
            for (final BedrockItemStackResponse response : responses) {
                writeResponse(buffer, response);
            }
        }

        private static BedrockItemStackResponse readResponse(final ByteBuf buffer) {
            final BedrockItemStackResponse.Status status = buffer.readByte() == 0
                ? BedrockItemStackResponse.Status.OK
                : BedrockItemStackResponse.Status.ERROR;
            final int requestId = BedrockTypes.VAR_INT.read(buffer);

            final List<BedrockItemStackResponse.ContainerEntry> containers = new ArrayList<>();
            if (status == BedrockItemStackResponse.Status.OK) {
                final int containerCount = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
                for (int i = 0; i < containerCount; i++) {
                    final FullContainerName containerName = BedrockTypes.FULL_CONTAINER_NAME.read(buffer);
                    final int slotCount = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
                    final List<BedrockItemStackResponse.SlotEntry> slots = new ArrayList<>(slotCount);
                    for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                        final int slot = buffer.readUnsignedByte();
                        buffer.readUnsignedByte(); // hotbar slot
                        final int count = buffer.readUnsignedByte();
                        final int stackNetworkId = BedrockTypes.VAR_INT.read(buffer);
                        BedrockTypes.STRING.read(buffer); // custom name
                        BedrockTypes.STRING.read(buffer); // filtered custom name
                        BedrockTypes.VAR_INT.read(buffer); // durability correction
                        slots.add(new BedrockItemStackResponse.SlotEntry(slot, count, stackNetworkId));
                    }
                    containers.add(new BedrockItemStackResponse.ContainerEntry(containerName, slots));
                }
            }

            return new BedrockItemStackResponse(status, requestId, containers);
        }

        private static void writeResponse(final ByteBuf buffer, final BedrockItemStackResponse response) {
            buffer.writeByte(response.status() == BedrockItemStackResponse.Status.OK ? 0 : 1);
            BedrockTypes.VAR_INT.write(buffer, response.requestId());
            if (response.status() != BedrockItemStackResponse.Status.OK) {
                return; // rejected requests carry no containers
            }

            BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(buffer, response.containers().size());
            for (final BedrockItemStackResponse.ContainerEntry container : response.containers()) {
                BedrockTypes.FULL_CONTAINER_NAME.write(buffer, container.container());
                BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(buffer, container.slots().size());
                for (final BedrockItemStackResponse.SlotEntry slot : container.slots()) {
                    buffer.writeByte(slot.slot());
                    buffer.writeByte(0); // hotbar slot
                    buffer.writeByte(slot.count());
                    BedrockTypes.VAR_INT.write(buffer, slot.stackNetworkId());
                    BedrockTypes.STRING.write(buffer, ""); // custom name
                    BedrockTypes.STRING.write(buffer, ""); // filtered custom name
                    BedrockTypes.VAR_INT.write(buffer, 0); // durability correction
                }
            }
        }

    }

    private static final class ItemStackRequestArrayType extends Type<BedrockItemStackRequest[]> {

        private ItemStackRequestArrayType() {
            super(BedrockItemStackRequest[].class);
        }

        @Override
        public BedrockItemStackRequest[] read(final ByteBuf buffer) {
            final int length = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
            final BedrockItemStackRequest[] requests = new BedrockItemStackRequest[length];
            for (int i = 0; i < length; i++) {
                requests[i] = readRequest(buffer);
            }
            return requests;
        }

        @Override
        public void write(final ByteBuf buffer, final BedrockItemStackRequest[] requests) {
            BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(buffer, requests.length);
            for (final BedrockItemStackRequest request : requests) {
                writeRequest(buffer, request);
            }
        }

        private static void writeRequest(final ByteBuf buffer, final BedrockItemStackRequest request) {
            BedrockTypes.VAR_INT.write(buffer, request.requestId()); // request id
            BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(buffer, request.actions().size());
            for (final BedrockItemStackRequest.Action action : request.actions()) {
                buffer.writeByte(wireId(action.type()));
                writeAction(buffer, action);
            }

            BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(buffer, request.filterStrings().size());
            for (final String filterString : request.filterStrings()) {
                BedrockTypes.STRING.write(buffer, filterString);
            }

            BedrockTypes.INT_LE.write(buffer, request.textProcessingEventOrigin());
        }

        private static void writeAction(final ByteBuf buffer, final BedrockItemStackRequest.Action action) {
            switch (action) {
                case BedrockItemStackRequest.Take take -> {
                    buffer.writeByte(take.amount());
                    writeSlot(buffer, take.source());
                    writeSlot(buffer, take.destination());
                }
                case BedrockItemStackRequest.Place place -> {
                    buffer.writeByte(place.amount());
                    writeSlot(buffer, place.source());
                    writeSlot(buffer, place.destination());
                }
                case BedrockItemStackRequest.Swap swap -> {
                    writeSlot(buffer, swap.source());
                    writeSlot(buffer, swap.destination());
                }
                case BedrockItemStackRequest.Drop drop -> {
                    buffer.writeByte(drop.amount());
                    writeSlot(buffer, drop.source());
                    buffer.writeBoolean(drop.randomly());
                }
                case BedrockItemStackRequest.Consume consume -> {
                    buffer.writeByte(consume.amount());
                    writeSlot(buffer, consume.source());
                }
                case BedrockItemStackRequest.Destroy destroy -> {
                    buffer.writeByte(destroy.amount());
                    writeSlot(buffer, destroy.source());
                }
                case BedrockItemStackRequest.CraftCreative craftCreative -> {
                    BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(buffer, craftCreative.itemNetworkId());
                    buffer.writeByte(craftCreative.numberOfCrafts());
                }
                case BedrockItemStackRequest.CraftRecipe craftRecipe -> {
                    BedrockTypes.UNSIGNED_VAR_INT.writePrimitive(buffer, craftRecipe.recipeNetworkId());
                    buffer.writeByte(craftRecipe.numberOfCrafts());
                }
            }
        }

        private static void writeSlot(final ByteBuf buffer, final BedrockStackRef slot) {
            BedrockTypes.FULL_CONTAINER_NAME.write(buffer, slot.containerName());
            buffer.writeByte(slot.slot());
            BedrockTypes.VAR_INT.write(buffer, slot.stackNetworkId());
        }

        private static BedrockItemStackRequest readRequest(final ByteBuf buffer) {
            final int requestId = BedrockTypes.VAR_INT.read(buffer);
            final int actionCount = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
            final List<BedrockItemStackRequest.Action> actions = new ArrayList<>(actionCount);
            for (int i = 0; i < actionCount; i++) {
                actions.add(readAction(buffer, fromWireId(buffer.readUnsignedByte())));
            }

            final int filterCount = BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer);
            final List<String> filterStrings = new ArrayList<>(filterCount);
            for (int i = 0; i < filterCount; i++) {
                filterStrings.add(BedrockTypes.STRING.read(buffer));
            }

            final int origin = BedrockTypes.INT_LE.read(buffer);
            return new BedrockItemStackRequest(requestId, actions, filterStrings, origin);
        }

        private static BedrockItemStackRequest.Action readAction(final ByteBuf buffer, final ItemStackRequestActionType type) {
            return switch (type) {
                case Take -> new BedrockItemStackRequest.Take(buffer.readByte(), readSlot(buffer), readSlot(buffer));
                case Place -> new BedrockItemStackRequest.Place(buffer.readByte(), readSlot(buffer), readSlot(buffer));
                case Swap -> new BedrockItemStackRequest.Swap(readSlot(buffer), readSlot(buffer));
                case Drop -> new BedrockItemStackRequest.Drop(buffer.readByte(), readSlot(buffer), buffer.readBoolean());
                case Consume -> new BedrockItemStackRequest.Consume(buffer.readByte(), readSlot(buffer));
                case Destroy -> new BedrockItemStackRequest.Destroy(buffer.readByte(), readSlot(buffer));
                case CraftCreative -> new BedrockItemStackRequest.CraftCreative(
                    BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer), buffer.readByte());
                case CraftRecipe -> new BedrockItemStackRequest.CraftRecipe(
                    BedrockTypes.UNSIGNED_VAR_INT.readPrimitive(buffer), buffer.readByte());
                default -> throw new IllegalStateException("Unsupported item stack request action: " + type);
            };
        }

        private static BedrockStackRef readSlot(final ByteBuf buffer) {
            final FullContainerName containerName = BedrockTypes.FULL_CONTAINER_NAME.read(buffer);
            final int slot = buffer.readByte();
            final int stackNetworkId = BedrockTypes.VAR_INT.read(buffer);
            return new BedrockStackRef(containerName.name(), slot, stackNetworkId);
        }

    }

}
