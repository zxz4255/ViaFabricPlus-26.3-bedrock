# ViaFabricPlus 26.3 × Bedrock 转译 — 代码分析报告

> 对象：Drive 中的 `ViaFabricPlus-ver-26.3.zip`、`ViaFabricPlus-ver-1.21.11.zip`、`Protocol-3.0 (1).zip`
> 产出：`ViaFabricPlus-26.3-bedrock/`（26.3 主干 + 内置基岩转译模式 + 新增容器 GUI 交互转译）

---

## 1. 结论速览

| 项 | 结论 |
|---|---|
| 两个 VFP 包的关系 | 不是同一分支的两个版本，而是**两条不同代际的代码线**：`1.21.11`（VFP 4.4.15，MC 1.21.11，**内置**基岩支持）与 `26.3`（VFP 5.1.4-SNAPSHOT，MC 26.3，**不含**基岩支持） |
| 26.3 里的"基岩转译模式" | 26.3 已把基岩支持**拆成独立 addon**（README 明确写：`viafabricplus-bedrock`，基于 ViaBedrock）。26.3 主工程里只有 3 个文件提到 bedrock，且都是数据 JSON / README，**没有任何基岩实现代码** |
| 本次"加入 26.3 的基岩转译模式项" | 把官方 `viafabricplus-bedrock`（1.2.1-SNAPSHOT，面向 MC 26.3 / VFP 5.x，70 个 Java 文件）**内置合并**进 26.3 主工程：设置组、账号、Realms/好友界面、RakNet/NetherNet 连接、协议平台/Provider、35 个 mixin、依赖与版本目录 |
| 为什么"箱子能打开但物品动不了" | ViaBedrock 中 `Container#handleClick(...)` 是**空实现**（`return false`），`ChestContainer` / `InventoryContainer` 等都没有覆写它；`InventoryPackets` 收到 Java `CONTAINER_CLICK` 后调用它，返回 false 就直接把包丢掉。**基岩方向根本没有"点击"，只有 ItemStackRequest**，而 ViaBedrock 从不发送这个包 |
| 本次新增 | `com.viaversion.viafabricplus.bedrock.gui`（9 个新文件，约 1350 行）：基岩 `ItemStackRequest` 编解码、Java 槽位 ↔ 基岩容器槽位映射（22 种容器菜单）、点击→动作合成（取/放/交换/丢弃/收集/拖拽/Shift 移动）、热键栏换位、请求发送与注册；1 个新 mixin 挂到 `MultiPlayerGameMode#handleContainerInput` |
| 转译目标版本 | **基岩 1.26.30 / 协议 1001**（ViaBedrock 0.0.29） |
| 验证程度 | 新增代码用**真实** `ViaBedrock-0.0.29` + `ViaVersion-5.12.3-SNAPSHOT` jar 编译通过（仅对 MC 26.3 / VFP API 用最小 stub 替代）；整体工程**无法在本环境完整构建**（需要 MC 26.3 + Fabric Loom + JDK 25），详见第 6 节 |

---

## 2. 三个压缩包是什么

### 2.1 `ViaFabricPlus-ver-1.21.11.zip`

- `gradle.properties`：`project_version=4.4.15`、`minecraft_version=1.21.11`、`fabric_api_version=0.141.4+1.21.11`
- 依赖：`net.raphimc:ViaBedrock:0.0.29-SNAPSHOT`、`dev.kastle.netty:netty-transport-raknet/nethernet:1.7.0`、`webrtc-java`
- 规模：`src` 下 474 个 Java 文件 + api 子模块 12 个
- 包结构（旧代际，"按功能命名"）：

```
com/viaversion/viafabricplus/
├── base/bedrock/                     NetherNet 地址类型
├── features/{block,interaction,item,movement,networking,...}
├── injection/access/base/bedrock/    IChunkTracker、IConfirmScreen、IServerAddress...
├── injection/mixin/base/connection/bedrock/      连接层 mixin
├── injection/mixin/features/bedrock/{block,movement,inventory,...}   ← 基岩特性 mixin
├── protocoltranslator/ProtocolTranslator.java     ← 中心静态类（含基岩管线注入）
└── settings/impl/BedrockSettings.java
```

`grep -ril bedrock` 命中 **96 个文件**——这就是 1.21.11 这条线"内置基岩"的全部痕迹。

