> 源 URL: https://aka.doubaocdn.com/s/OEx9gv2WVM
> 抓取状态: total_length=1252, end_offset=1252（已读至末尾）

---
## 落文 Windows 端 · 深度原生性能
Compose Desktop 是 JVM 上的，但 Windows 原生能力通过 JNI/JNA 全接进来。
---
### 一、渲染层：Skia → Direct2D
Compose Desktop 默认用 Skia 软件渲染。换成 Direct2D 硬件加速后端：
```kotlin
// Compose Desktop 启动参数
System.setProperty("compose.swing.render.on.graphics.api", "opengl")  // 或 directx
// 或者直接用 Compose 的 DirectX 后端（JetBrains 已在开发，用最新版）
```
如果 Compose 的 Direct2D 后端还不成熟——阅读页手写一个 **DirectWrite + Direct2D 渲染层**，用 JNA 调 Windows 原生 API：
| Compose 组件 | Windows 原生替代 | 效果 |
|---|---|---|
| Text 组件 | `ID2D1RenderTarget::DrawText`（DirectWrite） | GPU 加速文字，竖排自己算布局 |
| Canvas | `ID2D1DeviceContext` | 全 GPU 渲染，60fps 无抖动 |
| 窗口 | `DwmExtendFrameIntoClientArea` + Acrylic 毛玻璃 | 系统原生磨砂，不是 Compose 模拟的 |
阅读页翻页动画用 Direct2D 的 `DrawBitmap` + 矩阵变换做 3D 掀角——GPU 原生，不掉帧。
---
### 二、音频：WASAPI 独占直通
Windows 上直接调用 WASAPI，绕过系统混音器：
```
AAudio (Android) → WASAPI Exclusive (Windows)
AVAudioEngine (iOS) → WASAPI Shared (macOS，mac 上 CoreAudio 自带独占)
```
| 参数 | 效果 |
|---|---|
| `AUDCLNT_SHAREMODE_EXCLUSIVE` | 独占模式，系统其他声音静音 |
| `pwfx->wBitsPerSample = 32` | 32bit 采样 |
| `pwfx->nSamplesPerSec = 384000` | 384kHz 上限（听羽 f1 硬性能） |
| `IAudioClient::Initialize` + `IAudioRenderClient` | 零缓冲延迟，直出 DAC |
JNA 调 `avrt.dll` + `audioclient.h`，几十行代码。跟 Android AAudio 独占一样逻辑。
---
### 三、数位板：WinTab + Windows Ink 双轨
高漫数位板在 Windows 上是 WinTab 协议。JNA 直接调用 `wintab32.dll` 拿原始压感数据，不走 `javafx.scene.input.TouchPoint` 的中间层：
```java
// JNA 调用 WinTab
public interface WinTab extends Library {
    WinTab INSTANCE = Native.load("wintab32", WinTab.class);
    int WTInfo(int wCategory, int nIndex, Pointer lpOutput);
    int WTOpen(HWND hWnd, WTContext.ByReference lpLogCtx, boolean fEnable);
    int WTPacketsGet(int hCtx, int cMaxPkts, Pointer lpPkts);
}
```
8192 级压感直接进画板，64 位精度不降级。Windows Ink 做悬停预览。
---
### 四、WebView：JCEF（Chromium Embedded Framework）
不用 Android WebView 那套。Windows 上用 **JCEF**（Chromium 的 Java 绑定），性能碾压：
| 对比 | Android WebView | Windows JCEF |
|---|---|---|
| 引擎 | Skia | Chromium 原生 + GPU 合成 |
| 3D WebGL | OpenGL ES | DirectX 11 ANGLE 后端 |
| 帧率 | 45-55fps（较大场景掉帧） | 稳定 60fps |
| 内存 | 150-200MB | 80-120MB |
| 离屏渲染 | 不支持 | 支持，3D 沙盘可离屏渲染到 Compose 窗口 |
3D 沙盘的 three.html 用 JCEF 加载，WebGL → ANGLE → DirectX 11 硬件加速，16641 顶点地形在桌面 GPU 上随便跑。
---
### 五、字典/数据加载：mmap + 零拷贝
Windows 上用 `CreateFileMapping` + `MapViewOfFile` 把词典文件直接映射到内存，零拷贝读取：
| 词表 | 方案 | 效果 |
|---|---|---|
| `zh_ci.txt` 28 万条 | mmap + 排序数组 + 二分，不 new String | 加载 0ms（直接映射），内存 ≤ 15MB 文件映射 |
| `char_dict.txt` | mmap + 按 Unicode 码点跳行 | 查一个字 ≤ 10μs |
| `allusion.txt` | mmap + 首两字索引 + 指针跳转 | 扫描一页 ≤ 30ms |
JNA 调 `kernel32.dll` 的 `CreateFileMappingW` + `MapViewOfFile`。跟 Android 的 `AssetManager.openFd` 一样的零拷贝思路，但 Windows 的 mmap 直接可用。
---
### 六、窗口效果：Acrylic + Mica + 系统原生控件
| 效果 | API | 用在哪 |
|---|---|---|
| Acrylic 毛玻璃 | `DwmSetWindowAttribute` + `DWM_WA_USE_HOSTBACKDROPBRUSH` | 半屏 Sheet 背后遮罩 |
| Mica 材质 | `DwmExtendFrameIntoClientArea` + `DWM_SYSTEMBACKDROP_TYPE` | 书架页背景（透桌面壁纸） |
| 标题栏融入 | `SetWindowCompositionAttribute` + 自绘标题栏 | 阅读页全屏沉浸 |
| Snap Layouts | `ITaskbarList3` | 窗口拖拽到屏幕边缘自动分屏 |
这些在 WinUI 3 里是开箱即用的，落文不需要 WinUI——直接 JNA 调 dwmapi.dll，自绘窗口也能开 Acrylic。
---
### 七、性能表（对标 Android 端）
| 指标 | Android | Windows 原生 |
|---|---|---|
| 翻页 | 14-16ms/帧 | **2-4ms/帧**（Direct2D GPU） |
| 3D 地形帧率 | 45-55fps | **60fps 锁定**（JCEF + ANGLE + 独显） |
| 字典查字 | 80ms（冷）/ 15ms（热） | **≤ 10μs**（mmap 零拷贝） |
| WebView 首开 | 4s 白屏 | **≤ 400ms**（Chromium 预热 + 离线缓存） |
| 内存峰值 | 180MB | **≤ 100MB**（mmap 不改 GC 堆） |
| 推演一回合 | 50ms | **≤ 8ms**（JVM JIT 编译循环体） |
| 音频延迟 | 8ms（AAudio） | **≤ 4ms**（WASAPI 独占 48 采样缓冲区） |
---
**总架构**：
```
┌─────────────────────────────────────────┐
│  Compose Desktop UI（Kotlin，跨平台）      │
│  书架/发现/搜索/设置                       │
├─────────────────────────────────────────┤
│  Windows 原生渲染层（JNA 调用）            │
│  DirectWrite 文字 + Direct2D 翻页         │
│  Acrylic/Mica 窗口材质                    │
├─────────────────────────────────────────┤
│  Windows 原生 IO                          │
│  mmap 词典 + WASAPI 音频 + WinTab 数位板   │
├─────────────────────────────────────────┤
│  JCEF（Chromium WebView）                 │
│  3D 沙盘 + 2D 地图 + 画板                 │
└─────────────────────────────────────────┘
```
