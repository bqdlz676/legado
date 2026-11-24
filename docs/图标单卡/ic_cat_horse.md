# ic_cat_horse.xml（名马·马头侧影）

- 规格行（切片-24，逐字）：`极简马头侧影：长弧从 x=6 y=10 到 x=14 y=8 到 x=16 y=14（鼻梁）+ 短弧从 x=8 y=12 到 x=10 y=8（耳朵）`。
- pathData 鼻梁：`M6,10 Q10,9 14,8 Q15,11 16,14`——起(6,10)，经(14,8)，收(16,14)，两段贝塞尔。
- pathData 耳朵：`M8,12 Q9,10 10,8`——起(8,12)，收(10,8)。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；无 fillColor（描边）。
- 坐标核验：x/y ∈ 6..15，全部落 0..24。
- 自检：minidom 解析良构（XML_OK），path 数=2；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round、fillColor=0。
- 未验证项：未编译、未真机渲染。
