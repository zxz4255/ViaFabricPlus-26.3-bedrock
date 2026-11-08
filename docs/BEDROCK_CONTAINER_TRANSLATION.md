# 基岩版容器 GUI 交互转译（箱子 / 木桶 / 潜影盒 / 漏斗 / 背包 / 热键栏）

> 代码位置：`src/main/java/com/viaversion/viafabricplus/bedrock/gui/`
> Mixin：`src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/inventory/MixinMultiPlayerGameMode.java`
> 设置项：`bedrock.container_translation`（默认开）、`bedrock.container_translation_debug`（默认关）

---

## 1. 目标与原理

Java 客户端在容器里点击时，发的是 `ServerboundContainerClickPacket`
（`containerId` + `slot` + `button` + `ContainerInput`）。
基岩服务器**不认识**这个包，它只接受 `ItemStackRequest`（包 id 147）：

```
ItemStackRequest {
    requestId   : varint（每条连接内自增）
    actions[]   : 一串原子动作 Take / Place / Swap / Drop / Consume ...
    filters[]   : 空
    origin      : int LE = -1
}
每个动作里的槽位 = (容器名 ContainerEnumName, 容器内下标, 客户端认为该槽位的 stackNetworkId)
```

所以"让物品能动"= 在客户端把一次 Java 点击**展开成基岩的原子动作序列**，并用**服务器认可的 stackNetworkId** 发出去。

ViaBedrock 已经负责基岩 → Java 方向的同步（容器内容、光标物品、热键栏选中），所以我们可以在 **Java 侧读到与世界一致的镜像状态**，再据此生成请求：

```
                 ┌──────────────────────── Java 客户端 ────────────────────────┐
点击 ──► MultiPlayerGameMode#handleContainerInput
                 │        │
                 │        └─ BedrockGuiBridge（本次新增）
                 │              ├─ BedrockGuiSlots    槽位解析 + 取 stackNetworkId
                 │              ├─ 动作合成（本文第 3 节）
                 │              └─ BedrockGuiSender   序列化 + 发送
                 │                        │
                 │                        ▼  ItemStackRequest（自研 Type，见第 4 节）
                 │              ViaFabricPlus → ViaBedrock 管线 → 基岩服务器
                 └──────────────────────────────────────────────────────────────┘
基岩服务器 ──► ItemStackResponse + InventorySlot/InventoryContent ──► ViaBedrock ──► Java 界面刷新
```

关键设计取舍：**不做本地预测**（不在 Java 侧模拟点击结果）。基岩是强服务端权威，服务器会把每个变动过的槽位推回来，ViaBedrock 再转成 Java 的 `CONTAINER_SET_SLOT` / `SET_CURSOR_ITEM`。这样光标与槽位永远和服务器一致，代价是链接延迟下的手感延迟（真实基岩客户端也是同一条请求/响应往返）。

---

## 2. 槽位映射表（`BedrockContainerLayout` + `BedrockGuiSlots`）

### 2.1 玩家部分（所有界面通用）

| Java 槽位 | 含义 | 基岩容器名 | 基岩下标 |
|---|---|---|---|
| 9–35 | 主背包 | `InventoryContainer` | 同 Java（9–35） |
| 36–44 | 热键栏 | `HotbarContainer` | Java − 36（0–8） |
| 5–8 | 盔甲（仅背包界面） | `ArmorContainer` | Java − 5（0–3） |
| 45 | 副手（仅背包界面） | `OffhandContainer` | 0 |
| 1–4 | 2×2 合成格（仅背包界面） | `CraftingInputContainer` | 28–31（基岩 HUD 容器内） |
| 0 | 2×2 合成输出 | `CreatedOutputContainer` | 50（虚拟容器，合成后由服务器生成） |
| 光标 | `AbstractContainerMenu#getCarried()` | `CursorContainer` | 0（基岩 HUD 槽 0） |

### 2.2 方块/实体容器部分

