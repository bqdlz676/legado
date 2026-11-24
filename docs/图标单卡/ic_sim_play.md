# ic_sim_play.xml（沙盘播放）

- 规格行（切片-24 地图/沙盘，逐字）：`暖金色实心三角 M8,6 L18,12 L8,18 Z。选中态外加旋转虚线光轨`
- 单段 path：`M8,6 L18,12 L8,18 Z`，实心填充 fillColor=#D4A54A（暖金，原文点名实心）。
- 圆角端点：strokeColor 同暖金，strokeWidth=1.5，lineCap/lineJoin=round，柔化三角尖角。
- 规范：24dp/24 viewport；坐标 0..24 内，三点 (8,6)(18,12)(8,18) 未越界。
- 选中态旋转虚线光轨：由运行时叠加，不在本 drawable 文件实现。
- 自检：minidom 良构（XML_OK，1 path）；grep 回证 24dp/24 viewport、fillColor=#D4A54A、round cap/join、pathData 逐字一致。
- 未验证项：未编译、未真机渲染；运行时光轨叠加效果未在本文件验收。
