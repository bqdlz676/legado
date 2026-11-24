# ic_type_epub · EPUB（细线方框 + 框内小字 e）

- 规格：24dp/24 viewport；strokeWidth 1.5；strokeColor #8B6914；cap/join round；无填充。
- 方框：M5,4 L19,4 L19,20 L5,20 Z，闭合描边。
- e 碗形弧：M14.0,12.4 A2.1,2.6 0 1 0 13.9,12.5，圆心约 (11.9,12.4)。
- e 横杠：M9.9,12.2 L13.9,12.2。
- 坐标：x 5~19、y 4~20，e 居中 (12,12)，描边后仍在 0..24 内。
- 自检：minidom 良构；grep 回证四描边属性各 3 处在位、无 fillColor、viewport 24×24。
- 未验证：未编译、未真机渲染；e 字形弧度未做视觉比对。未触碰其他图标文件。
