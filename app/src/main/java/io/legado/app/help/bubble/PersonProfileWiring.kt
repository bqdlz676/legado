package io.legado.app.help.bubble

import io.legado.app.help.person.PersonProfile

/**
 * D2 人物档案 → 气泡 / 人物卡展示接线（观察项 O2 收口）。
 *
 * 本对象只放可在宿主 JVM 单测中跑的纯决策逻辑，不直接依赖 android.Context：
 * - 人名专用读音优先，通用字库兜底由 UI 层以 [generic] supplier 注入；
 * - 在位起讫展示文案在此收口，无载如实标「无载」，不编约数、不留空白态。
 *
 * 冻结门面（[PersonProfile.pinyinOf] / [PersonProfile.reignOf]）签名不变；
 * 本层只做取值优先级与展示格式化，不另造人物数据。
 */
internal object PersonProfileWiring {

    /**
     * 注音解析：人名专用异读门面 [PersonProfile.pinyinOf] 优先；
     * 专用读音未命中（返回 null / 空白）时回落通用字库读音 [generic]；
     * 两者都缺返回 null，由上层按无注音兜底。全程 runCatching，数据缺失 / 字典异常均不抛、不猜读音。
     */
    fun pinyin(name: String, generic: () -> String?): String? {
        val dedicated = runCatching { PersonProfile.pinyinOf(name) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        if (dedicated != null) return dedicated
        return runCatching { generic() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * 在位起讫展示文案：
     * - 起止年都有 → 「在位　起 — 止」；
     * - 其一无载 → 缺位标「?」，不补约数；
     * - 双无载（非君主 / 史料失载）→ 「在位无载」，不留空白态。
     *
     * [fmt] 把公元年（负为公元前）格式化为展示串，由调用方注入，对齐既有年份口径。
     */
    fun reignLabel(start: Int?, end: Int?, fmt: (Int) -> String): String {
        if (start == null && end == null) return "在位无载"
        val s = start?.let(fmt) ?: "?"
        val e = end?.let(fmt) ?: "?"
        return "在位　$s — $e"
    }
}
