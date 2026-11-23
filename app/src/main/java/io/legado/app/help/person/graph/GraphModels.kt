package io.legado.app.help.person.graph

/**
 * 人物关系图的图谱模型。
 *
 * 从旧 `help.relation` 包迁出：图谱结构只描述「谁和谁在哪些段落同场」，
 * 档案归属 / 身份 / 生卒一律由 D2 人物目录（`help.person.PersonProfile`）提供，
 * 模型本身不内嵌任何旧扁平表字段。
 */

/** 一次出场：所属章节标题 + 正文截取段。 */
data class Appearance(
    val chapter: String,
    val text: String,
)

/**
 * 图谱节点。
 *
 * @param faction 阵营 / 国别，D2 无载时由国号前缀规则兜底
 * @param appearances 出场段落（抽取层保留，最多 12 条）
 * @param identity 身份，取自 D2 档案
 * @param lifeSpan 生卒格式化串，取自 D2 档案
 * @param aliases 字 / 号 / 封号 / 别称
 */
data class Node(
    val id: String,
    val name: String,
    val faction: String,
    val count: Int,
    val appearances: List<Appearance>,
    val identity: String = "",
    val lifeSpan: String = "",
    val aliases: List<String> = emptyList(),
)

/** 共现边：source/target 为节点 id，weight 为同段次数，chapters 为共现章节。 */
data class Edge(
    val source: String,
    val target: String,
    val weight: Int,
    val label: String,
    val chapters: List<String>,
)

/** 整张关系图：节点 + 边 + 来源书名。 */
data class Graph(
    val nodes: List<Node>,
    val edges: List<Edge>,
    val source: String,
)

/** 同场人物快照：供半屏 Sheet「同场」清单使用。 */
data class CoPresent(
    val name: String,
    val hits: Int,
    val sample: String,
    /** 君臣 / 父子 / 盟友 / 敌国，取两人之间那几字的关系词 */
    val label: String = "",
)
