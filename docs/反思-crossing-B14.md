# 反思 · crossing B14（过渡 spring / 自动推进节拍）

1. **做了什么**：在 `shell/CrossingShell.kt` 把固定 `delay(600ms)` 换成事件档位 + 长局压缩的节拍纯函数；弹簧参数收口到 `SpringRole` 角色工厂；回合切换加 `Crossfade` 透明度过渡；新增 `BeatAndSpringTest.kt` 10 条纯函数单测。基线 `4ff8abc`（任务书写 d9d37ff，实际不一致）。

2. **踩的坑 / 教训**
   - 开工先 grep 全仓旧符号引用再删：`compareSpring` 只在本文件 3 处内部使用，删改名才安全；若有测试/他文件引用，删除会断编译。
   - 节拍函数初版带了 `duration` 形参但函数体不用，属于死参数（与 J5 同源问题）；写完自查发现后移除，调用点同步改。
   - 规格切片 `落文-原文-06` 内并没有 §1.5-C 字样，spring 口径实际散在切片-03/04/15 与 `ComponentPalette` 常量里；动手前以仓库内既有弹簧常量（BUBBLE/REVEAL/SHEET）为冻结口径，不另造数值。

3. **我可能判断错的地方**
   - 三档节拍时长（620/540/460/320）是按信息密度估的，未经真机手感验证；Compsoe Crossfade + spring 在多棋子场景的帧率未测。
   - 关键词分类依赖中文日志措辞，若引擎日志改写（如「交战」替代「遭遇」），档位会回落 QUIET。
   - `SpringSpec.dampingRatio/stiffness` 属性直取未在本地编译验证，按 Compose 公开 API 书写。

4. **遗留未决项**
   - 未编译、未跑单测（沙箱禁令）；存档恢复动画复位、双控制器装配挂 D24。
   - 长局压缩斜率 0.012 为经验值，没有用户可调速度接口。

5. **给下任的一句话提醒**：复核前先回仓库确认 `beatDelayMs` 公式与关键词表是否仍匹配当前 `CampaignController` 日志措辞；弹簧数值只许从 `ComponentPalette` 取，不要在 Composable 里新写阻尼/刚度。
