# 变更清单（相对原始 ViaFabricPlus-ver-26.3）

统计：新增 95 个文件，修改 8 个文件，删除 0 个文件。

## 修改的文件

| 文件 | 变更 |
|---|---|
| `README.md` | 声明内置基岩支持 + 容器 GUI 转译说明 |
| `build.gradle.kts` | 加入 ViaBedrock / MinecraftAuth / RakNet / NetherNet / libdatachannel 的 jar-in-jar 依赖 |
| `gradle.properties` | 描述加入 Bedrock |
| `gradle/libs.versions.toml` | 新增 5 个依赖坐标 |
| `libs/maven/…/CubeConverter-abecffaaea.{jar,pom}` | 新增（vendored 的本地 maven 仓库，ViaBedrock 0.0.29 运行期需要的模型互转库；以 module component 形式解析，避免依赖 JitPack 且能被 Loom 嵌套） |
| `settings.gradle.kts` | 加入 maven.lenni0451.net 与 repo.opencollab.dev（org.cloudburstmc.* / dev.opencollab）仓库、JitPack（仅 `com.github.*`，ViaBedrock 的 CubeConverter 只发布在那里） |
| `src/main/resources/fabric.mod.json` | 注册基岩 entrypoint 与 viafabricplus-bedrock.mixins.json |
| `build.gradle.kts` | `jarInJar(libs.viabedrock)` 增加 3 条 exclude：CubeConverter 的 pom 把 `viaversion-common:5.9.1` / `lombok` / `joml:1.10.8` 声明成 runtime 依赖，不排除会被当成嵌套 jar 打进 mod，和本工程的 viaversion-common 5.12.3-SNAPSHOT 冲突 |
| `.github/workflows/build.yml` | 构建前加 `chmod +x gradlew`（zip/Windows 解压会丢可执行位，CI 上 `./gradlew` 会 Permission denied） |
| `gradlew` | 权限改为 `755`（随 zip 一起打包，解压后可直接 `./gradlew build`） |

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

### 第三轮：`:compileJava` 报 `ProtocolConstants.BEDROCK_DEFAULT_PORT`（1 error）

- 报错：`BedrockSettings.java:62: cannot find symbol   variable BEDROCK_DEFAULT_PORT`
- 原因：ViaBedrock 0.0.29 的 `net.raphimc.viabedrock.protocol.data.ProtocolConstants` 里该常量叫
  **`BEDROCK_RAKNET_DEFAULT_PORT`**（= 19132），没有 `BEDROCK_DEFAULT_PORT` 这个名字
  （1.21.11 参考实现里用的就是 `BEDROCK_RAKNET_DEFAULT_PORT`，移植时写漏了 `RAKNET`）。
  已改正。0.0.29 同类常量一览：`BEDROCK_VERSION_NAME`、`BEDROCK_PROTOCOL_VERSION`(1001)、
  `BEDROCK_RAKNET_PROTOCOL_VERSION`(11)、`BEDROCK_RAKNET_DEFAULT_PORT`、`BEDROCK_NETHERNET_DEFAULT_PORT`、
  `BEDROCK_COMMAND_VERSION`、`BEDROCK_REQUEST_CHUNK_RADIUS_MAX_RADIUS`。
- **为什么之前的本地验证没发现**：`buildenv/verify.sh` 只编译 `bedrock/gui/*.java` + 测试 + 1 个 mixin，
  而 `buildenv/stubs/` 里恰好有一个同名的 `com/viaversion/viafabricplus/bedrock/settings/impl/BedrockSettings.java`
  stub，把真实文件"遮住"了 —— 真实源文件从未进入过本地编译。
- 补救：新增 `buildenv/symbolcheck.py`，对 **662 个源文件**逐个核对所有对真实 jar
  （ViaBedrock 0.0.29 + viaversion-common 5.12.3）的 `类.成员` 引用（javap 反射取成员表），
  当前结果 **0 problem**。这类"常量/方法名拼错"的错误以后能在本地直接抓到。
- 另外：这次 javac 把整个 `src/main/java` 全部过了一遍只报 1 个错，说明其余基岩代码
  （81 个文件 + 39 个 mixin）在 MC 26.3 / ViaBedrock 0.0.29 下**没有其它编译错误**。

### 第四轮：`:processIncludeJars` 报 "not a module component and has no capabilities"

