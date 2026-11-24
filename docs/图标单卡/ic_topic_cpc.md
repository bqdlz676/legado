# ic_topic_cpc（党史）
- 文件：`app/src/main/res/drawable/ic_topic_cpc.xml`
- 规格：切片-24 第 114 行，逐字照做
- 形制：描边五角星（path 复用 ic_bookmark.xml，外接圆 R=10、圆心 (12,12)，左上顶点 8° 断口）+ 星右方旗杆竖线 M19,4 L19,20
- 画布：24dp / viewport 24×24，坐标 0..24
- 描边：五星 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round；旗杆 strokeWidth=2、strokeColor=#C23B2E、cap=round；无 fillColor
- 重色理由：旗杆取朱砂 #C23B2E 而非墨色 #3A332C——党史专题红旗语义，重色竖线作视觉主轴，与金色五星形成暖金/朱砂双暖呼应
- 自检：minidom 良构（XML_OK）；grep 回证 1.5×1、#8B6914×1、线宽 2×1、#C23B2E×1、cap/join round、fillColor×0
- 未验证：未编译、未真机渲染（本单禁编译）
- 修正项：旗杆补 strokeLineJoin="round" ×1
