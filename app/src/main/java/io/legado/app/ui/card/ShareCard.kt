package io.legado.app.ui.card

import androidx.annotation.RequiresApi
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 落文 · 分享卡片（书摘生成图片）（切片-25「十、分享卡片」逐字落地）。
 *
 * 规格原文：选中文段竖排或横排 18sp 墨色（用户可切方向）；出处 10sp 石青横排；右下落文金点 8dp；
 * 背景四套可选（纸白/暗色/护眼/宣纸纹理）+ 极淡暖金渐变 opacity 0.05；导出 PNG 到
 * `/sdcard/Download/落文卡片/`；文件名 `书名_日期.png`。
 *
 * 五态 / 阴影 / 圆角 / 颗粒一律走 [CardBase]；色值优先取 [CardTokens]，四套底色中基座未定义的
 * 暗色 / 护眼 / 宣纸暖米三套在本文件顶部由既有 token 经 `lerp` 混合派生（判据 2 不写十六进制字面量）。
 */

/** 分享卡四套可选底色；顺序对应规格「纸白 / 暗色 / 护眼 / 宣纸纹理」，调用方按用户选择传入。 */
enum class ShareBg { Paper, Dark, EyeCare, Rice }

// ─────────────────────────── 四套底色派生常量（判据 2：不写十六进制色值字面量，全部从既有 token 混合派生） ───────────────────────────

/**
 * 暗色底：`ink` 向 `Color.Black` 混合 0.35f 加深到深墨近黑；
 * 为什么派生而非字面量：判据 2 硬编码色值 ≤2 且例外清单不含背景色，基座 token 是唯一来源；
 * 适用：夜间 / 暗色壁纸场景，文字随之翻为纸白亮色；导出静态图，不参与五态阴影插值。
 */
// F-73 待规格化：暗色底色值与暗色下正文/出处翻色规则无规格，0.35f 比例为写码自拟（审计 表三#1，待重做后回填规格出处）
private val ShareDarkBg: Color = lerp(CardTokens.ink, Color.Black, 0.35f)

/**
 * 护眼米绿：`cardPaper` 向 `stoneBlue` 混合 0.12f 偏出低饱和灰绿；
 * 为什么取石青为绿源：石青是基座既有语义色，偏色方向与护眼绿一致，不另造色相；
 * 适用：长时间阅读护眼模式下的书摘卡，不抢墨色正文；导出静态图，不参与五态阴影。
 */
// F-73 待规格化：护眼绿色相归属与派生比例无规格，0.12f 比例为写码自拟（审计 表三#1，待重做后回填规格出处）
private val ShareEyeCareBg: Color = lerp(CardTokens.cardPaper, CardTokens.stoneBlue, 0.12f)

/**
 * 宣纸暖米：`cardPaper` 向 `warmGold` 混合 0.10f 偏出暖米调；
 * 为什么取暖金为暖源：暖金是基座既有强调色，微量混入只增纸感不发光；
 * 适用：宣纸纹理套，配合基座颗粒叠层呈现纸纤维感；导出静态图，不参与五态阴影。
 */
// F-73 待规格化：宣纸暖米色值与 0.05 之外叠层口径无规格，0.10f 比例为写码自拟（审计 表三#1，待重做后回填规格出处）
private val ShareRiceBg: Color = lerp(CardTokens.cardPaper, CardTokens.warmGold, 0.10f)

/**
 * 单张分享卡片：选中文段 + 出处 + 落款金点，供宿主截图 / 导出 PNG 作书摘图。
 *
 * @param text 选中文段；适用：卡面主文案，18sp 墨色，按 [vertical] 切换竖排逐字下排 / 横排自动换行。
 * @param source 出处；适用：卡面底部横排次级标注，10sp 石青；暗色底下翻云灰保证可读。
 * @param vertical 排版方向开关；适用：用户在横排 / 竖排之间切换，与阅读页方向选择同源。
 * @param bg 四套底色之一；适用：纸白 / 暗色 / 护眼 / 宣纸纹理，渐变叠层四套通用。
 * @param sheetName 书名（sheet 名）；适用：不直接上卡面，供宿主拼导出文件名与出处前缀兜底。
 * @param date 日期字符串；适用：导出文件名第二段，格式由调用方保证（建议 yyyy-MM-dd）。
 * @param state 基座五态；Loading 态由 [CardBase] 自绘骨架灰块 + 暖金斜光带。
 * @param modifier 外部尺寸 / 对齐；分享图宽高由调用方按导出图比例传入，本卡不锁死。
 */
