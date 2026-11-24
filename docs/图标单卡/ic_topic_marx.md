# ic_topic_marx.xml（马克思 · 大写 M 变体）

- 规格行（切片-24，逐字）：`大写 M 变体：左右竖线 + 中间两个尖角顶点（简化 Marxist 符号）`。
- path 1 左竖线：`M6,5 L6,19`；path 2 右竖线：`M18,5 L18,19`；path 3 中间双尖角：`M6,19 L9,7 L12,15 L15,7 L18,19`（峰 9,7 / 谷 12,15 / 峰 15,7）。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor（纯描边）。
- 坐标核验：x∈6..18、y∈5..19，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=3；grep 回证 strokeWidth=1.5 ×3、strokeColor=#8B6914 ×3、cap/join=round ×3，fillColor=0。
- 未验证项：未编译、未真机渲染。
