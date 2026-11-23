package io.legado.app.ui.config.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString

/**
 * 设置页偏好状态桥：把 SharedPreferences 布尔 / 字符串 / 整型包成 Compose 可观察状态。
 *
 * 渲染层不直接持有 prefs 实例；每次 [SharedPreferences.OnSharedPreferenceChangeListener]
 * 回调都重读对应键，避免旧 PreferenceFragment 里手工 upPreferenceSummary 的同步负担。
 * 落盘仍走 [io.legado.app.utils.putPrefBoolean] 等既有扩展，数据层口径不变。
 */
@Composable
fun rememberPrefBoolean(key: String, def: Boolean): Boolean {
    val context = LocalContext.current
    val prefs = context.defaultSharedPreferences
    var value by remember(key) { mutableStateOf(context.getPrefBoolean(key, def)) }
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            if (changed == key) value = sp.getBoolean(key, def)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

@Composable
fun rememberPrefString(key: String, def: String? = null): String? {
    val context = LocalContext.current
    val prefs = context.defaultSharedPreferences
    var value by remember(key) { mutableStateOf(context.getPrefString(key, def)) }
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            if (changed == key) value = sp.getString(key, def)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

@Composable
fun rememberPrefInt(key: String, def: Int): Int {
    val context = LocalContext.current
    val prefs = context.defaultSharedPreferences
    var value by remember(key) { mutableStateOf(context.getPrefInt(key, def)) }
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            if (changed == key) value = sp.getInt(key, def)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

/** 读取上下文默认 prefs 的便捷句柄（供非 Composable 动作回调使用）。 */
fun Context.prefs() = defaultSharedPreferences
