# 锚点卡 · B27 MapWebHolder 孤儿

> 用途：B27 清理单开工依据。出自 `docs/清理-B24-旧DetailSheet清剿.md` 的二阶影响段。

## 一、事实锚点
- `ui/main/MapWebHolder.kt`：B24 删除 `DetailSheet.kt:132` 唯一调用点后，生产源码零调用。
- `app/src/test/java/.../DesignTokenGuardTest.kt:159`：基线仍登记该类，直接删除会让基线测试红。

## 二、判据
1. 先回证零引用：主源码、测试、资源、Manifest 四类 grep，逐类给命中数。
2. 排除隐式持有：grep `MapWebHolder` 是否被反射/字符串/全局容器（如 Application 级持有、WebView 池）引用；有则转「只登记不删」。
3. 删除时同步摘除 `DesignTokenGuardTest.kt:159` 基线行，并说明基线行删除后该测试仍可通过（读该测试断言逻辑）。
4. 只删该文件与其基线行，不顺手改别的类。
5. 删后复跑同一组 grep 零命中。

## 三、边界
- 删除不可逆：先贴证据再删；不动 B18/B19/B20/B21/P04 已改文件。
- 若发现 MapWebHolder 仍被 WebView 复用池使用，保留并登记，不许为「清理干净」硬删。

## 四、验收
独立验收代理核对引用清单与删除前后 grep；CI 唯一编译手段。
