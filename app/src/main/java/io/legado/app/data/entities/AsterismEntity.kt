package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 星官实体（表 `asterism`）。
 *
 * 规格出处：§四 第 12 条：「asterism（星官与星座分表，理由：成员判定与样式完全不同，
 *  合并会造"一套数据两个语义"）」。本表只收中国传统星官/二十八宿；88 西方星座的连线
 *  见 [ConstellationLineEntity]，两套体系不混表。
 */
@Entity(
    tableName = "asterism",
    indices = [
        Index(value = ["code"], unique = true)
    ]
)
data class AsterismEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 稳定内码（拉丁小写，供成员表/分野表按码关联，不随译名改动）。 */
    @ColumnInfo(name = "code")
    val code: String,

    /** 星官名（如「井宿」「鬼宿」）。 */
    @ColumnInfo(name = "name")
    val name: String,

    /** 归属组（三垣 / 二十八宿中的位次，如「南方朱鸟七宿」），无可空。 */
    @ColumnInfo(name = "parentGroup")
    val parentGroup: String? = null,

    /** 出处（《史记·天官书》《晋书·天文志》《步天歌》篇目），逐条可回源。 */
    @ColumnInfo(name = "source")
    val source: String? = null
)

/**
 * 星官成员实体（表 `asterism_member`）。
 *
 * 规格出处：§四 第 12 条：「asterism_member（memberIndex 如"井宿一"、linkToNext）」。
 * `linkToNext` 标记成员间是否需要在星官体系内连线（与西方星座线分属两层）。
 */
@Entity(
    tableName = "asterism_member",
    foreignKeys = [
        ForeignKey(
            entity = AsterismEntity::class,
            parentColumns = ["id"],
            childColumns = ["asterismId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StarEntity::class,
            parentColumns = ["id"],
            childColumns = ["starId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["asterismId"]),
        Index(value = ["starId"])
    ]
)
data class AsterismMemberEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 所属星官 `asterism.id`。 */
    @ColumnInfo(name = "asterismId")
    val asterismId: Long,

    /** 成员星 `star.id`。 */
    @ColumnInfo(name = "starId")
    val starId: Long,

    /** 位次名（如「井宿一」）；按序列位次给名，无可空。 */
    @ColumnInfo(name = "memberIndex")
    val memberIndex: String? = null,

    /** 本成员与下一成员是否在星官层连线。 */
    @ColumnInfo(name = "linkToNext")
    val linkToNext: Boolean = false
)

/**
 * 西方星座连线实体（表 `constellation_line`）。
 *
 * 规格出处：§四 第 12 条：「constellation_line（fromStarId、toStarId、constellationId、style）」。
 * 连线自绘，署名「参考 IAU/Sky & Telescope」（§四 第 8 条）；`style` 预留线样式内码，
 * 当前内置层统一走星座线口径（0.8px / alpha 0.45，见 `SkyParams`）。
 */
@Entity(
    tableName = "constellation_line",
    foreignKeys = [
        ForeignKey(
            entity = StarEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromStarId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StarEntity::class,
            parentColumns = ["id"],
            childColumns = ["toStarId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["constellationId"]),
        Index(value = ["fromStarId"]),
        Index(value = ["toStarId"])
    ]
)
data class ConstellationLineEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 线端恒星 `star.id`。 */
    @ColumnInfo(name = "fromStarId")
    val fromStarId: Long,

    /** 线端恒星 `star.id`。 */
    @ColumnInfo(name = "toStarId")
    val toStarId: Long,

    /** 所属 88 星座三字母 IAU 缩写。 */
    @ColumnInfo(name = "constellationId")
    val constellationId: String,

    /** 线样式内码（预留；默认 `constellation`）。 */
    @ColumnInfo(name = "style")
    val style: String = "constellation"
)
