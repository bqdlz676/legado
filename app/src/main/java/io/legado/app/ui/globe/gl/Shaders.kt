package io.legado.app.ui.globe.gl

/**
 * OpenGL ES 2.0 着色器源码收口（切片 17 / 19 / 20）。
 *
 * 自绘渲染：不引入 Sceneform / Filament / three.js 等任何第三方渲染框架，
 * 只用 Android SDK 自带的 GLES20。着色器行数对齐规格红线：
 *  - 大气 fragment ≤ 20 行 GLSL；
 *  - 海洋逻辑收敛在 surface fragment 内（≤15 行核心）。
 *
 * 注意：本文件为 GL 层，需真机 / 模拟器验证；纯逻辑部分不在此文件。
 */
object Shaders {

    // ---------- 地球表面 ----------
    const val SURFACE_VERT = """
        attribute vec3 aPos;
        attribute vec3 aNormal;
        attribute vec2 aUv;
        uniform mat4 uProj;
        uniform mat4 uView;
        uniform mat4 uModel;
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        varying vec2 vUv;
        void main() {
            vec4 world = uModel * vec4(aPos, 1.0);
            vWorldPos = world.xyz;
            vNormal = normalize(mat3(uModel) * aNormal);
            vUv = aUv;
            gl_Position = uProj * uView * world;
        }
    """

    /**
     * 表面片元：卫星纹理 + 覆盖色 + Phong 漫反射/高光 + 海洋三层 + 冰盖 + 昼夜 + 云影 + 晨昏渐变。
     * 海洋核心片段（deep/specular/shallow）控制在 15 行以内。
     */
    const val SURFACE_FRAG = """
        precision mediump float;
        uniform sampler2D uDayTex;
        uniform sampler2D uNightTex;
        uniform sampler2D uCoverTex;
        uniform sampler2D uCloudTex;
        uniform vec3 uSunDir;
        uniform vec3 uCameraPos;
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        varying vec2 vUv;

        vec3 oceanDeep = vec3(0.106, 0.227, 0.361);   // #1B3A5C
        vec3 oceanShallow = vec3(0.227, 0.478, 0.541); // #3A7A8A

        void main() {
            vec3 n = normalize(vNormal);
            vec3 sun = normalize(uSunDir);
            vec3 viewDir = normalize(uCameraPos - vWorldPos);
            float sunDot = dot(n, sun);

            float band = cos(radians(5.0));
            float day = smoothstep(-band, band, sunDot);

            vec3 dayCol = texture2D(uDayTex, vUv).rgb;
            vec3 cover = texture2D(uCoverTex, vUv).rgb;
            vec3 nightCol = texture2D(uNightTex, vUv).rgb;
            float cloudA = texture2D(uCloudTex, vUv).a;

            // 海洋三层：深蓝底 → 太阳镜面高光 → 近岸浅海 turquoise
            float isWater = cover.b;
            vec3 ocean = oceanDeep;
            vec3 refl = reflect(-sun, n);
            float spec = pow(max(dot(refl, viewDir), 0.0), 48.0);
            ocean += spec * vec3(1.0);
            ocean = mix(ocean, oceanShallow, texture2D(uCoverTex, vUv).a);

            vec3 base = mix(ocean, dayCol, 1.0 - step(0.55, isWater));

            // 极地冰盖：冷白微蓝，specular 0.15；雪线微反光
            float ice = step(0.9, cover.r) * step(0.9, cover.g);
            base = mix(base, vec3(0.86, 0.91, 0.93), ice);

            float diffuse = max(sunDot, 0.0);
            vec3 lit = base * (0.25 + 0.75 * diffuse);
            lit += spec * mix(0.05, 0.15, ice);

            // 暗半球：亮度 ×0.15 + 偏蓝 + 夜景金点
            vec3 night = base * 0.15 * vec3(0.8, 0.88, 1.0) + nightCol;
            vec3 color = mix(night, lit, day);

            // 云影：云 alpha > 0.5 → 地面 ×0.95
            if (cloudA > 0.5) color *= 0.95;

            // 晨昏带：日出侧暖橙 / 日落侧蓝紫
            vec3 east = normalize(cross(sun, vec3(0.0, 1.0, 0.0)));
            float side = dot(east, n);
            float terminator = (1.0 - abs(day - 0.5) * 2.0);
            vec3 dawn = vec3(0.95, 0.62, 0.30);
            vec3 dusk = vec3(0.45, 0.40, 0.72);
            vec3 term = side > 0.0 ? dawn : dusk;
            color = mix(color, term, terminator * 0.25 * step(0.02, abs(side)));

            gl_FragColor = vec4(color, 1.0);
        }
    """

    // ---------- 大气内圈：菲涅尔半透壳（半径 100.5）----------
    const val ATMOS_VERT = """
        attribute vec3 aPos;
        attribute vec3 aNormal;
        uniform mat4 uProj;
        uniform mat4 uView;
        uniform mat4 uModel;
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        void main() {
            vec4 world = uModel * vec4(aPos, 1.0);
            vWorldPos = world.xyz;
            vNormal = normalize(mat3(uModel) * aNormal);
            gl_Position = uProj * uView * world;
        }
    """

    // fragment ≤ 20 行 GLSL（规格红线）
    const val ATMOS_FRAG = """
        precision mediump float;
        uniform vec3 uCameraPos;
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        void main() {
            vec3 n = normalize(vNormal);
            vec3 v = normalize(uCameraPos - vWorldPos);
            float fresnel = pow(1.0 - max(dot(n, v), 0.0), 3.0);
            vec3 glow = vec3(0.62, 0.78, 0.95) * fresnel;
            gl_FragColor = vec4(glow, fresnel * 0.55);
        }
    """

