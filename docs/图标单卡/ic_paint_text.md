# ic_paint_text.xml（画板工具栏 · 文字）

- 规格行（切片-24，逐字）：`细线圆角方框 x=6 y=8→18→18→6 闭合。框内竖排一个极小"文"字（用 path 画字形：点+撇+捺的简笔）。`
- 四段 pathData：①方框 `M8,8 H16 Q18,8 18,10 V16 Q18,18 16,18 H8 Q6,18 6,16 V10 Q6,8 8,8 Z`；②点 `M12,9.4 L12,10.5`；③撇 `M12,11.4 L9.8,16`；④捺 `M12,11.4 L14.2,16`。
- 规范：24dp/24 viewport；strokeWidth=1.5；strokeColor=#8B6914；lineCap/lineJoin=round；全程描边，无填充色属性。
- 字形取舍：规格写明"点+撇+捺"简笔，未含横画，故不画横；点以 round-cap 短竖画呈现，不用填充圆点。
- 自检：minidom 解析良构（XML_OK），path 数=4；grep 回证 strokeWidth=1.5、strokeColor=#8B6914、cap/join=round、android:fillColor 计数=0；坐标范围 6.0..18.0，落 0..24。
- 未验证项：未编译、未真机渲染。
