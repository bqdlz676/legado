# ic_sync.xml（同步）

- 规格行（切片-24 工具栏，逐字）：`两条弧线箭头圈成一个环。上弧从左到右、下弧从右到左，两端尖`。
- 环：圆心 (12,12) r=6.5；上弧 `M5.5,12 A6.5,6.5 0 0 0 18.5,12`（左→右越顶，sweep=0）；下弧 `M18.5,12 A6.5,6.5 0 0 1 5.5,12`（右→左绕底，sweep=1）。
- 箭头尖坐标：右尖 **(18.5,12)**，两翼 (15.5,9.8)/(15.5,14.2)；左尖 **(5.5,12)**，两翼 (8.5,9.8)/(8.5,14.2)。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor；坐标 x∈[5.5,18.5]、y∈[5.5,18.5]，落 0..24。
- 自检：minidom 解析良构（XML_OK，path 数=4）；grep 回证 strokeWidth=1.5 ×4、strokeColor=#8B6914 ×4、lineCap/lineJoin=round ×4、fillColor=0。
- 未验证项：未编译、未真机渲染。
