package io.legado.app.ui.map.globe.astro.layer

/**
 * 分野条目（§四.13 `field_allocation`）：星宿 ↔ 周代州 / 国近似对应。
 *
 * @property asterismCode 关联星官内码
 * @property regionName 州域名（如「雍州」）
 * @property stateName 国名（如「秦」），可空——部分分野只系州不系国
 * @property source 出处篇目原文；异说条目在此注明「一作」来源
 * @property isVariant 是否异说：true 为「一作」版本，主表取十二州，异说只作小字脚注（§十一.5）
 */
data class FieldEntry(
    val asterismCode: String,
    val regionName: String,
    val stateName: String?,
    val source: String?,
    val isVariant: Boolean,
)

/**
 * 分野联动层（纯 JVM 状态持有者，不碰 Canvas）。
 *
 * 〔双向闭环（§十一）〕点星宿 → 本层记 `selectedRegion`，天球高亮该宿成员；
 * 地球侧「对应州域 colorGlow 脉冲 1200ms + 州名竖排」需要地球渲染层提供高亮入口。
 *
 * 〔跨模块缺口（本单不硬改地球渲染）〕当前地球渲染层尚无州域多边形高亮 / 脉冲入口；
 * 本层只维护天球一侧的选中与脉冲计时，地球侧联动缺口列入验收报告「跨模块交接项」，
 * 由调度方转给下一棒；本单不越界改地球渲染代码。
 */
class FieldAllocationLayer {

    private val entries = ArrayList<FieldEntry>()

    /** 星官内码 → 成员星主键（内置锚点成员；示意轮廓几何来源，§十一.4）。 */
    private val memberIds = HashMap<String, List<Long>>()

    /** 当前选中州域名；null = 未选中。 */
    var selectedRegion: String? = null
        private set

    /** 分野脉冲起算时刻 ms（§十六.10 周期 1200ms，与标记脉冲同一语言）。 */
    var pulseStartMs: Long = 0L
        private set

    fun loadEntries(entries: List<FieldEntry>) {
        this.entries.clear()
        this.entries += entries
    }

    /** 装载星官成员星索引；重复装载先清旧，不留双份。 */
    fun loadMembers(members: Map<String, List<Long>>) {
        memberIds.clear()
        memberIds += members
    }

    /** 点星宿 / 点州域：记选中州域并起算脉冲；null 清除选中。 */
    fun selectRegion(regionName: String?, nowMs: Long) {
        selectedRegion = regionName
        pulseStartMs = if (regionName == null) 0L else nowMs
    }

    fun entriesForRegion(region: String): List<FieldEntry> =
        entries.filter { it.regionName == region }

    /** 星官 → 对应州域；异说条目不参与主映射（§十一.5 不合并、不仲裁）。 */
    fun regionForAsterism(code: String): String? =
        entries.firstOrNull { !it.isVariant && it.asterismCode == code }?.regionName

    /** 州域 → 关联星官内码列表（反向点州域 → 天球转向用，§十一.2）。 */
    fun asterismCodesForRegion(region: String): List<String> =
        entries.filter { it.regionName == region }.map { it.asterismCode }

    /** 州域 → 成员星主键列表（分野边示意凸包 / 地球侧脉冲锚点用；无成员回落空列表）。 */
    fun memberIdsForRegion(region: String): List<Long> =
        asterismCodesForRegion(region).flatMap { memberIds[it].orEmpty() }

    fun allEntries(): List<FieldEntry> = entries
}