| 菜单 | 容器槽数 | Java 槽位 → 基岩容器名 `[下标]` |
|---|---|---|
| `ChestMenu`（箱子/陷阱箱/末影箱） | `menu.slots.size() − 36` | `< n` → `LevelEntityContainer [slot]` |
| 木桶 | 27 | `BarrelContainer [slot]`（ViaBedrock 以 `ContainerType.CONTAINER` 打开，布局同箱子时按木桶枚举发请求亦可被接受；见第 8 节备注） |
| `ShulkerBoxMenu` | 27 | `ShulkerBoxContainer [slot]` |
| `HopperMenu` | 5 | `LevelEntityContainer [slot]` |
| `DispenserMenu`（发射器/投掷器） | 9 | `LevelEntityContainer [slot]` |
| `FurnaceMenu` | 3 | 0 → `FurnaceIngredientContainer[0]`；1 → `FurnaceFuelContainer[1]`；2 → `FurnaceResultContainer[50]`（输出） |
| `BlastFurnaceMenu` | 3 | 0 → `BlastFurnaceIngredientContainer[0]`；1 → `FurnaceFuelContainer[1]`；2 → `FurnaceResultContainer[50]`（输出） |
| `SmokerMenu` | 3 | 0 → `SmokerIngredientContainer[0]`；1 → `FurnaceFuelContainer[1]`；2 → `FurnaceResultContainer[50]`（输出） |
| `BrewingStandMenu` | 5 | 0–2 → `BrewingStandResultContainer[0..2]`；3 → `BrewingStandInputContainer[0]`；4 → `BrewingStandFuelContainer[4]` |
| `CraftingMenu`（工作台） | 10 | 0 → `CreatedOutputContainer[50]`（输出，`CraftRecipe` 生成）；1–9 → `CraftingInputContainer[slot+31]`（基岩从 32 开始） |
| `AnvilMenu` | 3 | 0 → `AnvilInputContainer[1]`；1 → `AnvilMaterialContainer[2]`；2 → `AnvilResultPreviewContainer[50]`（输出） |
| `EnchantmentMenu` | 2 | 0 → `EnchantingInputContainer[14]`；1 → `EnchantingMaterialContainer[15]` |
| `BeaconMenu` | 1 | 0 → `BeaconPaymentContainer[27]` |
| `StonecutterMenu` | 2 | 0 → `StonecutterInputContainer[3]`；1 → `StonecutterResultPreviewContainer[50]`（输出） |
| `SmithingMenu` | 4 | 0 → `SmithingTableTemplateContainer[53]`；1 → `SmithingTableInputContainer[51]`；2 → `SmithingTableMaterialContainer[52]`；3 → `SmithingTableResultPreviewContainer[50]`（输出） |
| `CartographyTableMenu` | 3 | 0 → `CartographyInputContainer[12]`；1 → `CartographyAdditionalContainer[13]`；2 → `CartographyResultPreviewContainer[50]`（输出） |
| `LoomMenu` | 4 | 0 → `LoomInputContainer[9]`；1 → `LoomDyeContainer[10]`；2 → `LoomMaterialContainer[11]`；3 → `LoomResultPreviewContainer[50]`（输出） |
| `GrindstoneMenu` | 3 | 0 → `GrindstoneInputContainer[16]`；1 → `GrindstoneAdditionalContainer[17]`；2 → `GrindstoneResultPreviewContainer[50]`（输出） |
| `MerchantMenu`（村民交易） | 3 | 0 → `Trade2Ingredient1Container[4]`；1 → `Trade2Ingredient2Container[5]`；2 → `Trade2ResultPreviewContainer[50]`（输出） |

> 说明：这些表与 Geyser（基岩→Java 的反方向）使用的 `javaSlotToBedrockContainer` 表一一对应，
> 因为"谁的槽位对应谁的槽位"在双向都是同一张映射；差别只在于我们是从 Java 点击出发合成请求。
> `ContainerEnumName` 与 Protocol-3.0 的 `ContainerSlotType` 是同一个枚举（只是命名风格不同：
> `LevelEntityContainer` ≡ `LEVEL_ENTITY`、`CursorContainer` ≡ `CURSOR`、`AnvilResultPreviewContainer`
> ≡ `ANVIL_RESULT` …）。
>
> **输出槽统一用基岩下标 50**：所有 `…ResultPreviewContainer`、`FurnaceResultContainer` 和虚拟的
> `CreatedOutputContainer` 在基岩里都只用槽 50 表示"这个界面的产出"，这与 Geyser 的
> `javaSlotToBedrock` 表完全一致（`AnvilInventoryTranslator` 2→`ANVIL_RESULT[50]`、
> `StonecutterInventoryTranslator` 1→`STONECUTTER_RESULT[50]`、`CraftingInventoryTranslator` 0→50 …）。

### 2.3 stackNetworkId 从哪来

基岩服务器会校验"客户端认为这个槽位里是哪个物品实例"。ViaBedrock 的 `Container` 里保存的就是
服务器下发的 `BedrockItem`，其 `netId()` 正是 stack network id：

```java
container.getItem(bedrockSlot).netId()   // null / 空槽 → 发 0
```

因此 `BedrockGuiSlots#slotRef(...)` 直接从 ViaBedrock 的 `InventoryTracker`
（`getContainerServerbound(javaContainerId)` / `getInventoryContainer()` / `getArmorContainer()` /
`getOffhandContainer()` / `getHudContainer()`）取 netId，而不是自己猜。

三级回退（`BedrockGuiSlots#stackNetworkId`）：

1. `CreatedOutputContainer` → 恒为 `0`（请求发出时物品还不存在，由服务器自己填）。
2. 打开的窗口容器里 `slot`（结果槽 = 50）的 `BedrockItem#netId()` —— 基岩把结果槽的内容按
   `InventorySlot{windowId, slot=50}` 下发，ViaBedrock 的 `Container` 数组足够大时就能读到。
3. 都没有 → 用 `BedrockResponseTracker` 记录的上一次 `ItemStackResponse` 里服务器报告的 netId；再没有就是 `0`。

---

## 3. 点击 → 动作合成（`BedrockGuiBridge`）

