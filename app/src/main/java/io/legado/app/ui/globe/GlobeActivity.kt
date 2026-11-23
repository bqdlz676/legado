package io.legado.app.ui.globe

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * 落文 · 3D 地球仪独立入口页（自绘 [GlobeView] 全屏挂载）。
 *
 * 页面内容由代码直接装配，不引入布局 XML / ViewBinding；渲染、手势、回放状态
 * 全部委托给 [GlobeView] 与其内部的 GlobeRenderer / GlobeScene。
 * 离开页面时释放 GL 资源，避免上下文与纹理泄漏。
 *
 * 注册项见 AndroidManifest.xml：`io.legado.app.ui.globe.GlobeActivity`。
 */
class GlobeActivity : AppCompatActivity() {

    private var globe: GlobeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = GlobeView(this)
        globe = view
        val container = FrameLayout(this).apply {
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        setContentView(container)
    }

    override fun onDestroy() {
        globe?.release()
        globe = null
        super.onDestroy()
    }
}
