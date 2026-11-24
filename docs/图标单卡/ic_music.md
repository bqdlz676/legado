# ic_music 图标单卡（阅读页工具栏「音乐」·八分音符；`ic_sim_bgm` 将来复用）
- 文件：`app/src/main/res/drawable/ic_music.xml`，24dp/24 viewport，无填充纯描边；strokeWidth=1.5，strokeColor=#8B6914，lineCap/lineJoin=round。
- 符干：`M7,5 L7,19`
- 弧面旗：`M8,5 Q11.5,5.6 15,8 L15,14 Q11.5,13.4 8,12 Z`（上下边二次贝塞尔，锚点严守原文坐标）
- 孔：`M10,8 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M10,12 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z`
- 孔半径按原文 r=1 保留，未改用 0.9；两孔为空心描边圆（装饰孔）。
- 自检：XML 良构；strokeWidth/lineCap/lineJoin/#8B6914 各 3 处 grep 回证命中；路径坐标均在 0..24。
- 未验证：未编译、未真机渲染。