| Java `ContainerInput` | 场景 | 生成的基岩动作 |
|---|---|---|
| `PICKUP` 左键，光标空 | 槽位有物品 | `Take(min(count, 64), 槽位 → 光标)` |
| `PICKUP` 右键，光标空 | 槽位有物品 | `Take(ceil(count/2), 槽位 → 光标)` |
| `PICKUP`，光标有物品 | 目标槽空 | `Place(min(光标数, 剩余容量), 光标 → 槽位)` |
| `PICKUP`，光标有物品 | 同种物品 | `Place(右键1 / 左键补满, 光标 → 槽位)` |
| `PICKUP`，光标有物品 | 异种物品 | `Swap(光标 ↔ 槽位)` |
| `QUICK_MOVE`（Shift 点击） | — | 若干 `Place(源槽 → 各个目标槽)`：先补同种未满堆，再用空槽（箱子↔背包自动选方向） |
| `SWAP`（数字键 1–9） | — | `Swap(热键栏槽 ↔ 点击槽)` ← **热键栏换位转译** |
| `SWAP`（`F` 键，button=40） | — | `Swap(副手槽 ↔ 点击槽)` |
| `THROW`（`Q` / `Ctrl+Q`） | — | `Drop(1 / 整堆, 槽位, randomly=false)` |
| `PICKUP_ALL`（双击） | — | 对背包中每个同种堆：`Take(…, → 光标)` |
| `QUICK_CRAFT`（拖拽） | phase 0/1 只记录槽位，phase 2 一次发出 | 每格一条 `Place(...)`（见 3.3） |
| `CLONE`（创造模式中键） | — | `CraftCreative(创造物品网络 id, 1)` + `Take(创造输出[50] → 光标)`（见 3.2） |

这些规则与基岩客户端的实际行为对齐（右半堆、异种交换等等），也与 Geyser 反向翻译时的判定一致。

**热键栏说明**：基岩的"选中热键栏格"是 `MOB_EQUIPMENT` 包，ViaBedrock 已处理
（`InventoryContainer#setSelectedHotbarSlot` ← Java `SET_CARRIED_ITEM`），所以滚动/数字键切换本身是通的；
本次新增的是**容器界面里"数字键把物品换到热键栏"**这条路径（Java 只发 `SWAP`，基岩必须显式 `Swap`）。

### 3.1 输出槽：合成 / 熔炉 / 铁砧 …（`BedrockCrafting`）

输出槽在基岩里**不是可取的槽位**——物品由服务器生成。因此 `ResolvedSlot#result()` 为真的槽位走单独
分支 `BedrockGuiBridge#outputSlot(...)`，并按两类处理：

| 类型 | 判定 | 发出的请求 |
|---|---|---|
| 合成产出（工作台槽 0、背包 2×2 槽 0） | `ResolvedSlot#craftable()` | `[CraftRecipe(配方网络 id, 次数), Take(CreatedOutput[50] → 目标)]` |
| 机器产出（熔炉/高炉/烟熏炉、铁砧、切石机、锻造台、制图台、织布机、砂轮、村民交易） | `result() && !craftable()` | `[Take(数量, <kind>Result[50] → 目标)]`（服务器早已产出，直接取） |

`CraftRecipe` 需要**配方网络 id**，它在 `CraftingData`（包 31）里，ViaBedrock 完全没读 → 新增
`BedrockRecipeBook` 解析该包：

```
包体 = shaped[], shapeless[], multi[], shapelessUser[], shapelessChemistry[], shapedChemistry[],
       smithingTransform[], smithingTrim[], potionMix[], containerMix[], materialReducers[], cleanRecipes(bool)
shaped          = id(string), width(varint), height(varint), ingredients[]{type,descriptor,count},
                  results[]{ItemInstance}, uuid(16B), tag(string), priority(varint), assumeSymmetry(bool),
                  [hasRequirement(bool) → context(varint), [ingredients]] , netId(unsigned varint)
shapeless       = id(string), ingredients[], results[], uuid(16B), tag(string), priority(varint),
                  [hasRequirement(bool) → …] , netId(unsigned varint)          // 没有 assumeSymmetry
```

只保留 shaped / shapeless（工作台与 2×2 网格能产生的就是这两类），其余 9 个数组只做"精确跳过"以保持
字节对齐。匹配算法（`BedrockRecipeBook#find`）：

- shaped：对 3×3 / 2×2 网格枚举所有偏移，图案外的格必须为空，图案内的格按 `identifier + aux` 匹配；
  `aux == 0x7fff` 视为通配；
- shapeless：网格非空格的多重集合必须与配方成分一一对应；
- 可合成次数 = 各成分 `可用数量 / 需求数量` 的最小值（再被 `Take` 的 64 与目标槽容量夹一次）。

投入的物品不需要我们 `Consume`：服务器收到 `CraftRecipe` 后自己扣料、把产物放进 `CreatedOutput`。

### 3.2 创造模式中键复制（`BedrockCreativeItems`）

Java 的 `CLONE`（中键）语义是"凭空得到物品"，基岩对应 `CraftCreative`：

```
[ CraftCreative(创造物品网络 id, 1), Take(数量, CreatedOutput[50] → 光标) ]
```

