# ic_paint_eraser.xml（橡皮 · 画板工具栏）

- 规格行（切片-24 画板工具栏，逐字）：`细线小方块 x=6 y=14→14→14→6 闭合，方块倾斜约 15°。方块内三个极小横排小点 cx=9/11/13 cy=14 r=0.6。`
- 原文描述 → pathData：①方块 x=6→14、y=10→18（三点 cy=14 居中）→ `M6,10 H14 V18 H6 Z`；②三小点 → `M9,14` / `M11,14` / `M13,14` 各 r=0.6 描边圆。
- 旋转：`<group android:rotation="-15" android:pivotX="10" android:pivotY="14">` 整体包裹方块与三点；本地坐标保留规格数值，不手算三角函数。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor。
- 包围盒估算：方块半宽 4，转 15° 后半展 4·(|cos15|+|sin15|)=4.899；绕 (10,14) 得 x 5.10..14.90、y 9.10..18.90，加半线宽 0.75 后 x 4.35..15.65、y 8.35..19.65，未越 24。
- 自检：minidom 解析良构（XML_OK），path 数=4；grep 回证 strokeWidth=1.5、#8B6914、lineCap/lineJoin=round 各 4 处；fillColor=0；rotation=-15 已写入。
- 未验证项：未编译、未真机渲染。