    // ---------- 大气外圈：极淡蓝光晕（半径 104，alpha 0.08）----------
    const val ATMOS_GLOW_FRAG = """
        precision mediump float;
        uniform vec3 uCameraPos;
        varying vec3 vNormal;
        varying vec3 vWorldPos;
        void main() {
            vec3 n = normalize(vNormal);
            vec3 v = normalize(uCameraPos - vWorldPos);
            float rim = pow(1.0 - max(dot(n, v), 0.0), 2.0);
            gl_FragColor = vec4(vec3(0.55, 0.70, 0.92), rim * 0.08);
        }
    """

    // ---------- 云层壳（半径 101，alpha = 云纹理 alpha ×0.3）----------
    const val CLOUD_VERT = """
        attribute vec3 aPos;
        attribute vec2 aUv;
        uniform mat4 uProj;
        uniform mat4 uView;
        uniform mat4 uModel;
        varying vec2 vUv;
        void main() {
            vUv = aUv;
            gl_Position = uProj * uView * uModel * vec4(aPos, 1.0);
        }
    """

    const val CLOUD_FRAG = """
        precision mediump float;
        uniform sampler2D uCloudTex;
        varying vec2 vUv;
        void main() {
            float a = texture2D(uCloudTex, vUv).a * 0.3;
            gl_FragColor = vec4(vec3(1.0), a);
        }
    """

    // ---------- 星空：点精灵（半径 900，固定不动）----------
    const val STAR_VERT = """
        attribute vec3 aPos;
        attribute float aSize;
        attribute vec4 aColor;
        uniform mat4 uProj;
        uniform mat4 uView;
        varying vec4 vColor;
        void main() {
            vColor = aColor;
            gl_Position = uProj * uView * vec4(aPos, 1.0);
            gl_PointSize = aSize;
        }
    """

    const val STAR_FRAG = """
        precision mediump float;
        varying vec4 vColor;
        void main() {
            vec2 c = gl_PointCoord - 0.5;
            float d = length(c);
            if (d > 0.5) discard;
            float alpha = smoothstep(0.5, 0.1, d);
            gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);
        }
    """

    // ---------- 太阳圆盘（直径 8，距离 400，金色辉光）----------
    const val SUN_VERT = """
        attribute vec2 aCorner;
        uniform vec3 uCenter;
        uniform vec3 uCameraRight;
        uniform vec3 uCameraUp;
        uniform float uRadius;
        uniform mat4 uProj;
        uniform mat4 uView;
        void main() {
            vec3 world = uCenter + uCameraRight * aCorner.x * uRadius
                                + uCameraUp * aCorner.y * uRadius;
            gl_Position = uProj * uView * vec4(world, 1.0);
        }
    """

    const val SUN_FRAG = """
        precision mediump float;
        void main() {
            vec2 c = gl_PointCoord;
            gl_FragColor = vec4(0.93, 0.76, 0.32, 1.0);
        }
    """

    // ---------- 地名标签 billboard（一张 atlas，朝向相机）----------
    const val LABEL_VERT = """
        attribute vec3 aPos;
        attribute vec2 aUv;
        attribute float aFade;
        uniform mat4 uProj;
        uniform mat4 uView;
        uniform float uOpacity;
        varying vec2 vUv;
        varying float vOpacity;
        void main() {
            vUv = aUv;
            vOpacity = uOpacity * aFade;
            gl_Position = uProj * uView * vec4(aPos, 1.0);
        }
    """

    const val LABEL_FRAG = """
        precision mediump float;
        uniform sampler2D uAtlas;
        varying vec2 vUv;
        varying float vOpacity;
        void main() {
            vec4 c = texture2D(uAtlas, vUv);
            if (c.a < 0.05) discard;
            gl_FragColor = vec4(c.rgb, c.a * vOpacity);
        }
    """

    // ---------- 战役回放：彩色 ribbon / 势力色块（切片 18）----------
    const val REPLAY_COLOR_VERT = """
        attribute vec3 aPos;
        attribute vec4 aColor;
        uniform mat4 uProj;
        uniform mat4 uView;
        uniform mat4 uModel;
        varying vec4 vColor;
        void main() {
            vColor = aColor;
            gl_Position = uProj * uView * uModel * vec4(aPos, 1.0);
        }
    """

    const val REPLAY_COLOR_FRAG = """
        precision mediump float;
        varying vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """

    // ---------- 战役回放：流动粒子 / 顶部金点（点精灵）----------
    const val REPLAY_POINT_VERT = """
        attribute vec3 aPos;
        attribute float aSize;
        attribute vec4 aColor;
        uniform mat4 uProj;
        uniform mat4 uView;
        uniform mat4 uModel;
        varying vec4 vColor;
        void main() {
            vColor = aColor;
            vec4 world = uModel * vec4(aPos, 1.0);
            gl_Position = uProj * uView * world;
            gl_PointSize = aSize;
        }
    """

    const val REPLAY_POINT_FRAG = """
        precision mediump float;
        varying vec4 vColor;
        void main() {
            vec2 c = gl_PointCoord - 0.5;
            float d = length(c);
            if (d > 0.5) discard;
            float alpha = smoothstep(0.5, 0.1, d);
            gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);
        }
    """
}