创造物品网络 id 来自 `CreativeContent`（包 145），同样被 ViaBedrock 忽略：

```
包体 = groups[]{category(byte), name(string), icon(ItemInstance)},
       contents[]{ netId(unsigned varint), item(ItemInstance), groupId(unsigned varint) }
```

解析时用 `ItemRewriter#getItems()`（`BiMap<identifier, runtimeId>`）把 `runtimeId` 折成标识符，建成
`identifier → (aux → 创造网络 id)` 表；取用时把 Java 物品的注册名规整成同一形式再查。
Geyser 的 `PlayerInventoryTranslator#translateCreativeRequest` 正是按 `netId - 1` 索引自己的创造物品表，
即"创造网络 id 就是这条 contents 记录的 netId"，与此实现一致。

### 3.3 拖拽（`QUICK_CRAFT`）

Java 把一次拖拽拆成多次点击，`button` 编码了拖拽类型与阶段：

```
phase = button & 3     // 0 = 开始, 1 = 经过某格, 2 = 结束
type  = button >> 2    // 0 = 平均分配, 1 = 每格 1 个, 2 = 创造模式中键拖拽
```

基岩没有"拖拽"这个概念（原生客户端也是拖完才一次性发动作），所以：

- phase 0/1：只把槽位记进 `BedrockGuiSession.DragState`（`LinkedHashSet` 保序去重），**不发包**；
- phase 2：一次性生成动作——`type 1` 每格 `Place(1)`；`type 0` 按 `剩余/剩余格数` 向上取整分配，
  余数落在前排槽位（与 Java 一致）；目标槽若已装同种物品则按剩余容量夹紧；
- `type 2`（创造模式中键拖拽）需要每格一次 `CraftCreative`，暂不转译。

---

## 4. `ItemStackRequest` 线上格式（`BedrockGuiTypes`）

> **目标协议：基岩 1.26.30 / 协议号 1001。** 依据：ViaBedrock **0.0.29** 的
> `ProtocolConstants.BEDROCK_VERSION_NAME = "1.26.30"`、`BEDROCK_PROTOCOL_VERSION = 1001`
> （运行时 `BedrockProtocolVersion.bedrockLatest` 就是 `Bedrock 1.26.30 / protocol 1001`）。
> 因此序列化以 Cloudburst Protocol 3.0 里 **≤ 1001 的最新覆盖**为准
> （`BedrockCodecHelper_v554`、`v712` 的槽位格式、`ItemStackResponseSerializer_v419`、
> `BedrockCodecHelper_v766#readItemEntry`）。
>
> ViaBedrock 每个版本只支持**一个**基岩协议号（0.0.30 = 1.26.45 / 2169，0.0.31 = 1.26.51 / 2193，
> 0.0.32 = 1.26.60 / 2225），所以"把转译目标改成 1.26.3x"等于"把 ViaBedrock 换成 0.0.29"。
> 连带影响：0.0.29 面向的 Java 版本是 **v26_2**（`ProtocolConstants` 里引用
> `Protocol26_1To26_2`），26.3 客户端连进来时由 ViaVersion 的 26.2→26.3 协议补齐这一段。

ViaBedrock 0.0.29 没有该包的序列化器（它从不发送），因此按 Protocol-3.0
`BedrockCodecHelper_v554#writeItemStackRequest` + `v712#writeStackRequestSlotInfo` 实现：

```
包体 = array<unsigned varint> of ItemStackRequest        // 一包可含多个请求，我们每次发 1 个
ItemStackRequest:
    requestId                varint (zig-zag)
    actions                  array<unsigned varint>:
        actionType           byte             ← 1001 是字节；wire id，不是枚举值（见下）
        Take/Place  : byte amount, slot source, slot destination
        Swap        : slot source, slot destination
        Drop        : byte amount, slot source, bool randomly
        Consume     : byte amount, slot source
        Destroy     : byte amount, slot source
        CraftCreative: unsigned varint itemNetworkId, byte numberOfCrafts
        CraftRecipe  : unsigned varint recipeNetworkId, byte numberOfCrafts
    filterStrings            array<unsigned varint> of string   （空）
    textProcessingEventOrigin int LE                            （-1）
slot:
    FullContainerName{ byte containerId, bool hasDynamicId, [int LE dynamicId] }
    byte slot
    varint (zig-zag) stackNetworkId   ← 1001 是 zig-zag varint（2168 起才改成 int LE！）
```

`ItemStackRequestActionType` 的**线上 id 与枚举序号不一致**：协议在 712 起跳过了两个"容器类"动作
（`PlaceInItemContainer` / `TakeFromItemContainer`，只存在于服务端代码里的条目，
`Bedrock_v712#ITEM_STACK_REQUEST_TYPES` = `v686 的表.remove(7).remove(8)`），所以线上 id 要减 2。
`BedrockGuiTypes#wireId/fromWireId` 就是这张表（1001）：

