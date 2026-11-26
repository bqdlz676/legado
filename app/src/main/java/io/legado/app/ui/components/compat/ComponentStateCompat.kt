package io.legado.app.ui.components.compat

import io.legado.app.ui.components.contract.ComponentVisualState

/**
 * S-04 状态收口 · 过渡身份扩展（旧枚举定义已删除，本扩展不再做档位映射）。
 *
 * 〔历史〕旧七态枚举曾登记在 `components/ComponentState.kt`，本适配原做「旧 → 新契约」单向
 * 档位搬运。本单双源收口：旧枚举定义物理删除，唯一状态模型为
 * [ComponentVisualState] 契约枚举；此处保留一个**身份函数**，仅为尚未迁移的并行域调用点
 * （`ui/components/InkCard.kt`、`ui/main/bookshelf/BookshelfScreen.kt`）维持旧调用写法可编译。
 *
 * 〔为什么不是第二套模型〕
 * 身份函数不映射任何档位、不复制透明度 / 事件许可规则，入参出参同为契约枚举；不存在反向映射，
 * 也不存在第二套优先级口径。待上述并行域把 `.toVisualState()` 调用点直连契约枚举后，本扩展
 * 与 `ComponentState.kt` 内的类型别名一并删除（A3：新链路接好再物理删旧，不留兼容层兜底）。
 */
fun ComponentVisualState.toVisualState(): ComponentVisualState = this
