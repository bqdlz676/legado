package io.legado.app.ui.book.read

import io.legado.app.ui.book.read.ink.ReaderSheetExitPhase
import io.legado.app.ui.book.read.ink.SheetExitPhaseGate

/**
 * B26 批注弹窗离场两段式守卫（纯 JVM，不引用 Android / Compose 类型，普通单测可断言）。
 *
 * 〔B30 口径收口〕
 * 阶段枚举 [ReaderSheetExitPhase]（OPEN / EXITING / REMOVED）、迁移规则与
 * `visible` / `isComposed` / `canOpen` 派生口径全部继承 B30 纯 JVM 基类
 * [SheetExitPhaseGate]；本守卫无会话请求负载（highlight 参数在构造时固定），
 * 不再自写第二份阶段判断。B21 `ReaderSheetExitGate` 同继承该基类，两守卫派生口径单一同源。
 *
 * 〔与 B21 守卫的差异〕
 * 无 retainedRequest：弹窗子树保留依据就是阶段本身（OPEN / EXITING 均在场），REMOVED
 * 后 Fragment 由 Compose 层收束回调摘除。
 */
class NoteDialogExitGate : SheetExitPhaseGate() {

    /** 新开会话：回到在场段。 */
    fun open() = enterOpen()
}
