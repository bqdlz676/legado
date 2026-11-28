package io.legado.app.ui.map.globe.host

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.render.FrameSnapshot

/**
 * 底部回放控制台（D45 从 [GlobeScreenHost] 下沉）：战役信息卡 + 横选 chips +
 * 金线时间轴 + 播放 / 暂停 / 重置 / 自转控制行 + 势力图例。
 *
 * 状态全部由宿主提升后经 [host] / [snapshot] 入参传入，本文件不自建同源状态；
 * 闭包回调走门面转发，不直接引用宿主私有 remember。
 */
@Composable
internal fun ReplayConsole(
    host: GlobeHostController,
    snapshot: FrameSnapshot,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        CampaignInfo(host)
        Spacer(Modifier.height(8.dp))
        CampaignChips(host)
        Spacer(Modifier.height(8.dp))
        TimelineRow(host, snapshot)
        Spacer(Modifier.height(8.dp))
        ControlRow(host, snapshot)
        Spacer(Modifier.height(4.dp))
        PowerLegend()
    }
}

/** 战役信息卡：标题 / 年代 / 双方 / 叙述（切片 18 信息卡；无战役时回退不空白）。 */
@Composable
private fun CampaignInfo(host: GlobeHostController) {
    val campaign = host.currentCampaign ?: return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = campaign.title,
                style = TextStyle(
                    color = Color(GlobeTokens.brandGold),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = campaign.era,
                style = TextStyle(
                    color = Color(GlobeTokens.textTertiary),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
        Spacer(Modifier.height(2.dp))
        BasicText(
            text = host.sidesLabel(),
            style = TextStyle(
                color = Color(GlobeTokens.textSecondary),
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        BasicText(
            text = campaign.context,
            style = TextStyle(
                color = Color(GlobeTokens.textTertiary),
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

/** 势力图例：己方暖金 / 敌方石青 / 盟友古铜 / 中立灰褐，颜色全走 token。 */
@Composable
private fun PowerLegend() {
    val items = listOf(
        "己方" to GlobeTokens.brandGold,
        "敌方" to GlobeTokens.stoneBlue,
        "盟友" to GlobeTokens.brandCopper,
        "中立" to GlobeTokens.textTertiary,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        items.forEach { (label, token) ->
            BasicText(
                text = "●",
                style = TextStyle(color = Color(token), fontSize = 10.sp),
            )
            Spacer(Modifier.width(4.dp))
            BasicText(
                text = label,
                style = TextStyle(
                    color = Color(GlobeTokens.textTertiary),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** 战役横选 chips：选中暖金，未选茶褐；超出可横滑。 */
@Composable
private fun CampaignChips(host: GlobeHostController) {
    val currentId = host.currentCampaign?.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        host.campaigns.forEach { campaign ->
            val active = campaign.id == currentId
            BasicText(
                text = campaign.title,
                style = TextStyle(
                    color = Color(if (active) GlobeTokens.brandGold else GlobeTokens.textSecondary),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                ),
                modifier = Modifier
                    .padding(end = 12.dp)
                    .pointerInput(campaign.id) {
                        detectTapGestures { host.selectCampaign(campaign.id) }
                    },
            )
        }
    }
}

/** 金线时间轴：进度朱砂圆点 + 阶段篆章铜点；点按时间轴跳到对应回合。 */
@Composable
private fun TimelineRow(host: GlobeHostController, snapshot: FrameSnapshot) {
    val campaign = host.currentCampaign
    val rounds = campaign?.rounds ?: 0
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "回合 ${host.turn.toInt()}/$rounds",
                style = TextStyle(
                    color = Color(GlobeTokens.brandGold),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.width(12.dp))
            BasicText(
                text = host.phaseLabel().orEmpty(),
                style = TextStyle(
                    color = Color(GlobeTokens.textTertiary),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .pointerInput(rounds) {
                    detectTapGestures { offset ->
                        if (rounds > 0) {
                            val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            host.seekTurn((ratio * rounds).toDouble())
                        }
                    }
                },
        ) {
            drawLine(
                color = Color(GlobeTokens.brandCopper),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1f,
            )
            val progress = snapshot.routeProgress.coerceIn(0.0, 1.0).toFloat()
            drawCircle(
                Color(GlobeTokens.textAccent),
                radius = 4f,
                center = Offset(size.width * progress, size.height / 2),
            )
        }
    }
}

/** 播放 / 暂停 / 重置 / 自动旋转 控制行，外加路线与素材汇总。 */
@Composable
private fun ControlRow(host: GlobeHostController, snapshot: FrameSnapshot) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = if (host.playing) "⏸" else "▶",
            style = TextStyle(
                color = Color(GlobeTokens.brandGold),
                fontSize = 16.sp,
            ),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures {
                    if (host.playing) host.pause() else host.play()
                }
            },
        )
        Spacer(Modifier.width(16.dp))
        BasicText(
            text = "↺",
            style = TextStyle(
                color = Color(GlobeTokens.textSecondary),
                fontSize = 16.sp,
            ),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { host.resetReplay() }
            },
        )
        Spacer(Modifier.width(16.dp))
        BasicText(
            text = if (host.autoRotate) "自转·开" else "自转·关",
            style = TextStyle(
                color = Color(if (host.autoRotate) GlobeTokens.brandGold else GlobeTokens.textTertiary),
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { host.toggleAutoRotate() }
            },
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            text = host.routeSummary(snapshot),
            style = TextStyle(
                color = Color(GlobeTokens.textTertiary),
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}
