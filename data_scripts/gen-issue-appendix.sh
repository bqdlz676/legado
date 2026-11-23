#!/usr/bin/env bash
# 生成 docs/问题清单-附录-逐文件.md —— 问题清单的逐文件落点（穷举，不许靠印象派单）。
# 为什么: 派单只给"某目录"等于让子AI自己猜范围；附录取真实文件清单，范围可核对、可回验。
# 适用: 每次清单增删后重跑一次；本脚本只读仓库，不写代码。
set -euo pipefail
cd "$(dirname "$0")/.."

UI=app/src/main/java/io/legado/app/ui
S=app/src/main/java
R=app/src/main/res
OUT=docs/问题清单-附录-逐文件.md

emit_list() {  # $1=标题 $2=计数 $3=命令输出（逐行路径）
  printf '## %s\n\n**计数：%s**\n\n```\n%s\n```\n\n' "$1" "$2" "$3"
}
count() { printf '%s' "$1" | grep -c . || true; }

{
echo "# 问题清单 · 附录：逐文件穷举（自动生成，勿手改）"
echo
echo "> 生成时间 $(date '+%Y-%m-%d %H:%M') ｜ 仓库 HEAD \`$(git rev-parse --short HEAD)\`"
echo "> 用途：给 \`docs/问题清单.md\` 提供逐文件落点；派单直接从这里取文件，不许靠印象。"
echo "> 重新生成：\`bash data_scripts/gen-issue-appendix.sh\`"
echo

L=$(ls "$R"/layout/*.xml 2>/dev/null | sed "s#$R/layout/##"); emit_list "H1 · res/layout 遗留 XML 布局" "$(count "$L")" "$L"
L=$(ls "$R"/menu/*.xml 2>/dev/null | sed "s#$R/menu/##"); emit_list "H2 · res/menu 遗留 XML 菜单" "$(count "$L")" "$L"
L=$(grep -rl 'class .*Activity : ' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "H3 · 旧 Activity 宿主" "$(count "$L")" "$L"
L=$(grep -rl 'DialogFragment' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "H4 · 仍用 DialogFragment（旧弹层）" "$(count "$L")" "$L"
L=$(grep -rl 'setContentView' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "H5a · setContentView" "$(count "$L")" "$L"
L=$(grep -rl 'findViewById' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "H5b · findViewById" "$(count "$L")" "$L"
L=$(grep -rl 'LayoutInflater' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "H5c · LayoutInflater" "$(count "$L")" "$L"
L=$(grep -rl 'RecyclerView.Adapter' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "H5d · RecyclerView.Adapter" "$(count "$L")" "$L"
L=$(grep -rl 'ViewBinding' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "H6a · ViewBinding" "$(count "$L")" "$L"
L=$(grep -rl 'androidx.appcompat' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "H6b · androidx.appcompat" "$(count "$L")" "$L"
L=$(grep -rl 'com.google.android.material' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "H6c · Material 组件" "$(count "$L")" "$L"
L=$(grep -rL 'androidx.compose' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "H7 · ui 层无 Compose import（尚未迁）" "$(count "$L")" "$L"
L=$(grep -rlE '#(000000|FFFFFF|FF000000|FFFFFFFF)' "$R" --include=*.xml | sed "s#$R/##"); emit_list "I1 · XML 含纯黑/纯白" "$(count "$L")" "$L"
L=$(grep -rl 'linear-gradient\|<gradient' "$R" --include=*.xml | sed "s#$R/##"); emit_list "I2 · XML 含 gradient" "$(count "$L")" "$L"
L=$(grep -rE 'Color\.(Black|White)' "$UI" --include=*.kt | sed "s#$S/io/legado/app/#…#"); emit_list "I3 · Kotlin Color.Black/White" "$(count "$L")" "$L"
L=$(grep -rnE 'catch ?\([^)]*\) ?\{ ?\}|catch ?\(._?\) ?\{' "$S" --include=*.kt | cut -c1-160 | sed "s#$S/io/legado/app/#…#"); emit_list "J6 · 静默吞异常" "$(count "$L")" "$L"
L=$(grep -rnE 'TODO|FIXME|XXX|HACK' "$S" --include=*.kt | cut -c1-160 | sed "s#$S/io/legado/app/#…#"); emit_list "J7 · TODO/FIXME/XXX/HACK" "$(count "$L")" "$L"
L=$(grep -rn '@Suppress("unused")' "$S" --include=*.kt | cut -c1-140 | sed "s#$S/io/legado/app/#…#"); emit_list "J8 · @Suppress(\"unused\")" "$(count "$L")" "$L"
L=$(grep -rn 'runBlocking' "$UI" --include=*.kt | cut -c1-140 | sed "s#$S/io/legado/app/#…#"); emit_list "K1 · runBlocking" "$(count "$L")" "$L"
L=$(grep -rn 'Thread.sleep' "$S" --include=*.kt | cut -c1-140 | sed "s#$S/io/legado/app/#…#"); emit_list "K2 · Thread.sleep" "$(count "$L")" "$L"
L=$(grep -rnE 'delay\([0-9]+' "$UI" --include=*.kt | cut -c1-140 | sed "s#$S/io/legado/app/#…#"); emit_list "K3 · 硬编码 delay( 动画" "$(count "$L")" "$L"
L=$(find "$S" -name '*.kt' -print0 | xargs -0 wc -l 2>/dev/null | awk '$1>1000 && $2!="total"{printf "%s行 %s\n",$1,$2}' | sed "s#$S/io/legado/app/#…#" | sort -rn); emit_list "L1 · 巨型文件（>1000 行）" "$(count "$L")" "$L"
L=$(grep -rnE '尚未|待接|暂未|未实现|还没接' "$S" --include=*.kt | cut -c1-160 | sed "s#$S/io/legado/app/#…#"); emit_list "J1 · 自认未完成注释" "$(count "$L")" "$L"
L=$(grep -n -B3 'android:exported="true"' app/src/main/AndroidManifest.xml | grep -E 'android:name=|exported' | cut -c1-120); emit_list "M1 · android:exported=\"true\"" "$(count "$L")" "$L"
L=$(find app/src -path '*test*' -name '*.kt' | wc -l); echo "## N1 · 测试规模"; echo; echo "测试文件 ${L} 个，@Test 方法 $(grep -rh '@Test' app/src --include=*.kt | wc -l) 个（是否全绿以 CI 为准，最后一次全绿在 98ee6d0 的 2736 tests）。"; echo
} > "$OUT"

echo "已生成 $OUT（$(wc -l < "$OUT") 行）"
