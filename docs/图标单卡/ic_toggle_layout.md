# ic_toggle_layout.xml（古/今切换）

- 规格行（切片-24 阅读页工具栏，逐字）：`左侧竖线 x=7 y=4→20。右侧横线 x=10→20 y=16 半宽。`
- 原文「左侧竖线 x=7 y=4→20」→ pathData `M7,4 L7,20`
- 原文「右侧横线 x=10→20 y=16 半宽」→ pathData `M10,16 L20,16`
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；原文未点名实心，故无 fillColor。
- 自检：minidom 解析良构（XML_OK）；grep 命中 strokeWidth/lineCap/lineJoin/strokeColor/pathData。
- 未验证项：未编译、未真机渲染。
