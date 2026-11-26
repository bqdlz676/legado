package io.legado.app.ui.config.b3

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

/**
 * B3 设置页偏好状态桥：把 SharedPreferences 布尔 / 字符串 / 整型包成 Compose 可观察状态。
 *
 * 口径沿用旧 `settings/PrefState.kt`：渲染层不直接持有 prefs 实例；每次
 * [SharedPreferences.OnSharedPreferenceChangeListener] 回调都重读对应键，避免手工
 * upPreferenceSummary 的同步负担。落盘仍走既有 `putPrefBoolean` / `putPrefString` /
 * `putPrefInt` 扩展，数据层键名复用既有 [io.legado.app.constant.PreferKey]，不新增分叉键。
 *
 * 坏值兜底（P9 / 任务书 §八 B2）：布尔读取经 `getPrefBoolean` → `getBooleanCompat`，
 * 类型不符时 try/catch 回落默认值并回写；未知键 / 空串回落缺省；取值转换异常不上抛 UI 层。
 */

/** 监听布尔偏好键；类型不符 / 未知键回落 [def]。 */
@Composable
internal fun rememberB3Boolean(key: String, def: Boolean): Boolean {
    val context = LocalContext.current
    val prefs = context.defaultSharedPreferences
    var value by remember(key) { mutableStateOf(context.getPrefBoolean(key, def)) }
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            if (changed == key) value = sp.getBooleanCompatSafe(key, def)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

/** 监听字符串偏好键；未知键 / 空串 / 类型不符回落 [def]（O-13 坏值不抛重组线程）。 */
@Composable
internal fun rememberB3String(key: String, def: String? = null): String? {
    val context = LocalContext.current
    val prefs = context.defaultSharedPreferences
    var value by remember(key) { mutableStateOf(prefs.getStringCompatSafe(key, def)) }
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            if (changed == key) value = sp.getStringCompatSafe(key, def)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

/** 监听整型偏好键；越界档位由步进盘钳制后落盘，读取缺省 [def]；类型不符回落 [def]（O-13）。 */
@Composable
internal fun rememberB3Int(key: String, def: Int): Int {
    val context = LocalContext.current
    val prefs = context.defaultSharedPreferences
    var value by remember(key) { mutableStateOf(prefs.getIntCompatSafe(key, def)) }
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            if (changed == key) value = sp.getIntCompatSafe(key, def)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

/**
 * 监听器内安全读布尔：反射 / 类型异常时回落 [def]，不把异常抛到重组线程（P9 兜底）。
 * 与 `getBooleanCompat` 同口径：Boolean 直取，String 解析，其余回落缺省。
 */
private fun SharedPreferences.getBooleanCompatSafe(key: String, def: Boolean): Boolean =
    try {
        getBoolean(key, def)
    } catch (_: ClassCastException) {
        fallbackB3Boolean(all[key], def)
    }

/** 监听器内安全读字符串：类型不符回落 [def]，口径复用 [fallbackB3String]（O-13）。 */
private fun SharedPreferences.getStringCompatSafe(key: String, def: String?): String? =
    try {
        getString(key, def)
    } catch (_: ClassCastException) {
        fallbackB3String(all[key], def)
    }

/** 监听器内安全读整型：类型不符回落 [def]，口径复用 [fallbackB3Int]（O-13）。 */
private fun SharedPreferences.getIntCompatSafe(key: String, def: Int): Int =
    try {
        getInt(key, def)
    } catch (_: ClassCastException) {
        fallbackB3Int(all[key], def)
    }
