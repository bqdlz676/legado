package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 星空天象层 · 恒星实体（表 `star`）。
 *
 * 规格出处：`docs/星空天象层-实施规格-合并定稿.md` §四 第 10 条：
 * 「id、tychoId、raDeg/decDeg（J2000 Double 必填）、magVt、bayer、flamsteed、hip、hd、
 *  colorBv、spectralType、distanceLy、constellation（88 之一必填）。后三者可空是有意设计——
 *  空字段不渲染对应行。」
 *
 * 主键口径：`id` 取内置星表的稳定序号（非自增）。理由：内置层是不可变参考数据，星名表、
 * 星官成员表都按 `starId` 关联；稳定主键保证重新导入/跨安装后关联不漂移。自增主键只在
 * 用户本地新增记录时才有意义，本表不存在该场景。
 *
 * 坐标口径：`raDeg` 赤经 [0,360)、`decDeg` 赤纬 [-90,90]，J2000.0 分点，与
 * `StarProjector` 的赤道坐标输入一致；必填、非空，无位置星不允许入库。
 */
@Entity(
    tableName = "star",
    indices = [
        // 拾取：按赤经/赤纬矩形框选（§十二 点击命中 <5ms），单列索引供区间扫描。
        Index(value = ["raDeg"]),
        Index(value = ["decDeg"]),
        // 星等裁剪：按 magVt 连续过滤亮暗星（§七 第 1 态：星点按 magLimit 连续裁剪）。
        Index(value = ["magVt"]),
        // 按 88 星座归集连线与成员。
        Index(value = ["constellation"])
    ]
)
data class StarEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    /** Tycho-2 编号（如 `TYC 1234-567-1`）；内置层来源无 TYC 号时为 null，不编。 */
    @ColumnInfo(name = "tychoId")
    val tychoId: String? = null,

    /** J2000 赤经，度，[0,360)，必填。 */
    @ColumnInfo(name = "raDeg")
    val raDeg: Double,

    /** J2000 赤纬，度，[-90,90]，必填。 */
    @ColumnInfo(name = "decDeg")
    val decDeg: Double,

    /** VT 星等（V 波段视星等），越小越亮；必填。 */
    @ColumnInfo(name = "magVt")
    val magVt: Double,

    /** 拜耳命名希腊字母（如 `Alp`/`Bet`），无可空。 */
    @ColumnInfo(name = "bayer")
    val bayer: String? = null,

    /** 弗兰斯蒂德编号（如 `21`），无可空。 */
    @ColumnInfo(name = "flamsteed")
    val flamsteed: String? = null,

    /** Hipparcos 星表编号，无可空。 */
    @ColumnInfo(name = "hip")
    val hip: Long? = null,

    /** HD 星表编号，无可空。 */
    @ColumnInfo(name = "hd")
    val hd: Long? = null,

    /** B-V 色指数；缺色不猜色温，按星等映射暖白段（§四 第 6 条）。 */
    @ColumnInfo(name = "colorBv")
    val colorBv: Double? = null,

    /** MK 光谱型（如 `G2V`），无可空。 */
    @ColumnInfo(name = "spectralType")
    val spectralType: String? = null,

    /** 距离（光年）；仅精选子集注录，其余显示「未收录」，不编（§四 第 5 条）。 */
    @ColumnInfo(name = "distanceLy")
    val distanceLy: Double? = null,

    /** 所属 88 星座三字母 IAU 缩写（如 `Ori`），必填，取值集合与内置表一致。 */
    @ColumnInfo(name = "constellation")
    val constellation: String
)

/**
 * 星名实体（表 `star_name`）。
 *
 * 规格出处：§四 第 11 条：「starId、name、lang（zh-xingguan / zh-modern / en-iau）、
 *  source、isPrimary。」
 * 一条星可有多语/多源名字；`isPrimary` 标记 Sheet 首行展示名。
 */
@Entity(
    tableName = "star_name",
    foreignKeys = [ForeignKey(
        entity = StarEntity::class,
        parentColumns = ["id"],
        childColumns = ["starId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["starId"]),
        Index(value = ["lang"])
    ]
)
data class StarNameEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 关联恒星 `star.id`。 */
    @ColumnInfo(name = "starId")
    val starId: Long,

    /** 星名原文（古名/现代中文名/IAU 西名）。 */
    @ColumnInfo(name = "name")
    val name: String,

    /** 语域：`zh-xingguan` 古星官名 / `zh-modern` 现代中文名 / `en-iau` IAU 西名。 */
    @ColumnInfo(name = "lang")
    val lang: String,

    /** 出处（古籍篇目或 WGSN 来源），逐条可回源；无可空。 */
    @ColumnInfo(name = "source")
    val source: String? = null,

    /** 是否为首选展示名。 */
    @ColumnInfo(name = "isPrimary")
    val isPrimary: Boolean = false
)
