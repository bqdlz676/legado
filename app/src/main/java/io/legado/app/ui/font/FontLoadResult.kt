package io.legado.app.ui.font

/**
 * 字体加载结果。五种状态覆盖切片-14 要求的全部兜底路径：
 * 命中加载成功 / 子集缺失 / 超出体积红线被拒 / 资产损坏加载失败 / （兜底取用见注册表）。
 */
sealed interface FontLoadResult {

    /** 对应的字体角色。 */
    val role: FontRole

    /** 本结果应回落的系统字体族。 */
    val fallback: FontFallbackKind

    /** 子集命中并加载成功。 */
    data class Loaded(
        override val role: FontRole,
        /** 平台字体句柄（Android 侧为 Typeface；单测侧为假句柄）。 */
        val handle: Any,
        val assetName: String,
        val bytes: Long,
    ) : FontLoadResult {
        override val fallback: FontFallbackKind get() = role.fallback
    }

    /** 资产目录内没有命中任何候选文件名。 */
    data class MissingAsset(
        override val role: FontRole,
    ) : FontLoadResult {
        override val fallback: FontFallbackKind get() = role.fallback
    }

    /** 文件存在但超出该角色体积红线，拒绝内置、回落系统字体。 */
    data class RejectedOversize(
        override val role: FontRole,
        val assetName: String,
        val bytes: Long,
        val budgetBytes: Long,
    ) : FontLoadResult {
        override val fallback: FontFallbackKind get() = role.fallback
    }

    /** 资产可读但字体构造失败（损坏 / 非字体格式 / 解析异常）。 */
    data class LoadFailed(
        override val role: FontRole,
        val assetName: String,
        val cause: Throwable,
    ) : FontLoadResult {
        override val fallback: FontFallbackKind get() = role.fallback
    }
}
