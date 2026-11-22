// ★ 落文·势力图层资产（对齐 CHGIS / 谭其骧《中国历史地图集》）
// 每条带 from/to 起止公元年（公元前为负）与 era 朝代标签；图层按「年份 ∈ [from,to] 且 era 相符」双条件过筛。
// 铁律：春秋晋（-770~-453）、战国三晋（赵/魏/韩，-403 起）、西晋/东晋（265 起）是三个不同实体，名字都可称「晋」，绝不混层。
// 坐标为历史地图集示意范围，tier3 不代表精确边界；每条带 source + confidence。

// 分朝代疆域多边形层（战国七雄 + 春秋周鲁吴越）
window.POWERS = [
  // —— 战国（-475~-221）：三家分晋后晋国旗号消亡，三晋为赵/魏/韩 ——
  {"name":"秦","era":"战国","from":-475,"to":-221,"color":"#B33A2B","poly":[[35,104.5],[36.5,106.5],[37.5,108.5],[37,110.5],[35.5,110.8],[34.3,110.6],[33.6,108],[33.8,105.5]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"楚","era":"战国","from":-475,"to":-221,"color":"#2E7D6B","poly":[[33.6,111],[33.2,113.5],[32.5,116],[30.5,117.5],[28.5,116.5],[27.5,113.5],[28.5,111],[30.5,110],[32.5,110.5]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"齐","era":"战国","from":-475,"to":-221,"color":"#3C6E9E","poly":[[37.8,118],[37.6,120.5],[36.8,122.5],[35.6,121.5],[35,119],[35.6,117],[36.8,116.5]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"燕","era":"战国","from":-475,"to":-222,"color":"#7A5FA6","poly":[[41.5,116.5],[41.8,120],[40.5,123],[39,121.5],[38.5,117.5],[39.5,115.5],[40.8,115.5]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"赵","era":"战国","from":-403,"to":-221,"color":"#C08A2E","poly":[[39.5,113.5],[39.2,116],[37.5,117],[36,115.5],[35.5,113],[36.5,111.5],[38,111]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"魏","era":"战国","from":-403,"to":-225,"color":"#8A5A3C","poly":[[36,110.5],[36.2,113.5],[35.4,115.8],[34.2,115],[33.8,113],[34.5,110.8]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"韩","era":"战国","from":-403,"to":-230,"color":"#6B8E3C","poly":[[35,111.5],[35.2,113.8],[34.4,114.5],[33.6,113],[33.8,111.3]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  // —— 春秋（-770~-476） ——
  {"name":"周","era":"春秋","from":-770,"to":-476,"color":"#A8894F","poly":[[35,111.5],[35,113.5],[34.2,113.8],[33.9,112],[34.4,111]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"鲁","era":"春秋","from":-770,"to":-476,"color":"#5E8FA8","poly":[[36.2,116.2],[36,118],[35,118.2],[34.6,116.8],[35.3,115.8]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"吴","era":"春秋","from":-770,"to":-473,"color":"#4E7E6E","poly":[[32.5,119],[32.3,121.8],[31,122],[30.2,120.5],[30.6,118.5],[31.8,118.3]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"越","era":"春秋","from":-770,"to":-334,"color":"#7B6BA8","poly":[[30.5,119.5],[30.3,121.8],[29,122],[28,120],[28.8,118.8],[29.8,118.8]],"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"}
];

// 示意色块层：只表势力范围，不是精确边界（tier 3），不画点。
// from/to 与 era 双条件过筛；同名「晋」按朝代拆成三条互斥区间。
window.POWERS_CIRCLE = [
  // —— 春秋诸侯（-770~-476；晋室止于 -453 三家分晋） ——
  {"name":"齐","era":"春秋","from":-770,"to":-476,"center":[36.86,118.31],"r":160,"color":"#2E7D6B","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"晋","era":"春秋","from":-770,"to":-453,"center":[35.7,111.4],"r":230,"color":"#8A6A3E","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low","note":"春秋晋国；中心对齐晋都绛（今翼城），非现代山西晋城"},
  {"name":"楚","era":"春秋","from":-770,"to":-476,"center":[30.33,112.2],"r":320,"color":"#B33A2B","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"秦","era":"春秋","from":-770,"to":-476,"center":[34.52,107.39],"r":210,"color":"#6B5B95","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"宋","era":"春秋","from":-770,"to":-476,"center":[34.41,115.65],"r":120,"color":"#C08A2E","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"鲁","era":"春秋","from":-770,"to":-476,"center":[35.6,116.98],"r":95,"color":"#4A6FA5","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"卫","era":"春秋","from":-770,"to":-476,"center":[35.6,114.2],"r":100,"color":"#7A8B4A","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"周","era":"春秋","from":-770,"to":-476,"center":[34.62,112.45],"r":60,"color":"#A8894F","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low","note":"东周王城洛邑畿内，示意范围"},
  {"name":"郑","era":"春秋","from":-770,"to":-476,"center":[34.4,113.74],"r":90,"color":"#A0522D","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"燕","era":"春秋","from":-770,"to":-476,"center":[39.9,116.4],"r":210,"color":"#3E6B6B","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"吴","era":"春秋","from":-770,"to":-473,"center":[31.3,120.6],"r":210,"color":"#2F6E8E","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"越","era":"春秋","from":-770,"to":-334,"center":[30,120.6],"r":190,"color":"#4B7A5A","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"陈","era":"春秋","from":-770,"to":-476,"center":[33.73,114.9],"r":70,"color":"#8E7B4F","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"蔡","era":"春秋","from":-770,"to":-476,"center":[33.26,114.37],"r":70,"color":"#9A6A4A","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"曹","era":"春秋","from":-770,"to":-476,"center":[35,115.6],"r":60,"color":"#7A6650","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"滕","era":"春秋","from":-770,"to":-476,"center":[35.1,117.15],"r":40,"color":"#8A8A6A","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"许","era":"春秋","from":-770,"to":-476,"center":[34,113.8],"r":50,"color":"#6E7B8A","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"邾","era":"春秋","from":-770,"to":-476,"center":[35.4,117],"r":40,"color":"#8B6E52","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"莒","era":"春秋","from":-770,"to":-476,"center":[35.6,118.8],"r":60,"color":"#5A7A6A","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"虞","era":"春秋","from":-770,"to":-476,"center":[34.8,110.9],"r":50,"color":"#7A6A8A","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"虢","era":"春秋","from":-770,"to":-476,"center":[34.7,110.9],"r":50,"color":"#8A5A5A","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  // —— 西汉郡国（-202~8） ——
  {"name":"京兆","era":"西汉","from":-202,"to":8,"center":[34.3,108.9],"r":150,"color":"#8A6A3E","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"河东","era":"西汉","from":-202,"to":8,"center":[35.3,111.2],"r":140,"color":"#6B5B95","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"河内","era":"西汉","from":-202,"to":8,"center":[35.2,113],"r":120,"color":"#7A8B4A","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"山东","era":"西汉","from":-202,"to":8,"center":[36,117.3],"r":200,"color":"#4A6FA5","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"江淮","era":"西汉","from":-202,"to":8,"center":[32.3,117],"r":180,"color":"#2E7D6B","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"巴蜀","era":"西汉","from":-202,"to":8,"center":[30.5,105.5],"r":260,"color":"#B33A2B","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"辽东","era":"西汉","from":-202,"to":8,"center":[41,123],"r":260,"color":"#3E6B6B","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"陇西","era":"西汉","from":-202,"to":8,"center":[35,104.5],"r":180,"color":"#C08A2E","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"汉中","era":"西汉","from":-202,"to":8,"center":[33,107],"r":110,"color":"#A0522D","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"晋阳","era":"西汉","from":-202,"to":8,"center":[37.8,112.5],"r":130,"color":"#8E7B4F","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low","note":"太原晋阳，汉太原郡治，非春秋晋都"},
  // —— 三国（220~280） ——
  {"name":"魏","era":"三国","from":220,"to":280,"center":[35,114.5],"r":330,"color":"#4A6FA5","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"蜀","era":"三国","from":221,"to":263,"center":[30.6,104.2],"r":300,"color":"#B33A2B","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  {"name":"吴","era":"三国","from":222,"to":280,"center":[30.2,118.5],"r":320,"color":"#2E7D6B","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low"},
  // —— 西晋 / 东晋（265~420）：国号亦称「晋」，与春秋晋严格分层，区间互斥 ——
  {"name":"西晋","era":"西晋","from":265,"to":316,"center":[34.62,112.45],"r":300,"color":"#6E5B8A","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low","note":"司马氏西晋，都洛阳；非春秋晋国"},
  {"name":"东晋","era":"东晋","from":317,"to":420,"center":[32.05,118.78],"r":260,"color":"#5E7E9E","tier":3,"source":"谭其骧《中国历史地图集》/CHGIS 考证","confidence":"low","note":"司马氏东晋，都建康；非春秋晋国"}
];
