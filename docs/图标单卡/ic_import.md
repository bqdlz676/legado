# ic_import.xml（书架·导入）
- 规格行（切片-24，逐字）：`方框 x=5 y=8→19→19→14。从框底开口向下箭头 x=12 y=14→12→20→三角。框顶一条横线`。
- 方框 `M5,8 L5,19 L19,19 L19,14`；框顶横线 `M5,8 L19,8`；箭杆 `M12,14 L12,20`；三角 `M9,16.5 L12,20 L15,16.5`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；cap/join=round；无 fillColor。
- 坐标核验：x∈5..19、y∈8..20，全部落 0..24。
- 差异（旧→新）：旧件 28dp/24dp、viewport 1280×1024、两路径黑色实心 fillColor 剪影；已整体覆盖为 24dp 描边线框四路径。
- 自检：minidom 良构（XML_OK）；grep 回证 strokeWidth=1.5×4、#8B6914×4、cap/join=round×4、无 fillColor、viewport=24。
- 未验证项：未编译、未真机渲染。
