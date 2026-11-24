# ic_cat_animal.xml（鸟兽·飞鸟）

- 规格行（切片-24，逐字）：`一个极简飞鸟剪影：M6,12 Q12,6 18,12。翅膀向上的弧线`。
- pathData 单段：`M6,12 Q12,6 18,12`——起(6,12)，贝塞尔控制点(12,6)，收(18,12)，弧线上扬。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor（描边，非实心剪影，依硬规范）。
- 坐标核验：x∈6..18、y∈6..12，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=1；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round、无 fillColor。
- 未验证项：未编译、未真机渲染。
