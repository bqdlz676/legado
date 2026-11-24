# ic_popup_highlight.xml（划线·气泡菜单）
- 规格行（切片-24 阅读页气泡弹出菜单，逐字）：`一条横线 x=5→19 y=12，两端各小圆点 r=1。线正上方一个极小的倒三角（画笔尖暗示）。`
- 三段 pathData：
  1. 横线：`M5,12 L19,12`
  2. 两端点（一条 path 两子路径，空心描边 r=1，无 fillColor）：`M4,12 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z M18,12 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0 Z`
  3. 倒三角（尖朝下）：`M10.5,6.5 L13.5,6.5 L12,9 Z`
- 填充：全程无 fillColor；圆点取规格允许的空心描边点，三角为描边轮廓。间隙：下尖 (12,9) 距横线 y=12 坐标间隙 3dp，扣 stroke 半宽后视觉间隙 ≈1.5dp（≥1dp）；坐标 0..24 内。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round。
- 自检：minidom 良构（XML_OK，3 paths）；grep 命中 strokeWidth/lineCap/lineJoin/strokeColor 各 3 处；无 fillColor。
- 未验证项：未编译、未真机渲染。
