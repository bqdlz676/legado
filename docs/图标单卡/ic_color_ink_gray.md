# ic_color_ink_gray.xml（画板颜色圆点 · 墨灰）

- 规格行（切片-24 画板颜色圆点，逐字）：`实心圆 #5C4F3A r=8。外圈 r=10`。
- 内圈：fillColor `#5C4F3A`，正圆 cx=12 cy=12 r=8：`M12,12 m-8,0 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0`。
- 外圈：strokeColor `#8B6914`，r=10，strokeWidth=1.5，strokeLineCap/Join=round：`M12,12 m-10,0 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0`。
- 规范：24dp/24 viewport；坐标 0..24。
- 坐标核验：内圈包围盒 4~20；外圈 2~22，描边外扩 0.75 后 1.25~22.75，仍落 0..24。
- 自检：minidom 解析良构（XML_OK，24dp/24/24/24），path 数=2；grep 回证 fillColor=#5C4F3A、strokeWidth=1.5、strokeColor=#8B6914、strokeLineCap=round。
- 未验证项：未编译、未真机渲染；未改动其他文件。
