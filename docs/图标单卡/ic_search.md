# ic_search.xml（搜索）

- 规格行（切片-24，逐字）：`正圆 r=6 cx=9 cy=9。镜柄 x=13→17 y=13→17。`
- 两段 pathData：①镜片 `M3,9 a6,6 0 1,0 12,0 a6,6 0 1,0 -12,0 Z`；②镜柄 `M13,13 L17,17`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor（描边空心）。
- 坐标范围：镜片外接框 x/y 3..15；镜柄 13→17，全部落 0..24。
- 旧文件差异（已覆盖）：①viewport 48×48 → 24×24；②两段均带 fillColor=#39393A → 移除填充；③strokeColor #39393A → #8B6914；④strokeWidth 2 → 1.5；⑤原无 lineCap/lineJoin → round；⑥旧 48 网格圆心 20,20 r≈12.6、镜柄至 39.8,40.4 → 新 24 网格 cx9 cy9 r6、镜柄 13,13→17,17；⑦旧 autoMirrored=true 已去除；⑧旧为双弧填环路径，新为单段空心描边圆。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 viewport 24、strokeWidth=1.5、#8B6914、cap/join=round、无 fillColor。
- 未验证项：未编译、未真机渲染。
