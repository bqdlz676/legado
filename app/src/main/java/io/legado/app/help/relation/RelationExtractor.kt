package io.legado.app.help.relation

import android.content.Context
import io.legado.app.help.person.graph.CoPresent
import io.legado.app.help.person.graph.PersonGraphExtractor

/**
 * 遗留转发门面（旧 relation 包）。
 *
 * 抽取引擎与图谱模型已迁至 D2 支撑的 `help.person.graph.PersonGraphExtractor`；
 * 本对象只给尚未迁移的旧链路（DetailSheet / PersonChapterEvents）保留
 * `sentencesAbout` / `coPresent` 两个静态入口，内部全部转发到新抽取器，
 * 不再持有旧扁平档案表、旧图谱模型定义。
 *
 * 新链路（RelationActivity）请直接使用 `help.person.graph.PersonGraphExtractor`，
 * 不要再引用本对象。
 */
@Deprecated("新链路请使用 io.legado.app.help.person.graph.PersonGraphExtractor")
object RelationExtractor {

    /** 同场人物快照，字段与新包 [CoPresent] 对齐，供旧链路按原结构读取。 */
    data class CoPresent(
        val name: String,
        val hits: Int,
        val sample: String,
        val label: String = "",
    )

    /** 本段里与他有关的句子（按句切），最多 3 条。 */
    fun sentencesAbout(
        context: Context,
        text: String,
        name: String,
        year: Int?,
    ): List<String> = PersonGraphExtractor.sentencesAbout(context, text, name, year)

    /** 本段同场人物：和他在同一句里出现的人，按次数排。 */
    fun coPresent(
        context: Context,
        text: String,
        name: String,
        year: Int?,
    ): List<CoPresent> = PersonGraphExtractor.coPresent(context, text, name, year)
        .map { CoPresent(it.name, it.hits, it.sample, it.label) }
}
