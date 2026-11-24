# ic_globe_atmos 图标单卡（大气开关 · 正圆 + 两道虚线同心弧）
- 文件：`app/src/main/res/drawable/ic_globe_atmos.xml`，24dp/24 viewport，无填充纯描边；strokeWidth=1.5，strokeColor=#8B6914，lineCap=round。
- 主圆：cx=12 cy=12 r=7，`M5,12 a7,7 0 1,0 14,0 a7,7 0 1,0 -14,0`。
- 内层虚线弧：同心 r=9.5，8 段小弧模拟，弧间留角向空隙。
- 外层虚线弧：同心 r=11，8 段小弧模拟，相对内层错 22.5° 排布。
- 虚线说明：vector 原生不支持 dash 分段，故以分段小弧模拟虚线效果；全部绝对坐标在 0..24 区间（1.15~22.85）。
- 自检：minidom 解析良构；strokeWidth/1.5、#8B6914、round 各 3 处 grep 回证命中；fillColor 0 处。
- 未验证：未编译、未真机渲染。
- 修正项：补 strokeLineJoin=round ×3（三条描边 path 均补，strokeColor=3 与 strokeLineJoin=3 grep 数相等；pathData、fill、缩进未动）。