| 动作 | wire id | 动作 | wire id |
|---|---|---|---|
| Take | 0 | CraftRecipe | 10 |
| Place | 1 | CraftRecipeAuto | 11 |
| Swap | 2 | **CraftCreative** | **12** |
| Drop | 3 | CraftRecipeOptional | 13 |
| Destroy | 4 | CraftRepairAndDisenchant | 14 |
| Consume | 5 | CraftLoom | 15 |
| Create | 6 | CraftNonImplemented | 16 |
| LabTableCombine | 7 | CraftResults | 17 |
| BeaconPayment | 8 | **CraftReservedAction 在 1001 不存在** | — |
| MineBlock | 9 | （2223 才加入，占 16，把后两个顶到 17/18） | — |

（`wireId` 是穷举 `switch`，新增动作时编译器会直接报错，避免又写成枚举值。）

### 4.1 `ItemStackResponse` 线上格式（协议 1001）

```
包体 = array<unsigned varint> of ItemStackResponse
ItemStackResponse:
    status                   byte (0 = OK, 1 = ERROR)
    requestId                varint (zig-zag)
    [status == OK] containers  array<unsigned varint>   ← 1001 没有"has containers"标志位
        container            FullContainerName{ byte containerId, bool hasDynamicId, [int LE] }
        slots                array<unsigned varint>:
            slot               byte
            hotbarSlot         byte
            count              byte
            stackNetworkId     int (zig-zag)      ← 无条件写入，不是 optional
            customName         string
            filteredName       string             ← 无条件写入，不是 optional
            durabilityCorrection int (zig-zag)
```

> 这一块是 1.26.x 里改动最大的地方：1001 是"OK 才跟容器数组、netId 与两个名字都必写"，
> 1.26.50（2193）起改成"一个 hasContainers 标志 + 一个 optional netId"，1.26.60（2225）又去掉了
> 第二个 bool。写错版本服务器会静默丢包（它会把名字的第一个字节当标志位读）。

### 4.2 配方成分的描述符在 1001 是**数字**

- 1001：`ItemDescriptorType` 是**一个无符号字节**（0 invalid / 1 default / 2 molang / 3 item_tag /
  4 deferred / 5 complex_alias）；DEFAULT 里是 `short LE 运行时 id` + `short LE data value`，之后才是
  堆叠数量（zig-zag varint）。`BedrockItemData#readIngredient` 用 `ItemRewriter` 把运行时 id 解析成
  标识符（解析不到就退化成 `#unknown:<id>`，永远匹配不上，避免"莫名全匹配"）。
- 2168 起描述符变成字符串（`"name"` + 标识符字符串），2225 起标识符里还会拼上 `:aux` 后缀
  （`BedrockCodecHelper_v2225#readItemDescriptor` 用 `Pattern.compile(":[0-9]+$")` 去掉）。

另外两条同源的坑：

- `NetworkItemInstanceDescriptor` 的 `runtimeId == 0`（air）表示**结构到此为止**，后面再没有
  count/aux/blockRuntimeId —— 按"总是读 count"实现会让整包错位（`BedrockCodecHelper_v431`）。
- `CreativeContent` 的分组类别在 1001 是 **int LE**（`CreativeContentSerializer_v776`），不是单字节。

发送前需要把包类型注册进 `BedrockProtocol`（否则管道无法编码）：

```java
final BedrockProtocol protocol = Via.getManager().getProtocolManager().getProtocol(BedrockProtocol.class);
final int id = ServerboundBedrockPackets.ITEM_STACK_REQUEST.getId();
protocol.registerServerbound(State.PLAY, id, id, wrapper -> { }, false);   // 幂等，只需一次
```

> 注意：0.0.29 的 `BedrockProtocol` **没有** `INSTANCE` 字段（1.21.11 那条线的写法在这里编译不过），
> 必须走 `ProtocolManager#getProtocol(Class)`。这是本次用真实 jar 编译时抓到的错误之一。

另外三个**接收方向**的包由本工程自己注册（ViaBedrock 全部未处理，落在
`UnhandledPackets` 里），在 `ViaFabricPlusBedrock#onPostProtocolTranslationLoading` 里完成：

| 包 | id | 谁用 | 用途 |
|---|---|---|---|
| `ITEM_STACK_RESPONSE` | 148 | `BedrockResponseTracker` | 记录服务器确认/拒绝、回填结果槽的 netId |
| `CRAFTING_DATA` | 31 | `BedrockRecipeBook` | 配方网络 id（`CraftRecipe` 用） |
| `CREATIVE_CONTENT` | 145 | `BedrockCreativeItems` | 创造物品网络 id（`CraftCreative` 用） |

---

## 5. 文件清单

