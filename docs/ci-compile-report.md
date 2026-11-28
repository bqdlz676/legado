# 云端编译报告（2026-11-23，最新 run 45087598131 / commit 965d75a：BUILD SUCCESSFUL，已出 debug APK）

## 0. 最新结论（覆盖下文历史快照）
- `:app:compileAppDebugKotlin` 云端 **0 条 `e:`**；`:app:dexBuilderAppDebug`、`:app:packageAppDebug` 均通过，`assembleDebug` 成功。
- 产物：`app/build/outputs/apk/app/debug/app-app-debug.apk`；Actions artifact `legado-debug-apk`（ID 14780087410，98,912,116 字节）。
- 成功 run：https://github.com/bqdlz676/legado/actions/runs/45087598131 （commit 965d75a）。
- 本轮修复提交：`69d1888`（引擎外 52 条初修）→ `af783c1`（移除未暴露的 VelocityTracker）→ `965d75a`（竖向拖拽 dragAmount 为 Float，去 `.y`）。
- 未跑单元测试；APK 未装机/未真机验证；三引擎冻结签名与 applicationId/namespace 未改。

---

# 历史快照：云端编译报告（2026-11-23，run 45082967053 / commit fe28f57）

## 1. Workflow 推送状态
- `.github/workflows/build.yml` 已写入并随 `ba87c94`、`fe28f57` 两次推送 `origin/main`。
- 触发方式：push to main + workflow_dispatch。
-  runner：ubuntu-latest（镜像 ubuntu-26.04）；JDK temurin-21；SDK 预装 cmdline-tools，显式安装 `platforms;android-36`、`build-tools;36.0.0`、`platform-tools`。
- 构建命令：`./gradlew clean assembleDebug --no-daemon --stacktrace`；产物上传 `app/build/outputs/apk/**/*.apk`（artifact 名 `legado-debug-apk`）。

## 2. 云端 run 状态
| run | commit | 结论 | 链接 |
|---|---|---|---|
| 45082967053 | fe28f57 | failure（编译期，引擎外 52 条 Kotlin 报错） | https://github.com/bqdlz676/legado/actions/runs/45082967053 |
| 45082443831 | ba87c94 | failure（`setup-android@v3` 动作自身在新镜像装 cmdline-tools 失败，未到编译） | https://github.com/bqdlz676/legado/actions/runs/45082443831 |
| 45055132723 | a94928a | failure（首版引擎快照编译报错） | https://github.com/bqdlz676/legado/actions/runs/45055132723 |

- `:app:kspAppDebugKotlin` 成功（Room / Glide KSP 处理器通过）。
- 失败任务唯一：`:app:compileAppDebugKotlin`。未进入 dex / 打包 / APK 产出阶段。

## 3. 三引擎包修复清单（云端日志为准，已清零）

冻结签名未改：`VerticalLayoutEngine.layout/hitTest/invalidateCache`；`Chronology`、`ContextAnchor`；`BubbleResolver.resolve(paragraph,pos,anchor)`；sealed `Action` 四子类 `OpenMap/OpenPerson/OpenTimeline/OpenRelation` 原样保留（`SearchWeb/AddToNotebook` 亦未动）。

| 文件 | 云端报错 | 内部实现修法（签名不变） |
|---|---|---|
| `ui/book/read/vertical/VerticalModels.kt:75` | `CharKind` 为 internal，被 public 接口/对象暴露 | `internal enum class CharKind` → `enum class CharKind`（仅放宽可见性，枚举成员与顺序不变） |
| `ui/book/read/vertical/VerticalLayoutEngine.kt:24` | public 构造器形参暴露 internal `VerticalMeasureCache` | 从公开构造器收回 `cache` 形参，改为类体内 `private val cache = VerticalMeasureCache()`；工厂 `create()` 本就未传该参 |
| `help/bubble/BubbleResolver.kt:31 / :134` | 同名 `class BubbleResolver` 与 `object BubbleResolver` 同包重声明 | 实现类改名 `BubbleResolverImpl`（装配 `DefaultBubbleResolver` 同步改指）；入口 `object BubbleResolver` 及其 `resolve/queryToken/clearCache/preload` 签名不变 |
| `core/chronology/ContextAnchor.kt:116` | `year ?: range?.first` 为 `Int?`，传给 `dynastyOf(year: Int)` 类型不匹配 | 方法体加空值守卫：取不到锚年直接返回 null，再调 `Chronology.dynastyOf(anchorYear)`；`fun getDynasty(): String?` 签名不变 |

