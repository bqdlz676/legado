# ic_sim_route.xml（行军路线）

- 规格行（切片-24，逐字）：`一条从左下到右上的金色虚线（线+缺口循环）。虚线长度复用 strokeDasharray`
- VectorDrawable 不支持 dash 属性，按硬规范改用分段小线段模拟虚线：沿对角线 (5,19)→(19,5) 均布，每段长 2、间隔 2，共 5 段。
- 单 path 含 5 条子路径；strokeWidth=1.5，strokeColor=#8B6914，strokeLineCap=round。
- 规范：24dp/24 viewport；坐标均在 0..24 内（x/y 5→19），未越界。
- 自检：minidom 良构（XML_OK，1 path）；grep 回证 24dp/24 viewport、strokeWidth=1.5、#8B6914、round 全部命中。
- 未验证项：未编译、未真机渲染；虚线在实机底色上的间距观感与对比度未验收。

修正项：补 strokeLineJoin="round" ×1
