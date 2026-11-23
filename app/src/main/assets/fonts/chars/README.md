# 子集裁字字表

`luowen-core.txt`：落文首屏字体子集字表，一行纯文本、非空白字符去重收集。

## 字表口径

- 组成：内置读物（`assets/books/*.txt`）词频 top550 汉字 + 灵魂闭环 / 纪年 /
  气泡 / 启动落款 / 搜索页必覆盖字 + ASCII 可打印字符与常用中文标点。
- 规模：653 个可用字符（583 个 CJK 汉字 + 拉丁 / 数字 / 标点）。
- 规格「正文常用 2500 字」为 web woff2 口径；Android 内嵌 OTF 受 200KB 红线
  约束，首屏只收核心高频字，生僻字不进子集，运行时回落系统全量 CJK 字。
- 字表与字体资产同口径登记出处，不引入 CC-CEDICT / OSM / Wikipedia 派生数据。

## 刷新

```
python3 ../subset_generate.py --refresh-chars --books-dir ../../../books --top-n 550
```

`--top-n` 调大后必须确认对应子集仍在体积红线内（正文 / 标题 ≤ 200KB，
数字 ≤ 20KB）；超红线脚本会拒绝打包。