@Composable
fun ShareCard(
    text: String,
    source: String,
    vertical: Boolean,
    bg: ShareBg,
    sheetName: String,
    date: String,
    state: CardState = CardState.Default,
    modifier: Modifier = Modifier,
) {
    // 底色按四套枚举选定；Paper 复用基座纸白，其余三套取本文件兜底常量。
    val bgColor = when (bg) {
        ShareBg.Paper -> CardTokens.cardPaper
        ShareBg.Dark -> ShareDarkBg
        ShareBg.EyeCare -> ShareEyeCareBg
        ShareBg.Rice -> ShareRiceBg
    }

    // 正文色：暗底下墨色不可读，翻纸白；其余三套沿用基座墨色，规格 18sp 墨色在亮底上逐字落地。
    val contentColor = if (bg == ShareBg.Dark) CardTokens.cardPaper else CardTokens.ink

    // 出处色：亮底取石青；暗底石青过暗，兜底云灰，保持次级标注的明度层次。
    val sourceColor = if (bg == ShareBg.Dark) CardTokens.cloudGray else CardTokens.stoneBlue

    CardBase(
        state = state,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            // 四套底色铺满基座面；基座自身纸白底被本层完全覆盖，圆角由 CardBase 裁切约束。
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
        ) {
            // 极淡暖金渐变 opacity 0.05：自上而下暖金微光浮在底色上，只增氛围不压正文；
            // 0.05f 为规格给定峰值透明度，适用：四套底色通用叠层，暗色底下同样保留一缕金辉。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                CardTokens.warmGold.copy(alpha = 0.05f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 内边距 12dp：与卡片栅格同值气口；适用：文段 / 出处 / 金点不贴 16dp 圆角边缘。
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 文段主文案：[vertical] 为真走竖排逐字下排，否则横排自动换行；字号 18sp（规格逐字）。
                if (vertical) {
                    ShareVerticalText(
                        text = text,
                        fontSize = 18.sp,
                        color = contentColor,
                    )
                } else {
                    BasicText(
                        text = text,
                        style = TextStyle(
                            color = contentColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            // 宋体衬线：与阅读页东方排印口径一致；适用：书摘正文，不做粗体压韵。
                            fontFamily = FontFamily.Serif,
                        ),
                    )
                }

                // 底部行：出处横排居左，落款金点居右；出处恒横排，不随 [vertical] 切竖。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BasicText(
                        text = source,
                        style = TextStyle(
                            color = sourceColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Serif,
                        ),
                    )

                    // 落款金点 8dp：规格右下角金点；暖金实心圆，与基座金点同色，作为书摘落款印记。
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(CardTokens.warmGold, CircleShape),
                    )
                }
            }
        }
    }
}

/**
 * 竖排文段：逐字拆成单列 BasicText 纵向堆叠。
 * 为什么不用横排自动换行：规格要求文段可竖排，CJK 逐字下排即传统书页排版；
 * 适用：仅分享卡竖排方向，横排文字不要复用本组件。
 */
