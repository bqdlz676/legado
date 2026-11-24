# ic_topic_battle（战役）
- 文件：`app/src/main/res/drawable/ic_topic_battle.xml`
- 规格：切片-24 第 115 行，逐字照做
- 形制：左斜线 M6,18 L18,6；右斜线 M18,18 L6,6；交叉点 (12,12) 上方一颗描边小五星，中心 (12,5)，外径 2.2 / 内径 0.9
- 画布：24dp / viewport 24×24，坐标 0..24（星 x∈9.91..14.09、y∈2.80..6.78）
- 描边：双剑 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round；小五星 strokeWidth=1；无 fillColor
- 星线宽理由：五星为交叉点上方次级点缀，外径仅 2.2；若用 1.5 会压细五条星臂、轮廓发闷，故降到 1.0 保持尖角清晰，颜色/圆角与双剑一致
- 自检：minidom 良构（XML_OK）；grep 回证 24dp×2、viewport24×1、1.5×2、星 1×1、颜色×3、cap/join round×3、fillColor×0
- 未验证：未编译、未真机渲染（本单禁编译）
- 修正项：strokeWidth 1→1.5 ×1
