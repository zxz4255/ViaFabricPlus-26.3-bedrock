# 变更清单（相对原始 ViaFabricPlus-ver-26.3）

统计：新增 95 个文件，修改 6 个文件，删除 0 个文件。

## 修改的文件

| 文件 | 变更 |
|---|---|
| `README.md` | 声明内置基岩支持 + 容器 GUI 转译说明 |
| `build.gradle.kts` | 加入 ViaBedrock / MinecraftAuth / RakNet / NetherNet / libdatachannel 的 jar-in-jar 依赖 |
| `gradle.properties` | 描述加入 Bedrock |
| `gradle/libs.versions.toml` | 新增 5 个依赖坐标 |
| `settings.gradle.kts` | 加入 maven.lenni0451.net 与 repo.opencollab.dev（org.cloudburstmc.* / dev.opencollab）仓库 |
| `src/main/resources/fabric.mod.json` | 注册基岩 entrypoint 与 viafabricplus-bedrock.mixins.json |

## 新增的文件

### 1) 内置的官方基岩 addon（`viafabricplus-bedrock` 1.2.1-SNAPSHOT）

- `src/main/java/com/viaversion/viafabricplus/bedrock/ViaFabricPlusBedrock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/features/friends/BedrockFriendsService.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/features/friends/BedrockSocialService.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/features/friends/BedrockXboxError.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/features/global/BedrockAccount.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/features/realms/BedrockRealmTimelineService.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/features/realms/BedrockRealmsError.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/access/IConfirmScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/access/IServerAddress.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/access/MixinConfirmScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/access/MixinServerAddress.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/connection/MixinConnectScreen_1.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/connection/MixinConnection.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/connection/MixinConnection_1.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/connection/MixinServerNameResolver.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/integration/MixinJoinMultiplayerScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/integration/MixinLimitationsImpl.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/integration/MixinProtocolCategory.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/integration/MixinProtocolVersion.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/integration/MixinProtocolVersionMetadata.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/integration/MixinServerListsScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/integration/MixinServerStatusPinger.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/integration/MixinSettingsScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/core/integration/MixinVFPPopup.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinAbstractCauldronBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinBambooStalkBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinBedBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinBlockBehaviour_Properties.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinCactusBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinCandleCakeBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinChestBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinConduitBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinDoorBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinDragonEggBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinEndPortalFrameBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinEnderChestBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinLadderBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinLanternBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinLecternBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinSeaPickleBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/block/MixinTrapDoorBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/misc/MixinClientSuggestionProvider.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/misc/MixinInventoryScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/misc/MixinMinecraft.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/movement/MixinEntity.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/movement/MixinEntityFluidInteraction.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/movement/MixinEntityFluidInteraction_CurrentAccumulator.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/movement/MixinHoneyBlock.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/movement/MixinLivingEntity.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/movement/MixinLocalPlayer.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/features/movement/MixinPlayer.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/jjwt/MixinClasses.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/injection/mixin/jjwt/MixinDefaultJwtParserBuilder.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/protocoltranslator/impl/platform/ViaFabricPlusViaBedrockPlatform.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/protocoltranslator/impl/provider/viabedrock/ViaFabricPlusNettyPipelineProvider.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/protocoltranslator/impl/provider/viabedrock/ViaFabricPlusSkinProvider.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/protocoltranslator/impl/viabedrock/ViaFabricPlusViaBedrockConfig.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/protocoltranslator/network/BedrockConnectionUtil.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/protocoltranslator/network/NetherNetAddressParser.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/protocoltranslator/network/NetherNetInetSocketAddress.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/protocoltranslator/network/NetherNetJsonRpcAddress.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/protocoltranslator/network/NetherNetLanAddress.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/screen/impl/friends/BedrockFriendProfileScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/screen/impl/friends/BedrockFriendsScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/screen/impl/realms/AcceptInvitationCodeScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/screen/impl/realms/BedrockRealmTimelineScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/screen/impl/realms/BedrockRealmsScreen.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/screen/impl/settings/ActionListEntry.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/settings/base/ActionSetting.java`
- `src/main/java/com/viaversion/viafabricplus/bedrock/settings/impl/BedrockSettings.java`

### 2) 资源

- `src/main/resources/assets/viafabricplus-bedrock/icon.png`
- `src/main/resources/assets/viafabricplus-bedrock/lang/en_us.json`
- `src/main/resources/assets/viafabricplus-bedrock/lang/zh_cn.json`
- `src/main/resources/viafabricplus-bedrock.mixins.json`

### 3) 新增的容器 GUI 交互转译（本次开发，约 3346 行）

