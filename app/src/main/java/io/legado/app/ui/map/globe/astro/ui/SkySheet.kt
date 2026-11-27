package io.legado.app.ui.map.globe.astro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.entities.StarEntity
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.astro.contract.SkyParams
import io.legado.app.ui.map.globe.astro.data.StarStore

/**
 * 星空星点半屏 Sheet（验收 D4 后半；规格 §一.5、§五.5/7、§十四.4/12）。
 *
 * 〔三名字段（§十四.4）〕
 *  - [SkyStarNames.zhXingguan]：古星官位次名（如「参宿四」「井宿一」），无则不渲染该行；
 *  - [SkyStarNames.zhModern]：现代中文通用星名（如「天狼」「织女一」），无则不渲染该行；
 *  - [SkyStarNames.enIau]：IAU 西名（如「Sirius」），无则不渲染该行。
 *  距离行只在实体带 `distanceLy` 时渲染；空 / 哨兵距离不渲染距离行（§四.10 空字段不渲染对应行）。
 *
 * 〔纯展示口径（§七.8、§十四.12）〕本组件无内部选中态、不持有相机姿态、不改 `SkyFrame`；
 * 关闭前后相机四元数与选中 id 逐位相等由宿主保证。组件只负责把入参渲染成卡片，
 * `onDismiss` 仅把关闭意图回传宿主。
 *
 * 〔字号硬线（§五.5）〕标题 16sp；正文 / 出处一律 14sp，出处用 [GlobeTokens.textTertiary]
 * 弱化而不是缩字号。颜色全部走 [GlobeTokens] token，本文件零裸 hex / 裸 ARGB 字面量。
 *
 * 〔未接宿主〕本组件不直接读 assets / Room：星名由 [SkyNameProvider] 注入，实体由
 * [StarStore.entityById] 取；宿主接线方式见文件末尾预览注释。
 */

/**
 * 一颗星的三名字段 + 出处。字段可空：空字段在 Sheet 上不渲染对应行，不编占位名。
 *
 * @property zhXingguan 古星官位次名（`zh-xingguan`），无可空
 * @property zhModern 现代中文通用星名（`zh-modern`），无可空
 * @property enIau IAU 西名（`en-iau`），无可空
 * @property source 出处篇目 / 名词表，逐条可回源；无可空
 */
data class SkyStarNames(
    val zhXingguan: String? = null,
    val zhModern: String? = null,
    val enIau: String? = null,
    val source: String? = null,
)

/**
 * 星名查询入口（宿主侧实现）：按稳定星表主键取三名字段。
 *
 * 生产实现可由 `star_name` 表 DAO 或内置 `star_names.csv` 解析器适配；DAO / CSV 未就位时
 * 传 null，组件回落到「星名未录入」空态，不崩不空白（§七.6）。
 */
interface SkyNameProvider {
    /** 取三名字段；查不到返回 null。 */
    fun nameOf(starId: Long): SkyStarNames?
}

/**
 * 内置 `assets/astro/star_names.csv` 的纯 JVM 适配实现。
 *
 * 组件本身不碰 `AssetManager`：宿主把 CSV 文本行读入后传入 [CsvSkyNameProvider]，
 * 解析逻辑在普通 JVM 单测里也可直接跑。表头跳过；字段序与 `star_name` 表一致：
 * `starId,name,lang,source,isPrimary`。
 */
class CsvSkyNameProvider(rows: List<String>) : SkyNameProvider {

    private val byId: Map<Long, SkyStarNames> = parse(rows)

    override fun nameOf(starId: Long): SkyStarNames? = byId[starId]

    companion object {
        /** 解析 CSV 文本行为 `starId -> 三名字段`；同一语域多条时取首条，不覆盖。 */
        fun parse(rows: List<String>): Map<Long, SkyStarNames> {
            val merged = HashMap<Long, SkyStarNames>()
            rows.forEachIndexed { index, line ->
                if (index == 0 || line.isBlank()) return@forEachIndexed // 表头 + 空行
                val f = line.split(",")
                if (f.size < 5) return@forEachIndexed
                val starId = f[0].trim().toLongOrNull() ?: return@forEachIndexed
                val name = f[1].trim()
                val lang = f[2].trim()
                val source = f[3].trim().ifEmpty { null }
                val current = merged[starId] ?: SkyStarNames()
                merged[starId] = when (lang) {
                    "zh-xingguan" -> current.copy(zhXingguan = name, source = source ?: current.source)
                    "zh-modern" -> current.copy(zhModern = name, source = source ?: current.source)
                    "en-iau" -> current.copy(enIau = name, source = source ?: current.source)
                    else -> current // 未知语域不吞错数据，保留既有字段
                }
            }
            return merged
        }
    }
}