| 文件 | 作用 |
|---|---|
| `gui/BedrockStackRef.java` | 基岩槽位引用（容器名 + 下标 + stackNetworkId） |
| `gui/BedrockItemStackRequest.java` | 请求记录 + 密封动作接口（Take/Place/Swap/Drop/Consume/Destroy/CraftCreative/CraftRecipe） |
| `gui/BedrockGuiTypes.java` | `ItemStackRequest` 数组的 ViaVersion `Type` 实现（读+写） |
| `gui/BedrockContainerLayout.java` | 22 种容器菜单的"Java 槽位 → 基岩容器槽位"表 |
| `gui/BedrockGuiSlots.java` | 槽位解析（含光标/盔甲/副手/2×2 合成格/热键栏），并从 ViaBedrock 取 netId |
| `gui/BedrockGuiBridge.java` | 点击策略（第 3 节表格的实现），入口 `handleContainerInput(...)` |
| `gui/BedrockGuiSession.java` | 连接级会话（requestId 自增 + 拖拽会话状态），存在 `UserConnection` 里 |
| `gui/BedrockItemStackResponse.java` | `ItemStackResponse` 记录（状态 / requestId / 容器槽位条目） |
| `gui/BedrockResponseTracker.java` | 注册并解析包 148，记录各槽 netId 与请求是否被接受 |
| `gui/BedrockItemData.java` | `ItemInstance` / `NetworkItemStack` / 配方成分（`ItemDescriptorWithCount`）读取器 |
| `gui/BedrockRecipeBook.java` | 解析 `CraftingData`（11 个数组 + cleanRecipes），shaped/shapeless 网格匹配 |
| `gui/BedrockCreativeItems.java` | 解析 `CreativeContent`，得到创造物品网络 id 表 |
| `gui/BedrockCrafting.java` | 合成 / 创造模式复制 → `CraftRecipe` + `Take(CreatedOutput)` 动作合成 |
| `gui/BedrockGuiSender.java` | 包类型注册 + 组装 + 发送 + 调试日志 |
| `injection/mixin/features/inventory/MixinMultiPlayerGameMode.java` | 挂 `MultiPlayerGameMode#handleContainerInput` 的 HEAD，成功转译则 `ci.cancel()` |

---

## 6. 构建与静态验证

> 26.3 的编译目标是 **Java 25**（`gradle.properties` 里 `jvm_version=25`，
> `build-logic/src/main/kotlin/base.java.gradle.kts` 把它设成 toolchain / source / target；
> 编译参数 `-nowarn -Xlint:-unchecked -Xlint:-deprecation`）。所以下面的验证全部用 **JDK 25** 完成。

1. 语义编译（用真实 jar，JDK 25，`--release 25`）：

   ```bash
   $JAVA_HOME=/path/to/jdk-25 ./bin/javac --release 25 -proc:none -Xlint:all \
       -cp ViaBedrock-0.0.29.jar:viaversion-common-5.12.3.jar:netty-*.jar:guava-33.3.1-jre.jar:log4j-api.jar \
       -d /tmp/cls src/main/java/com/viaversion/viafabricplus/bedrock/gui/*.java \
               src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/inventory/MixinMultiPlayerGameMode.java
   ```

   结果：**0 error / 0 warning**（`-Xlint:all`），class 文件 major version = **69（Java 25）**。
   抓出并修正过的真实 API 错误：`BedrockProtocol.INSTANCE` 不存在（要走
   `ProtocolManager#getProtocol(Class)`）、`ItemStackRequestActionType` 的实际枚举名
   （`CraftRecipe` / `CraftCreative` / `Take`…）、`CraftCreative` 载荷里还有 `numberOfCrafts` 字节、
   以及 **26.3 里 API 类的包路径**：`com.viaversion.viafabricplus.api.ViaFabricPlus` 在 26.3 中
   不存在，`ViaFabricPlus.api()` 在 `com.viaversion.viafabricplus.ViaFabricPlus` 上（返回
   `ViaFabricPlusAPI`），`targetVersion()` / `userConnection()` 是它的 default 方法。

   本轮（改目标协议为 1001 后）重新验证：上述 15 个文件 + mixin 对 **ViaBedrock 0.0.29** 在 JDK 25 上
   `--release 25 -Xlint:all` **0 error / 0 warning**。换版本时抓到的 API 变化：
   `BedrockProtocolVersion.BEDROCK_LATEST` 在 0.0.29 里叫 **`bedrockLatest`**（另有 `PROTOCOLS` 列表），
   `ItemStackRequestActionType` 在 0.0.29 里没有 `CraftReservedAction`，最后两个动作叫
   `CraftNonImplemented_DEPRECATEDASKTYLAING` / `CraftResults_DEPRECATEDASKTYLAING`；
   addon 里 39 个 mixin 用的 `BEDROCK_LATEST` 要改成 `bedrockLatest`，`MixinEntity` 用的
   `PlayerAuthInputData` 要改成 `PlayerAuthInputPacket_InputData`。
   另外把 addon 引用的 **26 个** `net.raphimc.viabedrock` 类逐个对着 0.0.29 的 jar 核过，
   **全部存在**。这次沙箱里没有 26.3 的 Minecraft 产物，所以 `net.minecraft.*`
   （`ItemStack` / `Slot` / `AbstractContainerMenu` / 各 `*Menu` / `LocalPlayer` / `Minecraft` /
   `BuiltInRegistries` / `ContainerInput` / `MultiPlayerGameMode`）和 3 个工程自有类
   （`ViaFabricPlus`、`ViaFabricPlusBedrock`、`BedrockSettings`）是用**最小 stub** 提供签名的，
   验证的是"我这 15 个文件对 jar 里真实 API 的用法"是否正确 —— 上面那条包路径错误就是这么抓出来的。

