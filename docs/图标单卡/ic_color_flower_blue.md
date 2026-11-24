# ic_color_flower_blue.xml（花青）

- 规格行（切片-24 画板颜色圆点，逐字）：`实心圆 #3C5C6E r=8。外圈 r=10`。
- 两段 pathData，圆心均 (12,12)：①内圈实心 `M12,4 m-8,0 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0`，fillColor=`#3C5C6E`，r=8；②外圈描边环 `M12,2 m-10,0 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0`，r=10，strokeWidth=`1.5`，strokeColor=`#8B6914`，strokeLineCap=`round`，strokeLineJoin=`round`，无填充。
- 同心空隙：r=8 内圆与 r=10 外环之间留 2dp 间隙，环不贴内圆。
- 坐标安全：外环外扩 = 10 + 1.5/2 = 10.75，x/y 范围 1.25..22.75，全部落 0..24；viewport 24×24，width/height 24dp。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 strokeWidth=1.5 ×1、fillColor=#3C5C6E ×1、strokeColor=#8B6914 ×1、strokeLineCap=round ×1、24dp/viewport24 ×4。
- 未验证项：未编译、未真机渲染、未查选中态暖金辉光（选中态另按规格第 66 行处理，不在本文件）。