### 2.2 `ViaFabricPlus-ver-26.3.zip`

- `gradle.properties`：`project_version=5.1.4-SNAPSHOT`、`jvm_version=25`，MC 版本来自 `gradle/libs.versions.toml`（`minecraft = "26.3"`）
- 构建系统换成了 `build-logic` + version catalog（`libs.xxx`），不再是 1.21.11 的 `de.florianreuth.baseproject` 插件
- 规模：`src` 下 653 个 Java 文件 + api 子模块 19 个
- 包结构（新代际，"按协议版本命名"）：

```
com/viaversion/viafabricplus/
├── features/{c0_28,c0_30cpe,global,v1_7_6,v1_8,v1_11_1,...,v1_21_5,v26_1,...}
├── injection/mixin/{core,features/<版本>,compat}
├── protocoltranslator/{ProtocolTranslationImpl.java, impl/provider/viaversion/...}
├── screen/{base,impl}
├── settings/{SettingsImpl.java, base/, impl/}
└── util/
```

关键：**26.3 主工程里没有基岩实现**。`grep -ril bedrock` 只命中 `versioned-registries.json`、`materials-1.19.4.json`、`README.md` 三处。README 原文：

> Support for Minecraft: Bedrock Edition servers and Realms is available through the separate
> [ViaFabricPlus Bedrock](https://github.com/florianreuth/viafabricplus-bedrock) addon, which is based on
> [ViaBedrock](https://github.com/RaphiMC/ViaBedrock).

### 2.3 `Protocol-3.0 (1).zip`

- Cloudburst Protocol 3.0（`bedrock-codec`），1691 个文件
- 本次用到它作为**基岩协议规范的权威参考**：
  - `data/inventory/ContainerSlotType.java` / `ContainerId.java` / `FullContainerName.java`
  - `data/inventory/itemstack/request/ItemStackRequest.java`、`action/*`（Take/Place/Swap/Drop/Consume/...）
  - `codec/v2168/BedrockCodecHelper_v2168#writeItemStackRequest` / `writeStackRequestSlotInfo` —— 线上格式
  - `codec/v407/serializer/ItemStackRequestSerializer_v407` —— 包体是"请求数组"

---

## 3. 两条代码线的架构差异（决定了移植方式）

| 维度 | 1.21.11（VFP 4.4.15） | 26.3（VFP 5.1.4） | 对移植的影响 |
|---|---|---|---|
| 中心类 | `ProtocolTranslator`（静态方法，`getTargetVersion()` / `getPlayNetworkUserConnection()` / 管线注入写在 `injectViaPipeline`） | `ProtocolTranslationImpl implements ProtocolTranslation`（api 接口，`ViaFabricPlus.api().targetVersion()` / `.userConnection()`）；管线注入挪到 `injection/mixin/core/connection/MixinConnection_1` | 基岩管线代码必须换入口 |
| 设置 | `SettingsManager` + `BedrockSettings`（`ModeSetting`/`ButtonSetting`） | `SettingsImpl` + api 的 `Settings`/`SettingGroup`，`EnumSetting`；addon 用 `ViaFabricPlus.api().settings().register("bedrock")` | 直接复用 addon 的写法 |
| 特性组织 | `features/bedrock/...` | `features/v1_12_2`、`features/global`…（按"从哪个版本开始"分组） | 基岩特性独立成 `bedrock/features/**`，不进 `features/**` |
| 构建 | 脚本内 `configureVVDependencies("jij")` | version catalog + `build-logic` 插件（`configuration.transitive_jar_in_jar` 等） | 依赖得写进 `libs.versions.toml` |
| 访问扩宽 | `*.accesswidener`（accessWidener v1） | `viafabricplus.classtweaker`（classTweaker v1） | addon 那条 `EntityFluidInteraction$CurrentAccumulator` 26.3 主工程**已有**，直接删掉 addon 的 accesswidener |
| MC API | 1.21.11 | 26.3（Java 25，`EntityEquipment` 重构等） | 1.21.11 的基岩 mixin 不能直接拷；本次用**官方 26.3 addon 源码**而不是硬拷 1.21.11 |

> 正因如此，把 1.21.11 的 96 个基岩文件硬搬进 26.3 是不可行的（MC API + VFP 架构 + 构建系统三重不兼容）。26.3 这条线的"基岩转译模式项"的**正确来源**就是官方 addon，本次也是这么做的。

---

## 4. 已完成的"加入 26.3 基岩转译模式项"

把 `viafabricplus-bedrock`（main 分支，2026-11-02，`1.2.1-SNAPSHOT`，面向 MC 26.3 / VFP 5.1.3）内置进 26.3 主工程：

### 4.1 源码

```
src/main/java/com/viaversion/viafabricplus/bedrock/
├── ViaFabricPlusBedrock.java                入口（实现 VFP 的 ViaFabricPlusEntrypoint）
├── features/{global/BedrockAccount, friends/*, realms/*}
├── injection/access/{IServerAddress,IConfirmScreen}
├── injection/mixin/{jjwt, core/access, core/connection, core/integration, features/{block, misc, movement}}
├── protocoltranslator/{network/*, impl/platform, impl/provider/viabedrock/*, impl/viabedrock/*}
├── screen/impl/{friends, realms, settings}
└── settings/{base/ActionSetting, impl/BedrockSettings}
```

70 个文件全部并入 `src/main/java`（包名不变，与主工程 `com.viaversion.viafabricplus.*` 同级共存）。

### 4.2 资源与注册

| 文件 | 变更 |
|---|---|
| `src/main/resources/fabric.mod.json` | `entrypoints.viafabricplus` 追加 `com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock`；`mixins` 追加 `viafabricplus-bedrock.mixins.json` |
| `src/main/resources/viafabricplus-bedrock.mixins.json` | 35 条 mixin 声明 + 本次新增 1 条（见 4.4） |
| `src/main/resources/assets/viafabricplus-bedrock/lang/{en_us,zh_cn}.json` | 基岩设置文案；新增 2 个开关文案（中英） |
| `viafabricplus-bedrock.accesswidener` | **删除**（对应的 class 26.3 主工程的 `viafabricplus.classtweaker` 已放开） |

### 4.3 依赖

`gradle/libs.versions.toml` 新增：

```toml
viabedrock                     = "net.raphimc:ViaBedrock:0.0.29-20260720.172239-5"   # 基岩 1.26.30 / 协议 1001
minecraftauth                  = "net.raphimc:MinecraftAuth:5.0.2"
netty-transport-raknet         = "org.cloudburstmc.netty:netty-transport-raknet:2.0.0.CR6-...-23"
netty-transport-nethernet      = "org.cloudburstmc.netty:netty-transport-nethernet:2.0.0.CR6-...-23"
libdatachannel-java-arch-detect= "dev.opencollab:libdatachannel-java-arch-detect:0.24.6.0-...-10"
```

`build.gradle.kts` 用 `jarInJar(...)`（Jij）打入，并排除 `brigadier`/`lz4-java`/`io.netty`/`bouncycastle`；`settings.gradle.kts` 增加 `maven.lenni0451.net/everything` 与 `repo.opencollab.dev/maven-snapshots`（`org.cloudburstmc.*`、`dev.opencollab`）。

### 4.4 新增设置项

`BedrockSettings` 增加两个开关（原设置组 `bedrock` 内）：

| key | 默认 | 含义 |
|---|---|---|
| `container_translation` | on | 容器界面交互转译（箱子/木桶/潜影盒/背包里移动物品） |
| `container_translation_debug` | off | 把每条转译出的动作打到日志（排查用） |

---

## 5. 缺口分析：基岩 GUI 为什么"能开不能动"

以 ViaBedrock 0.0.29 为准（`net/raphimc/viabedrock`）：

1. **Java → 基岩的点击路径是空的**

   `protocol/packet/InventoryPackets#registerServerbound(CONTAINER_CLICK, null, wrapper -> ...)`：

   ```java
   if (!container.handleClick(revision, slot, button, action)) {
       PacketFactory.sendJavaContainerSetContent(...);  // 只是把界面刷回原样
   }
   ```

   而 `api/model/container/Container`：

   ```java
   public boolean handleClick(final int revision, final short slot, final byte button, final ContainerInput action) {
       return false;   // 唯一的实现；ChestContainer / InventoryContainer 都没有覆写
   }
   ```

   → 任何点击都被判为"没处理"，物品自然不动。这就是用户看到的现象。

2. **基岩没有"点击"**

   基岩客户端在容器里移动物品，是发 `ItemStackRequest`（包 id 147）：一串 `Take`/`Place`/`Swap`/`Drop` 等原子动作，每个动作引用 `(容器名, 槽位, 该槽位预期的 stack network id)`，服务器逐条校验后回 `ItemStackResponse` + 刷新槽位。

3. **ViaBedrock 从不发送 ItemStackRequest**

   全仓库只有 `ServerboundBedrockPackets.ITEM_STACK_REQUEST` 这一处声明，无任何注册/发送/序列化代码（`grep -c "ITEM_STACK_REQUEST"` 结果为 1）。→ 必须在 VFP 这一层自己**序列化并发送**这个包。

4. **槽位编号完全不同**

   Java 是"一个窗口里 0..N 的线性槽位"，基岩是"容器名 + 容器内下标"，而且同一种界面双方编号也不同（例：玩家背包 Java `9..35` = 基岩 `InventoryContainer 9..35`，Java `36..44` = 基岩 `HotbarContainer 0..8`，Java `1..4`（2×2 合成）= 基岩 `CraftingInputContainer 28..31`，箱子 `0..26` = 基岩 `LevelEntityContainer 0..26`，木桶是 `BarrelContainer`，潜影盒是 `ShulkerBoxContainer`…）。

   → 需要一张完整的槽位映射表（`Minecraft 源码` 的 `AbstractContainerMenu` 槽位顺序 × `Protocol-3.0` 的容器枚举）。

---

## 6. 验证情况（务必阅读）

| 验证项 | 结果 |
|---|---|
| 新增文件语法/语义 | `javac`（**JDK 25**，`--release 25`，`-Xlint:all`）0 error / 0 warning |
| 新增代码对 **ViaBedrock 0.0.29** 与 **ViaVersion 5.12.3-SNAPSHOT** 真实 API 的调用 | ✅ 编译通过（仅用最小 stub 替代 MC 26.3 与 VFP 的少数类）。期间发现并修正了 `BedrockProtocol.INSTANCE` 在该版本并不存在等问题，改为 `Via.getManager().getProtocolManager().getProtocol(BedrockProtocol.class)` + 按包 id 注册 |
| 合并进来的 addon 对本工程 VFP API 的引用 | 用脚本比对了全部 `com.viaversion.viafabricplus.*` import：除 5 个属于 `viafabricplus-api` 子模块的类外，**全部能在本工程源码树中找到** |
| 整个工程 `gradlew build` | ❌ 本沙箱无法完成：需要 JDK 25 + Fabric Loom + 真实 MC 26.3 客户端 jar（Loom 要下载/反编译 MC）+ ViaVersion/ViaBedrock 快照仓库。属于"没有你的开发机就无法完成的步骤" |
| 游戏内实测（进基岩服务器开箱子拖物品） | ❌ 需要实机，见 `docs/BEDROCK_CONTAINER_TRANSLATION.md` 第 7 节测试清单 |

---

## 7. 与 1.21.11 那条线的对照表（移植时踩过的点）

| 1.21.11 写法 | 26.3 写法 | 说明 |
|---|---|---|
| `ProtocolTranslator.getTargetVersion()` | `ViaFabricPlus.api().targetVersion()` | 目标版本 |
| `ProtocolTranslator.getPlayNetworkUserConnection()` | `ViaFabricPlus.api().userConnection()` | 拿 `UserConnection` |
| `BedrockProtocolVersion.bedrockLatest` | `BedrockProtocolVersion.BEDROCK_LATEST` | 常量改名 |
| `InteractPacket_Action` | `InteractPacketPayload_Action` | 枚举改名 |
| `ProtocolTranslator.injectViaPipeline(...)` 内含 RakNet 配置 | `bedrock.injection.mixin.core.connection.MixinConnection_1`（NetherNet 配置 + 编解码器） | 管线注入位置变了 |
| `Mixins` 里 `features.bedrock.*` | `viafabricplus-bedrock.mixins.json` 的 `features.*` | 独立 mixin 配置（priority 1001） |
| `SettingsManager` / `BedrockSettings extends SettingGroup` | `ViaFabricPlus.api().settings().register("bedrock")` | 设置系统重写 |