2. 单元测试（`src/test/java/.../bedrock/gui/`，JUnit 5）：
   - `BedrockItemStackRequestWireTest`：手写序列化器的**逐字节**断言（Take/Swap/Drop/CraftCreative/
     CraftRecipe 全部动作 + zig-zag requestId）与往返解码；
   - `BedrockRecipeBookTest`：按 Cloudburst 布局手工拼一个 `CraftingData` 包（1 个 shaped + 1 个
     shapeless + 9 个空数组），验证解析后**字节读空**、偏移匹配、可选次数计算、无匹配返回 `null`。
   - `BedrockItemStackResponseWireTest`：`ItemStackResponse` 的逐字节断言（1001 的"OK 才跟容器数组、
     netId 与两个名字都必写"）、拒绝响应、ingredient 的数字描述符（含 air）、以及
     `NetworkItemInstanceDescriptor` 的 air 截断。

   没有 Gradle 的环境里，用 **JDK 25** + `junit-platform-console-standalone 1.11.3` 直接跑了这 3 个
   测试类：**11 tests found / 11 successful / 0 failed**。其中两条断言是用协议真值校出来的：
   request 槽位与 response 槽位的 `stackNetworkId` 在 1001 **都是 zig-zag varint**
   （`BedrockCodecHelper_v712` / `v766` 用的都是 Cloudburst 的 `VarInts.writeInt`，它本身就做
   zig-zag）；而到了 2168 之后 request 槽位改成了 `writeIntLE`。也就是说这个字段在 1.26.x 线上
   换过一次编码，抄错版本会让服务器静默丢弃请求。
   顺带用运行时反射核对了包 id：`ITEM_STACK_REQUEST 147`、`ITEM_STACK_RESPONSE 148`、
   `CRAFTING_DATA 52`、`CREATIVE_CONTENT 145`，与 Cloudburst 在 1001 注册的 id 完全一致。
3. 注意：JDK 25 上跑 Netty 4.1.115 会打印两条 `sun.misc.Unsafe::allocateMemory` 的 terminally
   deprecated 警告，这是 Netty 自身的（ViaBedrock 依赖它），与本次新增代码无关。
4. 换到协议 1001 后修掉的六处格式差异（都是"写错就整包错位"的级别）：

   | 位置 | 2168+（1.26.4x/1.26.6x） | 1001（1.26.30）实际 | 依据 |
   |---|---|---|---|
   | 动作 type | unsigned varint | **byte** | `BedrockCodecHelper_v554#readItemStackRequest` |
   | 动作 id 表 | CraftNonImpl 17 / CraftResults 18 | **16 / 17**（无 CraftReserved） | `Bedrock_v712#ITEM_STACK_REQUEST_TYPES` |
   | request 槽 netId | int LE | **zig-zag varint** | `BedrockCodecHelper_v712#writeStackRequestSlotInfo` |
   | response 容器 | `bool` 标志 + optional netId | **无标志；OK 才跟数组**，netId/两个名字必写 | `ItemStackResponseSerializer_v419` + `v766#readItemEntry` |
   | 配方成分描述符 | 字符串标识符（2225 还带 `:aux`） | **数字运行时 id + data value** | `BedrockCodecHelper_v554#readItemDescriptor` |
   | CraftingData 布局 | 11 个分类数组、shaped 的 ingredients 是数组、requirement 有存在位 | **单数组按 type 分派**、shaped 的 ingredients **inline**、requirement **无条件跟随** | `CraftingDataSerializer_v465/v685/v748` |

5. 无法验证的部分（需你的开发机）：
   - `MC 26.3` 的 `AbstractContainerMenu`/各 `*Menu` 是否与 26.2 一致（本次按 1.21.x 稳定布局 +
     26.3 现有 mixin 的用法编写；`ChestMenu` 的槽数用 `menu.slots.size() - 36` 自适应，避免依赖
     `getRowCount()`）；
   - 基岩服务器是否接受我们合成的动作序列（需要实机）；
   - **Java 26.3 客户端 ↔ ViaBedrock 0.0.29（面向 v26_2）的链路**：VFP 会用 ViaVersion 的
     26.2→26.3 协议衔接，但沙箱里没有 26.3 的 Minecraft/loom 依赖，这一段只能在开发机上实测；
     如果实测发现链路不通，唯一的两条路是换回 0.0.32（= 基岩 1.26.60）或自行把 0.0.29 的
     Java 目标提到 26.3；
   - 0.0.29 的 `netty-transport-raknet` / `netty-transport-nethernet` / `libdatachannel` 具体
     快照版本（仓库只保留最新时间戳，无法回溯 2026-07 那一版）：现工程里仍是与 0.0.32 配套的
     `2.0.0.CR6-20261030.131938-23` / `0.24.6.0-20261029.091642-10`，若构建报 NoSuchMethod 需要
     换回 1.26.30 时期的快照。

---

## 7. 实机测试清单

