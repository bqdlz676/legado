# ic_state_notfound · 搜索无结果（半透金点 + 极简"无"字）

- 规格：24dp/24 viewport；strokeWidth 1.5；strokeColor #8B6914；strokeLineCap round；坐标 0..24。
- 金点：cx=12 cy=14 r=4，实心；不透明度 0.55。
- alpha 表达：fillColor `#8CD4A54A`，前导 alpha 0x8C ≈ 140/255 = 0.549；基色暖金 #D4A54A。
- 字形：点上方极简"无"，横 M8,5 L16,5；撇 M9,7 L7,9.5；竖弯钩 M13,7 L13,9.5 Q13,9.8 15,9.8 L16,9.8。
- 边界：x 7~16、y 5~18；点 y 10~18，描边后仍在 0..24 内。
- 自检：minidom 良构；grep 回证 strokeWidth/strokeColor/lineCap/fillColor 在位；未编译、未真机渲染。
