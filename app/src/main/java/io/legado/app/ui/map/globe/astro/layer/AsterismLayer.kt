package io.legado.app.ui.map.globe.astro.layer

import io.legado.app.ui.map.globe.astro.data.SkyNameIndex

/**
 * 星官成员（§四.12 `asterism_member`）。
 *
 * @property starId 成员星 `star.id`
 * @property memberIndex 成员序名（如「参宿四」「井宿一」）
 * @property linkToNext 成员间是否在星官体系内连线（与西方星座线分属两层）
 */
data class AsterismMember(
    val starId: Long,
    val memberIndex: String,
    val linkToNext: Boolean,
)

/**
 * 一个星官 / 一宿的成员组（三垣二十八宿体系）。
 *
 * @property code 稳定内码（拉丁小写，关联成员表 / 分野表，不随译名改动）
 * @property name 星官名（如「井宿」「紫微垣」）
 * @property parentGroup 归属组（三垣 / 二十八宿位次），可空
 */
data class AsterismGroup(
    val code: String,
    val name: String,
    val parentGroup: String?,
    val members: List<AsterismMember>,
)

/**
 * 星官内容层（纯 JVM 几何持有者，不碰 Canvas）。
 *
 * 〔与星座层分工〕星官线是内容层（1.0px / brandGold 0.55），星座线是参考层
 * （0.8px / textTertiary 0.45）；默认星官（§〇.2），两套可切、互不残留。
 * 数据由数据单 `StarStore` 关系路径喂入；DAO 未就位时回落空表，不崩。
 */
class AsterismLayer {

    private val groups = ArrayList<AsterismGroup>()

    /** 星点 ↔ 星官 code 双向反查索引（判据 3 · N1；装载期随 groups 重建）。 */
    private var nameIndex: SkyNameIndex = SkyNameIndex.empty()

    /** 当前选中星官内码；null = 未选中。 */
    var selectedCode: String? = null
        private set

    /** 装载整组；重复装载先清旧，不留双份成员。 */
    fun loadGroups(groups: List<AsterismGroup>) {
        this.groups.clear()
        this.groups += groups
        // N1 正向链第一跳：由成员表补建 starId → code 反查，点星后可解析所属星官。
        nameIndex = SkyNameIndex.build(
            groups.associate { group -> group.code to group.members.map { it.starId } },
        )
    }

    /** 点星宿 → 天球高亮该宿成员星 + 连线（§十一.1）。 */
    fun select(code: String?) {
        selectedCode = code
    }

    fun selectedGroup(): AsterismGroup? = groups.firstOrNull { it.code == selectedCode }

    fun groupByCode(code: String): AsterismGroup? = groups.firstOrNull { it.code == code }

    fun allGroups(): List<AsterismGroup> = groups

    /** 星表主键 → 所属星官 code；未收录返回 null（N1 反查入口）。 */
    fun codeOfStar(starId: Long): String? = nameIndex.codeOf(starId)

    /** 星官 code → 成员星主键去重列表；未收录返回空列表。 */
    fun membersOfCode(code: String): List<Long> = nameIndex.membersOf(code)
}