/**
 * 星点 Sheet 半屏卡片。
 *
 * @param selectedId 选中星稳定主键；null = 未选中，渲染未选中空态
 * @param store 星表门面；null 或 `entityById` 查不到时实体行（星等 / 距离）不渲染，不崩
 * @param nameProvider 三名字段查询入口；null 时只展示实体可给的字段
 * @param onDismiss 关闭意图回调；组件自身不改选中态 / 相机，关闭后状态一致性由宿主保证
 * @param modifier 外层修饰符（半屏位置 / 宽度由宿主布局决定）
 */
@Composable
fun SkySheet(
    selectedId: Long?,
    store: StarStore?,
    nameProvider: SkyNameProvider? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entity: StarEntity? = selectedId?.let { store?.entityById(it) }
    val names: SkyStarNames? = selectedId?.let { nameProvider?.nameOf(it) }

    // 标题优先级：今名 > 星官名 > 西名；三者皆无且实体缺失时回落空态文案。
    val title = names?.zhModern ?: names?.zhXingguan ?: names?.enIau
    val noRecord = selectedId != null && (store == null || entity == null)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(SkyParams.SHEET_RADIUS_DP.dp),
                ambientColor = Color(GlobeTokens.colorGlow),
                spotColor = Color(GlobeTokens.colorGlow),
            )
            .background(
                Color(GlobeTokens.bgOverlay),
                RoundedCornerShape(SkyParams.SHEET_RADIUS_DP.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 标题 16sp / brandGold（§五.5）。
        Box {
            BasicText(
                text = title ?: if (selectedId == null) "未选中星体" else "星名未录入",
                style = TextStyle(
                    color = Color(GlobeTokens.brandGold),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }

        // 三名字段：名字来自星名表，与实体是否入库无关；有则显示，无字段行不渲染（§十四.4）。
        names?.zhXingguan?.let { BodyRow(label = "星官名", value = it) }
        names?.zhModern?.let { BodyRow(label = "今名", value = it) }
        names?.enIau?.let { BodyRow(label = "西名", value = it) }

        // 实体派生行：无实体记录时不渲染；距离空 / 非有限不渲染距离行。
        entity?.magVt?.takeIf { it.isFinite() }?.let {
            BodyRow(label = "星等", value = String.format("%.2f", it))
        }
        entity?.distanceLy?.takeIf { it.isFinite() }?.let {
            BodyRow(label = "距离", value = String.format("%.1f 光年", it))
        }

        // 空态补编号：store 为空 / 无记录且星名表也查不到时，显式给编号，不留空白（§七.6）。
        if (noRecord && names == null) {
            BodyRow(label = "编号", value = selectedId?.toString() ?: "—")
        }

        // 出处 14sp / textTertiary：弱化不缩字号（§五.5）。
        names?.source?.let { source ->
            BasicText(
                text = source,
                style = TextStyle(
                    color = Color(GlobeTokens.textTertiary),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }

        Spacer(Modifier.height(4.dp))

        // 关闭入口：只回传意图，组件不触碰选中态与相机（§十四.12）。
        Row(
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = onDismiss),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "关闭",
                style = TextStyle(
                    color = Color(GlobeTokens.textSecondary),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }
}

/** 单行正文：标签 14sp 辅助色，值 14sp 正文色；字号不低于 14sp（§五.5 硬线）。 */
@Composable
private fun BodyRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = label,
            style = TextStyle(
                color = Color(GlobeTokens.textTertiary),
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        Spacer(Modifier.width(6.dp))
        BasicText(
            text = value,
            style = TextStyle(
                color = Color(GlobeTokens.textPrimary),
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

/**
 * 独立预览：未接宿主时可单独渲染。真实宿主接入一行：
 * ```
 * SkySheet(selectedId = frame.selectedId, store = starStore,
 *          nameProvider = CsvSkyNameProvider(assetLines), onDismiss = onHostDismiss)
 * ```
 * `assetLines` 由宿主读 `assets/astro/star_names.csv` 文本行喂入；DAO 就位后可换成
 * 按 `star_name` 表实现的 [SkyNameProvider]，组件签名不变。
 */
@Preview
@Composable
private fun SkySheetPreview() {
    val previewRows = listOf(
        "starId,name,lang,source,isPrimary",
        "32263,Sirius,en-iau,IAU WGSN（经 HYG v44 汇编）,1",
        "32263,天狼,zh-modern,《天文学名词》（第二版，1998）,1",
    )
    SkySheet(
        selectedId = 32263L,
        store = null,
        nameProvider = CsvSkyNameProvider(previewRows),
        onDismiss = {},
    )
}
