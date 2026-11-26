package io.legado.app.help.artifact.registry

import io.legado.app.help.artifact.ArtifactCategory
import io.legado.app.help.artifact.ArtifactRoute
import io.legado.app.help.bubble.contract.Kind

/**
 * D16 十七类注册表单条登记（F-07 定稿名单，`docs/口径冻结-2026-11-24-15点后.md:20`）。
 *
 * 为什么一类一条、字段封闭在此：09:13「加新类别只加数据表，不改 Provider/UI」——
 * 新增类别时只在 [ArtifactRegistry] 追加一条本登记，路由、懒加载、来源许可都随条目自带，
 * Provider 与 UI 不感知新类别，不在别处另写一份类映射（单源）。
 *
 * @param id            稳定机器 id（英文 wire，查询与资产路径共用，不改写为中文）。
 * @param displayName   中文类别名，与 F-07 定稿逐字一致，供索引页 / 气泡展示。
 * @param schemaTable   落库 schema 表名（新表五张或既有表七张，见 [io.legado.app.help.artifact.ArtifactSchema]）。
 * @param schemaTypes   本登记接受的全部 schema type；首项为代表 type，其余为子类型
 *                      （如 tower 收 tower/pavilion，astronomy 收五星宿子类）。
 * @param assetPath     种子 / 全量数据资产相对路径；本片种子内置内存表，资产文件归后续数据片落盘。
 * @param lazy          懒加载标记（09:72）：大库命中才 load、不全量首触驻留；种子小库本片置 false，
 *                      全量数据接入时按数据量级回填。
 * @param source        数据来源（古籍篇目 / 库名，09:68 清单逐类标注）。
 * @param license       许可口径：公版古籍辑录著作权过期；第三方条目按原条目许可（如 CC BY-SA）；
 *                      种子为公版合成口径，全量许可归数据片逐源登记（09:75）。
 * @param route         承接路由：既有槽（D1/D2/D3 原 Provider 路径，不双轨）或通用 EntityProvider。
 * @param category      回链契约层 [ArtifactCategory]；F-07 拆分 / 更名后枚举尚未回填的项为 null，
 *                      裁决回填前不硬挂旧枚举。
 * @param basis         归属依据（原文锚点行号），供验收逐行回源。
 */
data class ArtifactRegistration(
    val id: String,
    val displayName: String,
    val schemaTable: String,
    val schemaTypes: List<String>,
    val assetPath: String,
    val lazy: Boolean,
    val source: String,
    val license: String,
    val route: ArtifactRoute,
    val category: ArtifactCategory?,
    val basis: String,
) {

    /** 代表 type（[schemaTypes] 首项）；既有槽类别无子类型时为 null。 */
    val schemaType: String? get() = schemaTypes.firstOrNull()

    /** 兜底气泡类型；枚举待回填时退化为 [Kind.WORD]，不抛异常。 */
    val fallbackKind: Kind get() = category?.fallbackKind ?: Kind.WORD
}
