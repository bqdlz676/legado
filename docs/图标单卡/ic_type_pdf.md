# ic_type_pdf · PDF 文件类型标记

- 规格：24dp/24 viewport；strokeWidth 1.5；strokeColor #8B6914；cap/join round；无 fillColor。
- 方框（同 EPUB/TXT）：`M5,4 L19,4 L19,20 L5,20 Z`。
- 框内小圆点：cx=12 cy=14 r=3，描边空心；path `M15,14 A3,3 0 1 0 9,14 A3,3 0 1 0 15,14`。
- 点下方横线：`M8,18.5 L16,18.5`；圆底缘 y=17 之下、框底 y=20 之上。
- 边界：x 5~19、y 4~20；圆点 x 9~15 / y 11~17；横线 y=18.5，全部在 0..24 内。
- 自检：minidom 良构（3 path）；grep 回证 strokeWidth/strokeColor/cap/join 各 3 处在位，fillColor 计数 0。
- 未验证：未编译、未真机/模拟器渲染（按本单禁令不碰编译与其他文件）。
