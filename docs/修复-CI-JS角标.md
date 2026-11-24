# 修复 CI 红 · BookSourcePartHasJsTest JS 角标用例

## 结论
采用**方案甲**：JS 角标真正走字符串资源。理由：同文件 `MetaRow` 内 explore/书架数角标已用
`stringResource`，JS 角标资源化与既有模式一致；孤儿清理删键仅因代码零引用，补回引用即闭环。

## 根因
`js_source_badge` 被孤儿扫描判零引用删除，而本类是源码文本扫描型测试，:77 仍断言该资源键，故失败；实现未坏。

## 改动文件（授权域内）
1. `app/src/main/res/values/strings.xml`：1045 行补回 `<string name="js_source_badge">JS</string>`。
2. `app/src/main/java/.../manage/SourceManageRow.kt`：111 行
   `if (source.hasJs) badges += "JS"` → `badges += stringResource(R.string.js_source_badge)`。
   证据：20 行 import `stringResource`、26 行 import `R`；103 行 `@Composable`、104 行 `MetaRow`，调用即在其作用域内，无需重构。
3. `app/src/test/.../BookSourcePartHasJsTest.kt`：:67/:78 扫描串同步为资源调用形式；语义保留——
   hasJs 条件追加、无角标不留占位、名称列 `weight(1f)` 在前；:77/:79/:81 断言不变。

## 自检证据
- strings.xml:1045 `<string name="js_source_badge">JS</string>`；SourceManageRow.kt:111 资源调用。
- 测试文件括号配平：paren/brace/bracket 差均 0。
- `scan-orphan-strings.py`：A 类 0、B 类 0；`js_source_badge` 在 kept，引用点为 SourceManageRow + 测试。

## 顺带体检（只查不改）
`app/src/test` 下 `projectFile(...)` 引用 354 处，初筛 16 处疑似缺失：9 处为 `$xxx` 插值占位误报；
2 处裸文件名由测试 helper 拼目录前缀，文件均存在；3 处为故意负向断言（VerticalSeekBar.kt、
selector_fillet_btn_bg.xml、shape_fillet_btn.xml，`assertFalse(exists())` 验证旧实现已拆）。
**无指向已删除文件的悬空扫描测试。**

## 未触碰
`ui/map/crossing/**`、`ui/card/*`、其他 locale；未做 git 写、未编译（沙箱禁编译）。
