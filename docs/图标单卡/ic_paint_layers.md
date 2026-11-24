# ic_paint_layers.xml（画板工具栏 · 图层）

- 规格行（切片-24，逐字）：`两个重叠细线方块。上层 x=5 y=5→15→15→5。下层 x=9 y=9→19→19→9。`
- ①下层（先画，垫底）：`M9,9 H19 V19 H9 Z`（9,9→19,9→19,19→9,19 闭合）。
- ②上层（后画，覆盖）：`M5,5 H15 V15 H5 Z`（5,5→15,5→15,15→5,15 闭合）。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；仅描边、无 fillColor。
- 坐标核验：下层 9~19、上层 5~15，均落 0..24。
- 自检：minidom 良构（XML_OK），path=2；grep 回证 strokeWidth=1.5、#8B6914、lineCap/lineJoin=round 各 2 处；fillColor=0。
- 未验证项：未编译、未真机渲染。
