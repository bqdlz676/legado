package io.legado.app.ui.main.bookshelf

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * 书单按来源归组帮手。
 *
 * 外部分享 / 文件入口拿不到用户当前可见分组（Intent 只带数据 URI，无 groupId extra，
 * 书单 JSON 载荷也不带分组），因此用导入来源 URI 归一化出稳定键：同一来源重复导入
 * 复用同一分组；来源身份不可识别（临时文件、opaque URI 等）时回落未分组（groupId = 0）。
 * 本文件只做纯函数归一化与位分配，不碰 Room / Context，便于纯 JVM 单测。
 */
internal const val IMPORT_GROUP_NAME_PREFIX = "导入·"

private const val IMPORT_GROUP_TAG_LENGTH = 8
private const val IMPORT_GROUP_KEY_MAX_LENGTH = 40
private const val IMPORT_GROUP_MAX_BIT_SHIFT = 62

/**
 * 把导入来源归一化为稳定分组键；识别不出稳定身份时返回 null（调用方回落未分组）。
 *
 * 取字段口径（回证）：
 * - http/https：scheme + host + path（host 小写，去 query / fragment / 结尾斜杠）；
 *   同一清单的不同分享链接若 query 带临时 token，剥离后仍归同一组。
 * - content：最后路径段解码后取冒号与斜杠之后的文件名。
 * - file / legado 等其他 scheme：临时文件或内部跳转，身份不稳定，返回 null。
 */
internal fun sourceImportGroupKey(source: String): String? {
    if (source.isBlank()) return null
    val uri = runCatching { URI(source.trim()) }.getOrNull() ?: return null
    return when (uri.scheme?.lowercase()) {
        "http", "https" -> {
            val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            val path = uri.path?.trimEnd('/').orEmpty()
            "$host$path".takeIf { it.isNotBlank() }
        }

        "content" -> uri.rawPath
            ?.substringAfterLast('/')
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        else -> null
    }
}

/** 分组名中的来源指纹：sha256 前 8 位十六进制，避免长 URI 直接进分组名。 */
internal fun importGroupTag(key: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(IMPORT_GROUP_TAG_LENGTH)
}

/** 分组名是否对应某来源指纹（复用既有来源分组，不重复建组）。 */
internal fun matchesImportGroupTag(groupName: String, tag: String): Boolean =
    groupName.startsWith(IMPORT_GROUP_NAME_PREFIX) && tag in groupName

/** 显示用短键：折叠空白后截断，长路径不顶宽分组名。 */
internal fun importGroupDisplayName(key: String): String =
    key.replace(Regex("\\s+"), " ").trim().take(IMPORT_GROUP_KEY_MAX_LENGTH)

/**
 * 从现有正组位掩码 [idsSum] 中分配下一个空闲位（位掩码，2 的幂）；
 * 63 个正组位耗尽时返回 null，调用方回落未分组。
 */
internal fun allocateFreeGroupBit(idsSum: Long): Long? {
    var id = 1L
    repeat(IMPORT_GROUP_MAX_BIT_SHIFT + 1) {
        if (id and idsSum == 0L) return id
        id = id shl 1
    }
    return null
}
