# ic_globe_cloud 图标单卡（云层开关 · 两道重叠凸起半弧）
- 文件：`app/src/main/res/drawable/ic_globe_cloud.xml`，24dp/24 viewport，无 fillColor 纯描边。
- 硬规范：strokeWidth=1.5，strokeColor=#8B6914，strokeLineCap=round，strokeLineJoin=round。
- 上弧：x=5→19 y=8 波浪凸起，`M5,8 A7,3 0 0,1 19,8`。
- 下弧：x=7→17 y=14 波浪凸起，与上弧重叠，`M7,14 A5,2.5 0 0,1 17,14`。
- 坐标均在 0..24 区间；无填充、无阴影、无渐变。
- 自检：minidom 解析良构；strokeWidth/1.5、#8B6914、round 各 2 处 grep 回证；fillColor 0 处。
- 未验证：未编译、未真机渲染。
