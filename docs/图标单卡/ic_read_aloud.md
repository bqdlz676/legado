# ic_read_aloud.xml（朗读）

- 规格行（切片-24 阅读页工具栏，逐字）：`梯形喇叭口：左 x=5 y=8→x=9 y=6→x=9 y=18→x=5 y=16。口右侧两条弧线：第一条弧从 x=10 y=7 向右到 x=14 y=12。第二条弧从 x=11 y=5 向右到 x=17 y=12。`
- 梯形 pathData：`M5,8 L9,6 L9,18 L5,16 Z`（四点闭合，无填充）。
- 弧一 pathData：`M10,7 Q13.5,9.5 14,12`，端点 (10,7)→(14,12)，控制点在弦中点 (12,9.5) 右侧，向外（喇叭口右侧）凸。
- 弧二 pathData：`M11,5 Q16,8.5 17,12`，端点 (11,5)→(17,12)，控制点在弦中点 (14,8.5) 右侧，向外扩散；两弧末端 x 最大 17，未越 24。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 自检：minidom 解析良构（XML_OK）；grep 回证 strokeWidth/lineCap/lineJoin/strokeColor 各 3 处；坐标全部落 0..24。
- 未验证项：未编译、未真机渲染。
