package io.legado.app.ui.map.crossing.engine

/**
 * 内置演示战役数据（纯 JSON 字符串，§六 验收战役之一：四渡赤水）。
 *
 * 为什么战役数据以字符串常量收口在这里：引擎层不读 Android 资源、不联网；壳层首开即用
 * 这份 JSON 经 [CampaignJsonParser] 解析跑通演示。换仗只换这份 JSON / 外部输入，引擎代码不动。
 * 坐标为赤水河流域概略位置，仅用于推演演示，不做测绘精度承诺。
 */
object DemoCampaigns {

    /** 四渡赤水：60 回合、6 支军队（§六 验收表口径），事件全部走条件触发器。 */
    const val CHISHUI_JSON: String = """
{
  "id": "chishui",
  "title": "四渡赤水",
  "era": "1935",
  "context": "红军川黔滇边来回穿插，摆脱多路围追堵截。演示战役：条件触发器 + 多次渡河 + 情报模糊。",
  "terrainFocus": { "center": [105.85, 28.35], "radius": 60 },
  "duration": 60,
  "weather": { "condition": "clear", "wind": 2.0, "temperature": 12.0 },
  "victory": { "type": "defend", "side": "red" },
  "sides": [
    { "id": "red", "name": "红军" },
    { "id": "white", "name": "围追军" }
  ],
  "diplomacy": [
    { "a": "red", "b": "white", "relation": "hostile" }
  ],
  "armies": [
    {
      "id": "red1", "name": "红一军团", "side": "red",
      "initialPos": [105.70, 28.43], "strength": 10, "type": "elite",
      "fatigue": 1, "morale": "high", "intent": "advance", "supply": 5,
      "target": [105.92, 28.40]
    },
    {
      "id": "red2", "name": "红三军团", "side": "red",
      "initialPos": [105.76, 28.29], "strength": 9, "type": "regular",
      "fatigue": 1, "morale": "steady", "intent": "advance", "supply": 5,
      "target": [105.90, 28.32]
    },
    {
      "id": "red3", "name": "中央纵队", "side": "red",
      "initialPos": [105.80, 28.36], "strength": 7, "type": "regular",
      "fatigue": 2, "morale": "steady", "intent": "advance", "supply": 4
    },
    {
      "id": "white1", "name": "黔军", "side": "white",
      "initialPos": [105.96, 28.46], "strength": 8, "type": "militia",
      "fatigue": 1, "morale": "steady", "intent": "advance", "supply": 4
    },
    {
      "id": "white2", "name": "川军", "side": "white",
      "initialPos": [105.99, 28.27], "strength": 8, "type": "regular",
      "fatigue": 0, "morale": "steady", "intent": "advance", "supply": 5
    },
    {
      "id": "white3", "name": "中央军", "side": "white",
      "initialPos": [106.06, 28.36], "strength": 9, "type": "elite",
      "fatigue": 0, "morale": "high", "intent": "advance", "supply": 5
    }
  ],
  "events": [
    {
      "trigger": { "turn": 3 },
      "action": "moraleChange", "target": "red2", "value": "steady",
      "reason": "一渡赤水，脱离当面尾追"
    },
    {
      "trigger": { "army": "white1", "strengthBelow": 3 },
      "action": "surrender", "target": "white1",
      "reason": "黔军兵力溃散，放下武器"
    },
    {
      "trigger": { "turn": 6 },
      "action": "supplyCut", "target": "white3",
      "reason": "追兵补给线拉长，后勤不继"
    },
    {
      "trigger": { "army": "red3", "strengthBelow": 2 },
      "action": "rout", "target": "red3",
      "reason": "纵队伤亡过重，脱离接触"
    }
  ],
  "terrain": {
    "procedural": true,
    "ridges": [
      { "x": 0.25, "y": 0.20, "h": 0.8, "w": 0.22 },
      { "x": 0.75, "y": 0.80, "h": 0.75, "w": 0.25 }
    ],
    "rivers": [
      { "pts": [[0.05, 0.42], [0.35, 0.55], [0.60, 0.45], [0.95, 0.58]] }
    ]
  }
}
"""
}
