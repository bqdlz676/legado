# 锚点卡 · 报告字段闸 quotepath 路径识别

路径：docs/锚点/报告字段闸quotepath.md
回源：python3 data_scripts/check-report-fields.py --selftest
回源：python3 data_scripts/check-report-fields.py（聚合模式）
回源：python3 data_scripts/check-report-fields.py docs/修复-报告字段闸quotepath.md（显式路径）

存在类别：机制闸 3.5 报告字段门禁（data_scripts/check-report-fields.py）；缺陷形态＝聚合口径中文未跟踪/修改报告被八进制转义串漏识别。

## 一、对象与改动面

- 只改 `data_scripts/check-report-fields.py` 一个脚本；不改 `app/src` 任何产品代码；不动其它脚本的判据；不做 git add/commit/push。
- 聚合取路径函数 `changed_reports()`：status 命令补 `-c core.quotepath=false` 与 `-uall`。
- 新增路径解析辅助 `_porcelain_path()`：重命名行取 `->` 右侧新路径，剥成对引号。
- 新增分类辅助 `is_anchor_card()`：`docs/锚点/` 下设计卡显式直传时跳过，不按报告四件套判。
- 路径归一：反斜杠转正斜杠、`os.path.normpath` 规范化后再做前缀正则匹配。
- 空集分支：无待检报告时报 `EMPTY`、退出码 2，不再判 PASS（同 ⑲ 锚点卡 / 55 永远找问题口径）。

## 二、修复前后口径对照（机械可判）

1. 修复前：`git status --porcelain` 未放开 quotepath，中文路径输出为 `"docs/\344\276\244..."` 八进制串，前缀正则 `docs/(修复|验收|组装|分析)-.*\.md$` 匹配不到，聚合把中文报告整批漏掉，误报"本轮无报告改动"。
2. 修复后：`-c core.quotepath=false` 让路径按 UTF-8 原文输出；`-uall` 把未跟踪目录折叠展开成文件级条目；`_porcelain_path()` 剥引号、取重命名右侧；归一后再判前缀。
3. 显式传路径：不经过 `changed_reports()` 取路径，行为不变，直接交 `check()` 判 A/B/C/D。

## 三、自测夹具（`--selftest` 六例，临时迷你 git 仓验完自删）

- 夹具一：四件套齐全的修复类报告 → `check()` 返回空列表，PASS 通路。
- 夹具二：锚点卡/结论/反例/锚点全缺 → `check()` 返回四条 FAIL，FAIL 通路。
- 夹具三：迷你 git 仓内中文未跟踪报告 + 英文路径报告 → `changed_reports()` 两份都识别；非报告 docs 与脚本文件不混入。
- 夹具四：显式路径传入合规英文路径报告 → `main()` 绕开聚合直判，PASS。
- 夹具五：空迷你仓 → `changed_reports()` 返回空集；`main()` 报 EMPTY、退出码 2，不判 PASS。
- 夹具六：解析纯函数三例——重命名行 `R  old -> new` 取右侧、成对引号路径剥离、普通状态行原样。

## 四、口径边界

- 只改路径获取与空集表述；A 锚点卡、B 结论标记、C 反例/推演节、D 证据锚点五条字段判据原样保留，一条不放宽。
- `only_backfill()` 纯回执补录豁免、`is_fixture()` 夹具排除口径原样保留。
- 本单只登记仓内其它缺 quotepath 的调用点清单，不在本单白名单内修改；清单见报告「新发现问题」节。
