package io.legado.app.help.bubble

import io.legado.app.help.bubble.contract.MISSING_SOURCE

/**
 * 邻接槽位优先级仲裁器（B35 单源；B36 补名物候选来源门禁）。
 *
 * 背景：解析链上名物槽 [artifact] 邻接多字词槽 [word]；原 first-hit-wins 链序下 artifact
 * 前置位次可能截胡本该由 word 槽命中的普通多字词（例如「白马」既是名马也是普通词），造成
 * 读页气泡选错类。本器是这两个邻接槽候选取舍的**唯一裁决点**：Resolver 的段落链（dispatch）
 * 与整词链（queryToken）都只把两侧候选交回本器；Provider 内部不写第二份长度 / 类别判断。
 *
 * 规则（对齐规格「同位置多命中取更高优先级；同优先级取更长词」，见 [BubbleResolverImpl] 类注释）：
 *  - 任一侧为 null：null 不参与裁决，直接取另一侧；两侧皆 null 返回 null。
 *  - 两侧皆命中：候选 token 更长者胜（长词优先，规格冻结的次级排序键）。
 *  - 等长：保持既有领域优先，名物槽（artifact）先于多字词槽（word）。
 *
 * 来源门禁（验收 N2）：名物候选进长度比较前，先过 [isGenuineHit] 校验——B25 五态兜底壳
 * （source=[MISSING_SOURCE]）与无出处空来源件视同未命中，按 null 下钻，不允许壳冒充命中
 * 压制词槽。该校验单源在本器；适配层 [ArtifactBubbleProvider] 另有同款早退过滤，属双保险，
 * 不在 Provider 侧复制第二份裁决规则。
 *
 * 为什么长度而不是类别字典：当前气泡协议 [BubbleContent] 未带置信度 / tier 字段，证据强度
 * 裁决无数据可依；长度是两槽窗口口径内唯一可纯函数比较的信号。等名物候选 tier / 消歧置信
 * 接入后，裁决规则只在本器内部升级，Resolver 与 Provider 调用方不动。
 *
 * 纯 JVM：不 import Android / Compose 类型；只做候选比较，不查字典、不碰 Context。
 */
internal object SlotArbitrator {

    /**
     * 在名物槽候选 [artifactHit] 与多字词槽候选 [wordHit] 之间裁决最终出泡者。
     *
     * 名物候选若未过 [isGenuineHit] 来源门禁，按 null 下钻，交词槽或链尾兜底；兜底壳不会进入
     * 两侧长度比较，也就不会凭等长 / 更长位次压制 word 候选。
     *
     * @return 胜出气泡；两侧皆空（含名物壳被门禁剔除）时返回 null，交链尾 char / Missing 兜底。
     */
    fun chooseArtifactOrWord(artifactHit: BubbleContent?, wordHit: BubbleContent?): BubbleContent? {
        val artifact = if (isGenuineHit(artifactHit)) artifactHit else null
        if (artifact == null) return wordHit
        if (wordHit == null) return artifact

        val artifactLen = artifact.token.length
        val wordLen = wordHit.token.length
        return when {
            // 普通词候选更长：名物短词让位，避免短专名套进更长词组时截胡。
            wordLen > artifactLen -> wordHit
            // 名物候选更长：长专名胜出，词表短词让位。
            artifactLen > wordLen -> artifact
            // 等长：保持既有链序的领域优先（名物 > 普通词）。
            else -> artifact
        }
    }

    /**
     * 名物候选是否为可参与仲裁的真实命中。
     *
     * 判定依据只取候选项既有字段（[BubbleContent.source]，字段定义见 [BubbleContent] KDoc）：
     *  - null：未命中，不参与；
     *  - [MISSING_SOURCE]（「未命中」）：B25 五态兜底壳（空数据 / 离线缓存未命中 / 缺年份 /
     *    同名消歧失败 / 超时），壳结构完整但不是真实条目，不得进长度比较；
     *  - 空 / 纯空白来源：协议要求每条数据带来源与版本，无出处件按残缺件下钻，不进仲裁。
     *
     * 为什么不靠 [BubbleContent.kind]：名物十七类在既有链统一映射为冻结八值之一（多数回退
     * [BubbleKind.WORD]），兜底壳类别同样回退到这八值，kind 无法区分壳与真实命中；source 是
     * 壳与真命中之间唯一可纯函数区分的信号。
     */
    private fun isGenuineHit(content: BubbleContent?): Boolean {
        if (content == null) return false
        // isNotBlank 走空白字符扫描、不产生 trim 副本；门禁每槽位只跑一次，常数级无分配热点。
        val source = content.source
        return source.isNotBlank() && source != MISSING_SOURCE
    }
}