| 文件 | 行数 | 作用 |
|---|---|---|
| `.../bedrock/gui/BedrockContainerLayout.java` | 241 | 22 种容器菜单的 Java 槽位 → 基岩容器槽位表（含结果槽 50） |
| `.../bedrock/gui/BedrockCrafting.java` | 156 | 合成 / 创造模式复制 → `CraftRecipe`/`CraftCreative` + `Take(CreatedOutput[50])` |
| `.../bedrock/gui/BedrockCreativeItems.java` | 198 | 解析 `CreativeContent`(145)，创造物品网络 id 表 |
| `.../bedrock/gui/BedrockGuiBridge.java` | 575 | 点击策略（取放/交换/丢弃/收集/拖拽/输出槽/中键复制） |
| `.../bedrock/gui/BedrockGuiSender.java` | 102 | 包类型注册 + 组装 + 发送 + 调试日志 |
| `.../bedrock/gui/BedrockGuiSession.java` | 109 | 连接级会话：requestId 自增 + 拖拽会话状态 |
| `.../bedrock/gui/BedrockGuiSlots.java` | 231 | 槽位解析（光标/盔甲/副手/合成格/热键栏）+ netId 三级回退 |
| `.../bedrock/gui/BedrockGuiTypes.java` | 374 | `ItemStackRequest` / `ItemStackResponse` 的 ViaVersion `Type` 实现 |
| `.../bedrock/gui/BedrockItemData.java` | 227 | `ItemInstance` / `NetworkItemStack` / 配方成分读取器 |
| `.../bedrock/gui/BedrockItemStackRequest.java` | 186 | 请求记录 + 密封动作接口（含 `CraftRecipe`/`CraftCreative`） |
| `.../bedrock/gui/BedrockItemStackResponse.java` | 82 | `ItemStackResponse` 记录 |
| `.../bedrock/gui/BedrockRecipeBook.java` | 572 | 解析 `CraftingData`(31)，shaped/shapeless 网格匹配 |
| `.../bedrock/gui/BedrockResponseTracker.java` | 226 | 注册并解析 `ItemStackResponse`(148)，回填结果槽 netId |
| `.../bedrock/gui/BedrockStackRef.java` | 67 | 基岩槽位引用（容器名 + 下标 + stackNetworkId） |
| `src/main/java/.../injection/mixin/features/inventory/MixinMultiPlayerGameMode.java` | 49 | 挂 `MultiPlayerGameMode#handleContainerInput` 的 HEAD |

### 3b) 新增的单元测试

| 文件 | 行数 | 内容 |
|---|---|---|
| `src/test/java/.../bedrock/gui/BedrockItemStackRequestWireTest.java` | 114 | 逐字节断言 + 往返解码（Take/Swap/Drop/CraftCreative/CraftRecipe），按 **协议 1001 的 wire id / zig-zag 槽位**断言 |
| `src/test/java/.../bedrock/gui/BedrockItemStackResponseWireTest.java` | 189 | `ItemStackResponse` 逐字节断言（1001 无标志位 / 必写 netId）、拒绝响应、数字描述符与 air 截断 |
| `src/test/java/.../bedrock/gui/BedrockRecipeBookTest.java` | 211 | 手工构造 1001 版 `CraftingData` 包，验证解析对齐、偏移匹配、次数计算、运行时 id 解析失败时不匹配 |

### 3c) 协议目标与 1001 对齐

线上目标是 **基岩 1.26.30 / 协议 1001**，对应依赖 **ViaBedrock 0.0.29-20260720.172239-5**
（每个 ViaBedrock 版本只支持一个基岩协议号：0.0.30=1.26.45/2169、0.0.31=1.26.51/2193、
0.0.32=1.26.60/2225）。据此把线上格式从 1.26.6x 改回 1001：

- `ItemStackRequest`：动作 type 是 **byte**，wire id 表为 `CraftRecipe` 10、`CraftCreative` 12、
  `CraftNonImplemented` 16、`CraftResults` 17（1001 没有 `CraftReservedAction`）；槽位的
  `stackNetworkId` 是 **zig-zag varint**；
- `ItemStackResponse`：**没有** "has containers" 标志，OK 才跟容器数组，槽位的 netId 与两个名字都
  无条件写入；
- 配方成分描述符是 **数字运行时 id + data value**（`ItemDescriptorType` 用单字节），改由
  `ItemRewriter` 解析成标识符；
- `CraftingData` 是 **单数组按 type 分派**、shaped 的 ingredients **inline**、requirement 无条件跟随；
- `CreativeContent` 的分组类别是 **int LE**；`NetworkItemInstanceDescriptor` 的 air（runtimeId 0）
  表示结构结束。

换版本连带要改的 addon 代码（0.0.32 → 0.0.29 的 API 差异，已改完）：

- `BedrockProtocolVersion.BEDROCK_LATEST` → **`BedrockProtocolVersion.bedrockLatest`**（39 个 mixin 文件）；
- `...generated.PlayerAuthInputData` → **`...generated.PlayerAuthInputPacket_InputData`**（`MixinEntity`，
  常量仍是 `StartSwimming` / `StopSwimming`）。

详见 `docs/BEDROCK_CONTAINER_TRANSLATION.md` 第 4 节。

### 4) 文档

- `docs/BEDROCK_26_3_ANALYSIS.md`
- `docs/BEDROCK_CONTAINER_TRANSLATION.md`
