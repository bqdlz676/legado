package io.legado.app.help.bubble

/**
 * 邻接槽位优先级仲裁器（B35 单源）。
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
     * @return 胜出气泡；两侧皆空时返回 null，交链尾 char / Missing 兜底。
     */
    fun chooseArtifactOrWord(artifactHit: BubbleContent?, wordHit: BubbleContent?): BubbleContent? {
        if (artifactHit == null) return wordHit
        if (wordHit == null) return artifactHit

        val artifactLen = artifactHit.token.length
        val wordLen = wordHit.token.length
        return when {
            // 普通词候选更长：名物短词让位，避免短专名套进更长词组时截胡。
            wordLen > artifactLen -> wordHit
            // 名物候选更长：长专名胜出，词表短词让位。
            artifactLen > wordLen -> artifactHit
            // 等长：保持既有链序的领域优先（名物 > 普通词）。
            else -> artifactHit
        }
    }
}
