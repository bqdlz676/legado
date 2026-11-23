package io.legado.app.ui.map.globe.host

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.ui.map.globe.offline.TextureKind
import io.legado.app.utils.NetworkUtils

/**
 * 地球仪 Android 宿主（地图页入口接线目标）。
 *
 * 职责：持有纯 JVM [GlobeHostController]，把 Compose 自绘壳 [GlobeScreenHost] 挂上；
 * 网络状态经 [NetworkUtils] 转发门面离线兜底，内置种子素材（星空 / 地名 atlas / 战役
 * 路线）视为本机预缓存，未联网时门面自动切程序化 / 种子兜底，不空白、不崩溃。
 *
 * 本类不实现 GLES 底层渲染：球体三维网格与纹理 pass 留后续渲染层切片；当前壳走
 * Compose Canvas 正交投影的程序化舆图，门面几何 / 回放 / 离线逻辑全部真实调用。
 */
class GlobeActivity : AppCompatActivity() {

    private lateinit var host: GlobeHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = GlobeHostController()

        // 网络观察层写入：在线 / 离线决定门面走真实纹理还是缓存 / 程序化兜底。
        host.setOnline(NetworkUtils.isAvailable())
        // 内置种子素材随包就位，按预缓存登记；卫星 / 云层等远程纹理未命中时走兜底。
        host.markAssetCached(TextureKind.STAR_SKY)
        host.markAssetCached(TextureKind.LABEL_ATLAS)
        host.markAssetCached(TextureKind.CAMPAIGN_ROUTES)

        setContent {
            GlobeScreenHost(host = host, onBack = { finish() })
        }
    }

    override fun onResume() {
        super.onResume()
        // 回前台时刷新一次网络档位，覆盖中途断网 / 复网的情况。
        host.setOnline(NetworkUtils.isAvailable())
    }
}
