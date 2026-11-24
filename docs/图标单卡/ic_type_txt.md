# ic_type_txt · TXT 文件类型（方框 + 三条横线）

- 规格：24dp/24 viewport；strokeWidth 1.5；strokeColor #8B6914；cap/join round；无 fillColor。
- 外框：细线方框 (5,4)→(19,4)→(19,20)→(5,20) 闭合，pathData `M5,4 L19,4 L19,20 L5,20 Z`。
- 框内横线 1：`M8,9 L16,9`；横线 2：`M8,13 L16,13`；横线 3：`M8,17 L16,17`。
- 边界：x 5~19、y 4~20，加 0.75 描边后仍在 0..24 内。
- 自检：minidom 良构；grep 回证四描边属性各 4 处在位；fillColor 计数 0；坐标 4.0~20.0。
- 未验证：未编译、未真机渲染；未做 PNG 导出（按规格由系统渲染）。
