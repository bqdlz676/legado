package io.legado.app.ui.components

import io.legado.app.ui.components.contract.ComponentVisualState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B17 离线空态重试入口分闸验收（纯 JVM，只用 org.junit + java.*，不依赖 Android / Compose）。
 *
 * 分闸口径：
 *  - 容器本体：契约 OFFLINE [ComponentVisualState.eventsAllowed] = false 保持不变，
 *    离线空白区域点击仍不触发；
 *  - 重试入口：standaloneInteractive = true 的独立操作入口，在未禁用、未加载时独立放行自身点击；
 *  - 禁用 / 加载是硬闸，独立入口不凌驾其上；EMPTY 态语义不变（契约仍放行）。
 */
class InkEmptyStateRetryGateTest {

    @Test
    fun `离线重试入口 独立放行点击`() {
        val allowed = resolveEventsAllowed(
            visual = ComponentVisualState.OFFLINE,
            standaloneInteractive = true,
            enabled = true,
            loading = false,
        )

        assertTrue("离线空态「重试」作为恢复入口必须可点", allowed)
    }

    @Test
    fun `离线容器本体 空白区域 仍不触发点击`() {
        val allowed = resolveEventsAllowed(
            visual = ComponentVisualState.OFFLINE,
            standaloneInteractive = false,
            enabled = true,
            loading = false,
        )

        assertFalse("OFFLINE 容器本体仍按契约禁点，不得被连带放行", allowed)
    }

    @Test
    fun `契约离线档事件许可仍为 false 全局语义未改`() {
        assertFalse(ComponentVisualState.OFFLINE.eventsAllowed)
        org.junit.Assert.assertEquals(1f, ComponentVisualState.OFFLINE.contentAlpha, 1e-6f)
    }

    @Test
    fun `独立入口在真正禁用时仍被拦住`() {
        val allowed = resolveEventsAllowed(
            visual = ComponentVisualState.DISABLED,
            standaloneInteractive = true,
            enabled = false,
            loading = false,
        )

        assertFalse("禁用硬闸优先于独立放行", allowed)
    }

    @Test
    fun `独立入口在加载中仍被拦住`() {
        val allowed = resolveEventsAllowed(
            visual = ComponentVisualState.LOADING,
            standaloneInteractive = true,
            enabled = true,
            loading = true,
        )

        assertFalse("加载硬闸优先于独立放行", allowed)
    }

    @Test
    fun `空数据档仍按契约放行 语义不变`() {
        val allowed = resolveEventsAllowed(
            visual = ComponentVisualState.EMPTY,
            standaloneInteractive = false,
            enabled = true,
            loading = false,
        )

        assertTrue("EMPTY 态沿用旧口径可点，本单不动其语义", allowed)
    }
}
