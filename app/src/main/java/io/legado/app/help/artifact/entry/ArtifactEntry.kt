package io.legado.app.help.artifact.entry

/**
 * D16 名物条目（注册表路由命中后的最小数据载体）。
 *
 * 为什么字段收敛在「气泡壳 + 详情挂点」两层：09:73 最小共同气泡字段——气泡壳只放名称 +
 * 一句话，完整释义进半屏 Sheet；本类的 [body] 供气泡壳，[detail] 供后续详情 Sheet，
 * 十七类共用同一结构，不为某一类另扩字段。
 *
 * 纯 JVM 数据类：不 import Android / Compose 类型，普通 JVM 单测可直接构造断言。
 *
 * @param id                  条目主键（注册表内稳定 id，供 [io.legado.app.help.bubble.contract.Action.OpenArtifact] 回链）。
 * @param name                正名 / 本名；别名放 [aliases]，不塞进主名。
 * @param type                schema type 取值（见 [io.legado.app.help.artifact.ArtifactSchema] 各表 types）。
 * @param headline            一行摘要（气泡 headline）；空白由装配层回退到 [name]，不猜释义。
 * @param body                气泡内一句话 / 两到三行解释。
 * @param detail              进半屏 Sheet 的完整内容；本片种子只落摘要口径，全量详文归后续数据片。
 * @param aliases             别名 / 别称 / 异称列表；查询时与主名同权匹配。
 * @param source              出处标注（古籍篇目 / 库名 / 条目号），未收录条目不写来路（09:75）。
 * @param relatedPlaceId      两级跳转挂点：条目关联的地名主键（归 D3 place 表）；
 *                            非空时详情链可再走 OpenMap 定位，本片只挂数据点，不接 UI；
 *                            无关联地名时为 null，不造占位地名。
 * @param relatedArtifactIds  相关名物主键列表（人物→持有物、地名→所在建筑等，09:64 入口 4）；
 *                            供后续「相关名物 → 详情 Sheet」一级跳转展开，本片只留挂点。
 * @param longitude           经度；山川坐标归 D16 landscape 域，城邑坐标归 D3 place 域（09:37）。
 * @param latitude            纬度；不可考为 null，不猜值。
 */
data class ArtifactEntry(
    val id: String,
    val name: String,
    val type: String,
    val headline: String,
    val body: String,
    val detail: String,
    val aliases: List<String>,
    val source: String,
    val relatedPlaceId: String?,
    val relatedArtifactIds: List<String>,
    val longitude: Double?,
    val latitude: Double?,
)