第二轮云端日志中，`ui/book/read/vertical/`、`core/chronology/`、`help/bubble/` 三个包路径下 **0 条 `e:` 报错**。

## 4. 剩余引擎外报错（按规则原样记录，未修）

共 52 条 `e:`，分布在 help / ink / ui/components / ui/main / ui/welcome。路径前缀均为 `app/src/main/java/io/legado/app/`。

### help
- `help/NameResolver.kt:54:57` Unresolved label.
- `help/NameResolver.kt:54:73` Cannot infer type for type parameter 'T'. Specify it explicitly.

### ui/book/read/ink
- `ui/book/read/ink/BubblePlacement.kt:13:12` Redeclaration: object BubblePlacement.
- `ui/book/read/ink/BubblePlacement.kt:27:8` Redeclaration: data class BubblePlacement.
- `ui/book/read/ink/BubblePlacement.kt:52:22` Unresolved reference 'clamp'.
- `ui/book/read/ink/BubblePlacement.kt:75:17` Unresolved reference 'clamp'.
- `ui/book/read/ink/InkBubble.kt:33:33` Unresolved reference 'roundToPx'.
- 原 `ui/book/read/ink/InkReaderShell.kt` 第 201 行：Cannot infer type for type parameter 'T'. Specify it explicitly.
- 原 `ui/book/read/ink/InkReaderShell.kt` 第 202 行：Unresolved reference 'place'.
- 原 `ui/book/read/ink/InkReaderShell.kt` 第 220 行：Unresolved reference 'x'.
- 原 `ui/book/read/ink/InkReaderShell.kt` 第 220 行：Unresolved reference 'y'.
- `ui/book/read/ink/InkTouch.kt:40:23` 'when' expression must be exhaustive. Add an 'else' branch.
> （已废·W-436：ui/book/read/ink/InkTouch.kt:40）

### ui/components
- `ui/components/InkEmptyState.kt:82:27` Function invocation 'width(...)' expected.（候选不适用子消息 1 条）
- `ui/components/InkEmptyState.kt:83:27` Function invocation 'height(...)' expected.（候选不适用子消息 1 条）
- `ui/components/InkInteractionState.kt:74:38` Unresolved reference 'Cancel'.
> （已废·W-436：ui/components/InkInteractionState.kt:74）

### ui/main/bookshelf
- `ui/main/bookshelf/BookshelfScreen.kt:24:43` Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file.
- `ui/main/bookshelf/GoldParticleField.kt:30:14` Type 'State<Float>' has no method 'getValue(Nothing?, KProperty0<*>)', so it cannot serve as a delegate.
- `ui/main/bookshelf/GoldParticleField.kt:52:52` Argument type mismatch: actual type is 'Float', but 'Double' was expected.
- `ui/main/bookshelf/GoldParticleField.kt:52:59` Argument type mismatch: actual type is 'Float', but 'Double' was expected.
- `ui/main/bookshelf/GoldParticleField.kt:58:33` Argument type mismatch: actual type is 'Double', but 'Float' was expected.
- `ui/main/bookshelf/GoldParticleField.kt:59:25` Argument type mismatch: actual type is 'Double', but 'Float' was expected.
- `ui/main/bookshelf/GoldParticleField.kt:65:33` Argument type mismatch: actual type is 'Double', but 'Float' was expected.
- `ui/main/bookshelf/GoldParticleField.kt:66:25` Argument type mismatch: actual type is 'Double', but 'Float' was expected.
- `ui/main/bookshelf/ShelfBookCard.kt:138:14` Unresolved reference 'graphicsLayer' on receiver of type 'Modifier'.

### ui/main/chrono
- `ui/main/chrono/ChronoGlassDock.kt:78:21` No parameter with name 'mass' found.
> （已废·W-436：ui/main/chrono/ChronoGlassDock.kt:78）
- `ui/main/chrono/ChronoGlassDock.kt:87:25` No parameter with name 'mass' found.
> （已废·W-436：ui/main/chrono/ChronoGlassDock.kt:87）
- `ui/main/chrono/ChronoGlassDock.kt:273:33` Argument type mismatch: actual type is '(??? (Unknown type for value parameter velocity)) -> Unit', but '() -> Unit' was expected.
> （已废·W-436：ui/main/chrono/ChronoGlassDock.kt:273）
- `ui/main/chrono/ChronoGlassDock.kt:273:35` Cannot infer type for value parameter 'velocity'. Specify it explicitly.
- `ui/main/chrono/ChronoScreen.kt:30:33` Unresolved reference 'graphicsLayer'.
- `ui/main/chrono/ChronoScreen.kt:188:33` Unresolved reference 'graphicsLayer'.

