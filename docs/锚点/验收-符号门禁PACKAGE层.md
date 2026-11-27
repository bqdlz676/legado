# 锚点卡 · 验收-符号门禁 PACKAGE 层

- 自身路径：docs/锚点/验收-符号门禁PACKAGE层.md
- 本卡路径：docs/锚点/验收-符号门禁PACKAGE层.md
- 关联报告：docs/验收-复核-符号门禁PACKAGE层.md
- 被验提交：4131445（`4131445d9f9204dd7b143a9cede5175deb1f217e`），采集时点 HEAD fc3e19c，祖先链核验退出码 0。

## 一、回源命令

- 改动面：`git diff --name-status 4131445^ 4131445`
- 修红 diff：`git show 4131445 -- app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt`
- 全仓门禁：`python3 data_scripts/check-symbol-existence.py`
- 门禁自测：`python3 data_scripts/check-symbol-existence.py --selftest`
- 回归 Compose：`python3 data_scripts/check-compose-pitfalls.py app/src`
- 回归语法：`python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt`
- 证据包：`python3 data_scripts/acceptance-evidence.py --rev 4131445`
- 跨包 import 全仓扫描：`grep -rn "import.*VerticalPageView" app/src screenshot/src --include=*.kt`

## 二、判据锚点（文件:行）

- `data_scripts/check-symbol-existence.py:531-599`：PACKAGE 层 `package_import_issues`；索引建表 :543-564；前缀链放行 :578-586；宽松兜底 :589-593；名字仓内无归 NODEF :595-596；退出码并入 :643-653。
- `data_scripts/check-symbol-existence.py:525-528`：`EXT_DECL_RE` 扩展声明正则（接收者字符类不含空格，泛型接收者扩展可能漏索引）。
- `data_scripts/check-symbol-existence.py:676-687`：坏样本一 NODEF；`:690-701` 坏样本二 ARITY；`:752-767` 坏样本三 PACKAGE；放行夹具 :704-749。
- `app/src/main/java/io/legado/app/ui/book/read/ink/VerticalPageView.kt:1`：包声明 `io.legado.app.ui.book.read.ink`；`:45` 主构造 `VerticalPageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)`。
- `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt:1`：同包声明；`:79` 残留使用点 `VerticalPageView(context)`；被删 import 旧版位于 :23。
- `app/src/main/java/io/legado/app/ui/book/read/ink/InkReaderShell.kt:132`、`:143`：同包另一引用方，无 import。
- 保留 vertical 包符号定义：`vertical/VerticalLayoutEngine.kt:20`；`vertical/AndroidVerticalFactory.kt:43`；`vertical/VerticalModels.kt:41`；`vertical/LegadoChapterAdapter.kt:18`、`:21`。
- 存量脏 import：`model/analyzeRule/AnalyzeUrl.kt:51`；`widget/dialog/BottomWebViewDialog.kt:92`；同名扩展 `utils/MapExtensions.kt:12`；另一同名 `help/http/OkHttpUtils.kt:120`、`:132`。
- 接线与分级：`rule-gate.sh:460-461`；`gate-levels.json:930-940` g63 blocking。

## 三、验收结论锚点

- 总结论：有条件通过（编译面待 CI）；阻断 0，非阻断盲区/缺口 4，观察项 1，存量债 1。
- 销项 1（PACKAGE 三形态）PASS；销项 2（全仓 NODEF 0 / ARITY 0 / PACKAGE 0）PASS；销项 3（修红正确性）PASS；销项 4（坏样本可单独证红，断言粒度与覆盖有缺口）有条件销项；销项 5（回归两道 + 四机检）PASS。
- 新发现问题编号 N1~N6，明细见报告第三节；加固条件 C-1/C-2 见报告第四节。

## 四、边界与未验证项

- 静态闸不做类型推断；重载解析、泛型约束、协程契约以云端 clean 编译 + 单测为准。
- 验收方未直拉 CI run 日志；台账 `dbe70ce` 记 44f5a6b Verify + Screenshot 双绿，待 CI 收口复核。
- 仓外探针夹具不属仓库交付物，tempfile 跑完自删。