1. 装好本工程 jar + Fabric API，进 **基岩服务器**（目标版本选 Bedrock）。
2. 打开背包（`E`）：
   - [ ] 左键拿起物品 → 光标出现物品（`Take` 成功，服务器回 `SET_CURSOR_ITEM`）
   - [ ] 左键放到另一个背包格 / 热键栏格（`Place`）
   - [ ] 右键拆分一半、右键单个放置
   - [ ] 数字键 1–9 把物品换进热键栏（`Swap` + 热键栏选中同步）
   - [ ] `Shift+点击` 在主背包↔热键栏之间搬家
   - [ ] `Q` 丢 1 个、`Ctrl+Q` 丢整堆
   - [ ] 双击收集同种物品到光标
3. 打开 **箱子 / 木桶 / 潜影盒 / 漏斗 / 发射器**：
   - [ ] 容器槽位 ←→ 背包槽位 双向移动
   - [ ] `Shift+点击` 一键搬运（容器 → 背包、背包 → 容器）
   - [ ] 容器里按数字键换到热键栏
4. **输出槽**：
   - [ ] 工作台 3×3 摆好配方 → 点结果槽取出（`CraftRecipe` + `Take(CreatedOutput[50])`）
   - [ ] 工作台 Shift+点结果槽 → 尽量合成并放进背包空槽
   - [ ] 背包 2×2 网格同样可点（合成格走 `CraftingInputContainer[28..31]`）
   - [ ] 熔炉/高炉/烟熏炉产出一格可取出
   - [ ] 铁砧 / 切石机 / 锻造台 / 制图台 / 织布机 / 砂轮 / 村民交易的结果槽可取出
5. **创造模式**：
   - [ ] 中键点物品 → 光标得到一整堆（`CraftCreative` + `Take(CreatedOutput[50])`）
   - [ ] 光标已有同种物品时中键 → 补满
6. **拖拽**：按住左键扫过多个空格 → 平均分配；右键拖 → 每格 1 个；拖拽结束时一次性生效。
7. 关闭界面后背包内容与基岩服务端一致（另开一格/其他客户端核对）。
8. 打开 `bedrock.container_translation_debug`，日志里应出现 `Bedrock item stack request #N: [...]`，
   `#N` 单调递增，动作里的槽位名与本文第 2 节一致。

排错建议：
- 完全没反应 → 看是否 `container.translation` 关着；日志有无 `Failed to register the Bedrock ItemStackRequest packet type`。
- 有请求但服务器不生效 → 最可能是 stackNetworkId 不匹配（例如刚进服镜像还没同步完），先开关一次界面强制全量同步；
  日志里 `@0` 表示该槽位 netId 为 0。

---

## 8. 已知限制 / 后续可做

已补上的三项（本轮）：

| 原限制 | 现状 |
|---|---|
| 输出槽（合成结果、熔炉产物、交易结果、铁砧/磨石/织布机结果）不能点 | ✅ 合成类走 `CraftRecipe` + `Take(CreatedOutput[50])`；机器类走 `Take(<kind>Result[50])` |
| 创造模式中键复制 | ✅ `CraftCreative` + `Take(CreatedOutput[50])`，创造物品网络 id 从 `CreativeContent` 解析 |
| 拖拽只做"全放 / 每格 1 个"两种近似 | ✅ 完整三阶段：phase 0/1 记录、phase 2 一次性生成平均分配或每格 1 个 |

仍存在的限制：

| 限制 | 原因 | 后续方案 |
|---|---|---|
| 铁砧重命名、切石机/织布机/砂轮/交易的商品选择 | 这些 UI 在基岩里用 `CraftRecipeOptional` / `CraftLoom` / `CraftRepairAndDisenchant` 等专用动作 + 专门的按钮包（`STONECUTTER` 选择、铁砧 `FilterText`），只发 `Take` 时服务器可能拒绝 | 按 Geyser 的 `shouldHandleRequestFirst`/`translateSpecialRequest` 逐个实现专用动作 |
| 创造模式中键**拖拽**（`type 2`） | 需要每格一次 `CraftCreative`，且要先去重成"每格一条 Take" | 复用 3.3 的会话状态，追加 N 条 `CraftCreative` |
| 带 aux 变体的老物品配方（如 `minecraft:wool` + aux） | Java 侧拿不到 aux，只按"标识符 + aux==0"匹配 | 用 `ItemRewriter#bedrockItem(Item)` 拿到 `data` 后再匹配 |
| `QUICK_MOVE` 多动作被服务器中途拒绝 | 基岩没有事务回滚 | 收到失败响应时依赖服务器重新下发槽位；日志会打印 `request #N was rejected` |
| 无配方（自定义服务器配方）时点工作台输出 | 服务器没在 `CraftingData` 里给出匹配配方 | 已安全降级：不生成请求，保持原状 |
| 服务端对非原生动作序列的反作弊 | 协议语义 | 按原生顺序生成（`CraftRecipe` 在前、`Take` 在后，Shift 先补堆后空槽），已在实现中遵循 |

> 反作弊提醒：基岩服务器（尤其 Realms）可能对非原生请求序列敏感。请按 VFP 自己的免责声明使用。