@Composable
private fun ShareVerticalText(
    text: String,
    fontSize: TextUnit,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        text.forEach { ch ->
            BasicText(
                text = ch.toString(),
                style = TextStyle(
                    color = color,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }
}

/**
 * 导出文件名：`书名_日期.png`。
 *
 * 书名内文件系统非法字符（`\ / : * ? " < > |`，FAT32 与 Android 存储均禁用）统一替换为 `_`；
 * 书名清洗后为空时兜底「未命名」，避免产出 `_日期.png` 无名文件。日期格式由调用方保证。
 */
fun shareCardFileName(book: String, date: String): String {
    val safeBook = book
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .trim()
        .ifEmpty { "未命名" }
    return "${safeBook}_$date.png"
}

/**
 * 导出分享卡 PNG：写入 `Download/落文卡片/`，文件名走 [shareCardFileName]（`书名_日期.png`）。
 *
 * 截图由宿主准备：`rememberGraphicsLayer()` 记录 [ShareCard] 组合内容，`toBitmap()` 得到 [bitmap] 传入；
 * 本函数只负责编码落盘，不持有 Compose 状态。降级链：API 29+ `MediaStore.Downloads` →
 * API <29 公共 Download 目录 `FileOutputStream` → 公共目录不可写时退到应用私有 Download 子目录。
 * 整段 try/catch：IOException / SecurityException / 存储挂载异常一律记 `Log.w` 并返回 `null`，
 * 绝不 throw 到 UI，卡片与宿主不崩溃（用户铁律：不允许崩溃 / 空白态）。
 *
 * @param context 宿主 Context；适用：取 ContentResolver / 公共目录 / 私有目录，函数内不长期持有。
 * @param bitmap 已渲染的卡片位图；适用：PNG 编码源，质量参数对 PNG 无效固定 100。
 * @param book 书名；适用：经 [shareCardFileName] 清洗非法字符后作文件名第一段。
 * @param date 日期字符串；适用：文件名第二段，格式由调用方保证（建议 yyyy-MM-dd）。
 * @return 成功返回可访问的 [Uri]（Q 为 MediaStore Uri，低版本为文件 Uri）；失败返回 `null`。
 */
suspend fun exportShareCardPng(
    context: Context,
    bitmap: Bitmap,
    book: String,
    date: String,
): Uri? {
    val fileName = shareCardFileName(book, date)
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportViaMediaStore(context, bitmap, fileName)
        } else {
            exportViaLegacyFile(context, bitmap, fileName)
        }
    } catch (e: IOException) {
        // IO 失败：磁盘满 / 流中断等；返回 null 由宿主提示导出失败，不重试不弹层。
        Log.w("ShareCard", "导出分享卡 IO 失败: ${e.message}", e)
        null
    } catch (e: SecurityException) {
        // 权限被拒：低版本 WRITE_EXTERNAL_STORAGE 未授予；适用：权限被用户拒绝，不再二次申请。
        Log.w("ShareCard", "导出分享卡权限被拒: ${e.message}", e)
        null
    } catch (e: Exception) {
        // 兜底：存储未挂载 / 未知异常一律不冒泡；适用：任何未预见的落盘异常，保证 UI 不崩。
        Log.w("ShareCard", "导出分享卡失败: ${e.message}", e)
        null
    }
}

/**
 * API 29+ 分支：MediaStore.Downloads 作用域内写入，无需存储运行时权限。
 * 先 `IS_PENDING=1` 占条目，写完置 0 通知媒体库扫描；中途异常删除 pending 占位，不留空条目。
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun exportViaMediaStore(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "image/png")
        // 目录固定 Download/落文卡片：规格给定路径；RELATIVE_PATH 末段不带斜杠，遵循 MediaStore 规范。
        put(MediaStore.Downloads.RELATIVE_PATH, "Download/落文卡片")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { stream ->
            // PNG 无损压缩；100 为质量占位参数，PNG 编码忽略该值，适用：保留书摘文字边缘清晰。
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } ?: return null
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    } catch (e: Exception) {
        // 写入失败：回滚 pending 记录；异常上抛由 [exportShareCardPng] 统一记日志返回 null。
        resolver.delete(uri, null, null)
        throw e
    }
}

/**
 * API <29 分支：公共 Download 目录 + `落文卡片` 子目录直接写文件。
 * 降级条件：公共目录 `mkdirs()` 失败（权限被拒或外部存储未挂载）时，退到
 * `context.getExternalFilesDir(DIRECTORY_DOWNLOADS)/落文卡片` 应用私有目录；为什么降级而非报错：
 * 私有目录无需任何权限，保证至少导出成功一次，实际落点写进日志供宿主提示用户。
 */
private fun exportViaLegacyFile(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
): Uri? {
    val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val publicTarget = File(publicDir, "落文卡片")
    val targetDir = if (publicTarget.exists() || publicTarget.mkdirs()) {
        publicTarget
    } else {
        val privateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        Log.w("ShareCard", "公共 Download 不可写，降级应用私有目录: ${privateDir?.absolutePath}")
        File(privateDir ?: return null, "落文卡片")
    }
    if (!targetDir.exists() && !targetDir.mkdirs()) return null
    val file = File(targetDir, fileName)
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return Uri.fromFile(file)
}
