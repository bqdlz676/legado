package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分野实体（表 `field_allocation`）。
 *
 * 规格出处：§四 第 13 条：「field_allocation：asterismId、regionName（雍州）、
 *  stateName（秦，可空）、source、isVariant（异说 true + "一作"来源）。」
 * 分野把星官映射到地上州域（《史记·天官书》卷二十七）；点井宿→雍州脉冲、点雍州→井鬼
 * 高亮的双向闭环即按本表关联（§十四 第 3 条）。
 */
@Entity(
    tableName = "field_allocation",
    foreignKeys = [ForeignKey(
        entity = AsterismEntity::class,
        parentColumns = ["id"],
        childColumns = ["asterismId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["asterismId"]),
        Index(value = ["regionName"])
    ]
)
data class FieldAllocationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 关联星官 `asterism.id`。 */
    @ColumnInfo(name = "asterismId")
    val asterismId: Long,

    /** 州域名（如「雍州」）。 */
    @ColumnInfo(name = "regionName")
    val regionName: String,

    /** 国名（如「秦」），无可空——部分分野只系州不系国。 */
    @ColumnInfo(name = "stateName")
    val stateName: String? = null,

    /** 出处（篇目原文），异说条目在此注明「一作」来源。 */
    @ColumnInfo(name = "source")
    val source: String? = null,

    /** 是否异说：true 表示此条为「一作」版本，source 内记并列来源。 */
    @ColumnInfo(name = "isVariant")
    val isVariant: Boolean = false
)
