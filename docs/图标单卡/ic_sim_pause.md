# ic_sim_pause.xml（暂停·双竖线）

- 规格行（切片-24 功能图标全集，逐字）：`左竖线 x=8 y=6→18。右竖线 x=16 y=6→18`。
- 两段 pathData：①`M8,6 L8,18`；②`M16,6 L16,18`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor（原文未点名实心）。
- 边界：x 7.25~16.75、y 5.25~18.75（round cap 外扩 0.75），均在 0..24 内。
- 自检：minidom 良构（XML_OK，2 paths）；grep 回证四描边属性在位、无 fillColor；坐标 0..24 内。
- 未验证项：未编译、未真机渲染。