- 报错：`Attempted to nest artifact CubeConverter-abecffaaea.jar which is not a module component and has no capabilities`
- 原因：上一版用 `jarInJar(files("libs/xxx.jar"))` 直接塞了一个 **file 依赖**。Fabric Loom 的
  `NestableJarGenerationTask` 只接受带 capability 的 **module** artifact，file 依赖没有 capability，于是打包阶段崩。
- 修复：把 jar 摆成标准 maven 目录 `libs/maven/com/github/oryxel1/CubeConverter/abecffaaea/{jar,pom}`，
  `settings.gradle.kts` 加 `maven(url = file("libs/maven"))`（content filter 限 `com.github.oryxel1`，排在 JitPack 前），
  `build.gradle.kts` 改成 `jarInJar(libs.cubeconverter)`（`libs.versions.toml` 新增 `cubeconverter` 坐标）。
- 本地验证（Gradle 9.8.2 + JDK 21，**把 JitPack 仓库整段删掉 + `GRADLE_USER_HOME` 全新**）：
  ```
  CubeConverter-abecffaaea.jar  ->  DefaultModuleComponentIdentifier  com.github.oryxel1:CubeConverter:abecffaaea
  ```
  即已是标准 module component（与 jjwt / mcstructs 等一样），Loom 可以正常嵌套；整条链路不再经过 JitPack。

## 附：首次 CI 构建失败与修复（`buildfailed.txt`）

| 报错 | 原因 | 修复 |
|---|---|---|
| `Could not find com.github.oryxel1:CubeConverter:abecffaaea` / `Required by: net.raphimc:ViaBedrock:0.0.29-...` | ViaBedrock 0.0.29 的 pom 里带了 CubeConverter（bedrock/Java 模型互转库），该坐标只发布在 **JitPack**，本工程仓库列表里没有 | `settings.gradle.kts` 加 `maven("https://jitpack.io")`，用 `content { includeGroupByRegex("com\.github\..+") }` 限定只查 `com.github.*`，不影响其他依赖的解析顺序 |
| （连带预防）CubeConverter 的 pom 声明 `viaversion-common:5.9.1`、`lombok`、`joml:1.10.8` 为 runtime 依赖 | `jarInJar` 配置是传递解析的，且 `include` 继承自它 → 这三者会被当作嵌套 jar 打进 mod，等于同一个 jar 里塞两套 ViaVersion | 在 `jarInJar(libs.viabedrock)` 里 exclude 这 3 个坐标：viaversion-common 由 `viafabricplus-api` 的 5.12.3-SNAPSHOT 提供，joml 由 Minecraft 自带，lombok 只在编译期用 |

### 第二轮：JitPack 仍然解析失败 → 改成仓库内 vendored 副本

`build.gradle.kts` 里给 `jarInJar(libs.viabedrock)` 再加一条 `exclude(group = "com.github.oryxel1", module = "CubeConverter")`，
并改用本地 maven 仓库里的同坐标产物（63 KB，sha256 `3080a286…d4485`，与 0.0.29 pom 里声明的 `abecffaaea` 同一份）。
这样整个解析链路**不再需要访问 JitPack**；`settings.gradle.kts` 里的 JitPack 仓库保留作备用（换更高版本 ViaBedrock 时会用到）。

本地用 Gradle 9.8.2 + JDK 21 做了两次隔离验证：

| 方案 | 配置 | 结果 |
|---|---|---|
| A：JitPack 仓库 + content 过滤 | 不加 vendored jar | ✅ `CubeConverter-abecffaaea.jar` 解析成功，且无 viaversion-common / lombok / joml |
| B：vendored jar + exclude（当前采用） | settings 里**完全不配 JitPack** | ✅ `include` 里出现 `CubeConverter-abecffaaea.jar`（会成为嵌套 jar），`implementation` 解析无冲突依赖，`transitive_jar_in_jar` 插件对 file 依赖不报错 |

顺带核对过 3 个 Cloudburst/opencollab 快照坐标（`netty-transport-raknet` / `nethernet` `2.0.0.CR6-20261030.131938-23`、`libdatachannel-java-arch-detect` `0.24.6.0-20261029.091642-10`）仍在 `repo.opencollab.dev/maven-snapshots/*-SNAPSHOT/` 下，未失效，故不做改动。