### ui/main/explore
- `ui/main/explore/InkExploreCards.kt:92:25` Argument type mismatch: actual type is 'TextAlign?', but 'TextAlign' was expected.
- `ui/main/explore/InkExploreMotion.kt:43:31` Argument type mismatch: actual type is 'Long', but 'Int' was expected.

### ui/welcome
- `ui/welcome/SplashChoreography.kt:118:31` Argument type mismatch: lambda 形参未推断，期望 `MutableList<Pair<Float, Float>>.() -> Unit`.
- `ui/welcome/SplashChoreography.kt:118:33` Cannot infer type for value parameter 'i'. Specify it explicitly.
- `ui/welcome/SplashChoreography.kt:124:15` Cannot infer type for type parameter 'A'.
- `ui/welcome/SplashChoreography.kt:124:15` Cannot infer type for type parameter 'B'.
- `ui/welcome/SplashChoreography.kt:124:15` Candidate `fun <A, B> A.to(that: B): Pair<A, B>` inapplicable (receiver type mismatch).
- `ui/welcome/SplashScene.kt:135:5` Candidate `val Canvas.nativeCanvas: Canvas` inapplicable (receiver type mismatch).
- `ui/welcome/SplashScene.kt:143:5` 同上.
- `ui/welcome/SplashScene.kt:148:5` 同上.
- `ui/welcome/SplashScene.kt:170:5` 同上.
- `ui/welcome/SplashScene.kt:189:17` 'val' cannot be reassigned.
- `ui/welcome/SplashScene.kt:189:23` Assignment type mismatch: actual type is 'Int', but 'Color' was expected.
- `ui/welcome/SplashScene.kt:193:13` 同上 nativeCanvas 候选不适用.
- `ui/welcome/SplashScene.kt:231:5` 同上.
- `ui/welcome/SplashScene.kt:238:5` 同上.
- `ui/welcome/SplashScene.kt:260:9` 同上.
- `ui/welcome/SplashScene.kt:265:9` 同上.
- `ui/welcome/SplashScene.kt:289:5` 同上.
- `ui/welcome/SplashScene.kt:308:5` 同上.

## 5. APK 产出情况
- **本轮（fe28f57）未产出 APK**：编译在 Kotlin 编译任务失败，upload-artifact 步骤被跳过；run artifacts 数 = 0。
- 历史成功 run 45052984005（commit `2d79a8a`，早于引擎快照 `a94928a`）留有一个旧 artifact `legado-debug`（约 87.9 MB，2026-11-22 18:11），**不含当前三引擎代码，不可作为本次构建产物**。下载入口：https://github.com/bqdlz676/legado/actions/runs/45052984005 （Artifacts 区）。
- applicationId `com.legado.app`、namespace `io.legado.app` 未改；debug 包签名走仓库内 `app/luowen.jks`（alias `luowen`）。

## 6. 未验证项显式声明
- **未跑本地 gradle**（硬红线）：全流程只在云端 Actions 编译，沙箱内未执行任何 `gradlew`。
- **单元测试未执行**：workflow 只跑 `assembleDebug`，未调 `test` / `:app:testAppDebugUnitTest`；三引擎包的 JVM 单测与 bench 均未在云端运行，是否通过未知。
- **引擎修复仅经云端编译期验证**：零 `e:` 报错 ≠ 运行时正确；分页/命中/气泡/纪年换算的实际行为未在真机或模拟器上验证（用户设备荣耀 WIN，12GB 内存，本次未装机）。
- 引擎外 52 条报错为静态编译诊断，未逐条读源码修；其中部分为候选不适用的子消息，修复时需结合上下文判断。
- JDK 冲突说明：工程 `jvmToolchain` 钉死 21、AGP 8.13.2 / Kotlin 2.4.20 / Gradle wrapper 8.14.4，JDK17 产不出 21 字节码；云端 workflow 采用 temurin-21。此点与「JDK17 出包」的既有偏好冲突，以工程实际为准。
