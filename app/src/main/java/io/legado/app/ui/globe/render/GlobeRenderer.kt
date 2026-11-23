package io.legado.app.ui.globe.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.geo.Geo
import io.legado.app.ui.globe.core.math.Mat4
import io.legado.app.ui.globe.core.math.Vec3
import io.legado.app.ui.globe.core.mesh.LodLevel
import io.legado.app.ui.globe.core.mesh.SphereMesh
import io.legado.app.ui.globe.core.scene.GlobeScene
import io.legado.app.ui.globe.core.terrain.TerrainSource
import io.legado.app.ui.globe.gl.ShaderProgram
import io.legado.app.ui.globe.gl.Shaders
import io.legado.app.ui.globe.gl.TextureFactory
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * 地球仪 GL 渲染器（OpenGL ES 2.0，自绘，无第三方渲染框架）。
 *
 * Pass 顺序（切片 19 / 20，draw call ≤15）：
 *  1. 星空球（半径 900，点精灵，固定不动）
 *  2. 太阳圆盘（直径 8，距离 400，金色辉光）
 *  3. 大气外圈光晕（半径 104，alpha 0.08）
 *  4. 云层壳（半径 101，自转 ×0.9，alpha ×0.3）
 *  5. 地球表面（昼夜 / 海洋 / 冰盖 / 雪线 / 云影 / 晨昏渐变）
 *  6. 大气内圈菲涅尔壳（半径 100.5）
 *  7. 地名标签 billboard（单张 atlas）
 *
 * 注意：GL 编排与 shader 需真机 / 模拟器验证；几何、LOD、分类、雪线、光照等纯逻辑在 core 层单测。
 */
class GlobeRenderer(
    val scene: GlobeScene = GlobeScene(),
) : GLSurfaceView.Renderer {

    /** 首帧渲染完成回调（供 View 通知上层数据就位）。 */
    var onReady: (() -> Unit)? = null

    private var progSurface: ShaderProgram? = null
    private var progAtmos: ShaderProgram? = null
    private var progAtmosGlow: ShaderProgram? = null
    private var progCloud: ShaderProgram? = null
    private var progStar: ShaderProgram? = null
    private var progSun: ShaderProgram? = null
    private var progLabel: ShaderProgram? = null

    private var texDay = 0
    private var texNight = 0
    private var texCover = 0
    private var texCloud = 0
    private var atlasTex = 0

    private val meshCache = HashMap<LodLevel, GlMesh>()
    private var cloudMesh: GlMesh? = null
    private var innerShell: GlMesh? = null
    private var outerShell: GlMesh? = null
    private var starVbo = 0
    private var starCount = 0
    private var atlas: AtlasLabels? = null
    private val replayPass = ReplayPass()

    /** 地形采样源：把分类后的沿法线位移喂给球体顶点（7 种地表 / 雪线 / fbm 森林凸起）。 */
    private val terrainSource = TerrainSource()

    /** 太阳盘四角为常量，VBO 一次性建好不逐帧建删。 */
    private var sunVbo = 0

    /** 标签 billboard 逐帧重组位置，但 VBO 与缓冲按容量复用，不逐帧 glGen/Delete。 */
    private val labelBuffer = GlDynamicBuffer()

    private var viewportW = 1
    private var viewportH = 1
    private var lastFrameMs = 0L
    private var readyFired = false

    private val proj = Mat4.identity()
    private val view = Mat4.identity()

    // ---------- 生命周期 ----------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.071f, 0.063f, 0.055f, 1f) // 暖墨底 #12100E，不用大片靛蓝/紫
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        progSurface = ShaderProgram.build(Shaders.SURFACE_VERT, Shaders.SURFACE_FRAG)
        progAtmos = ShaderProgram.build(Shaders.ATMOS_VERT, Shaders.ATMOS_FRAG)
        progAtmosGlow = ShaderProgram.build(Shaders.ATMOS_VERT, Shaders.ATMOS_GLOW_FRAG)
        progCloud = ShaderProgram.build(Shaders.CLOUD_VERT, Shaders.CLOUD_FRAG)
        progStar = ShaderProgram.build(Shaders.STAR_VERT, Shaders.STAR_FRAG)
        progSun = ShaderProgram.build(Shaders.SUN_VERT, Shaders.SUN_FRAG)
        progLabel = ShaderProgram.build(Shaders.LABEL_VERT, Shaders.LABEL_FRAG)
        replayPass.onSurfaceCreated()

        texDay = TextureFactory.uploadTexture(TextureFactory.dayTexture())
        texNight = TextureFactory.uploadTexture(TextureFactory.nightTexture())
        texCover = TextureFactory.uploadTexture(TextureFactory.coverTexture())
        texCloud = TextureFactory.uploadTexture(TextureFactory.cloudTexture())

        buildStarBuffer()
        buildSunBuffer()
        atlas = AtlasLabels.build()
        atlasTex = TextureFactory.uploadTexture(atlas?.bitmap)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportW, viewportH)
        val aspect = viewportW.toDouble() / viewportH
        proj.multiply(
            Mat4.perspective(Math.toRadians(45.0), aspect, 1f, 2000f),
            Mat4.identity(),
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.currentTimeMillis()
        val dt = if (lastFrameMs == 0L) 0.016 else ((now - lastFrameMs) / 1000.0).coerceIn(0.001, 0.05)
        lastFrameMs = now

        scene.update(dt, now)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val eye = cameraEye()
        view.multiply(Mat4.lookAt(eye, Vec3.ZERO, Vec3.UNIT_Y), Mat4.identity())

        val model = modelMatrix()

        drawStars()
        drawSun()
        drawAtmosphereGlow(model)
        drawClouds(model)
        drawSurface(model, eye)
        drawAtmosphereInner(model, eye)
        replayPass.draw(proj, view, model, scene)
        drawLabels(model)

        if (!readyFired) {
            readyFired = true
            onReady?.invoke()
        }
    }

    fun release() {
        meshCache.values.forEach { it.release() }
        meshCache.clear()
        cloudMesh?.release(); innerShell?.release(); outerShell?.release()
        if (starVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(starVbo), 0)
        if (sunVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(sunVbo), 0)
        labelBuffer.release()
        if (atlasTex != 0) GLES20.glDeleteTextures(1, intArrayOf(atlasTex), 0)
        atlas?.release()
        progSurface?.release(); progAtmos?.release(); progAtmosGlow?.release()
        progCloud?.release(); progStar?.release(); progSun?.release(); progLabel?.release()
        replayPass.release()
    }

    // ---------- 相机 / 模型 ----------

    private fun cameraEye(): Vec3 {
        val yaw = scene.rotator.yawDeg * Geo.DEG2RAD
        val pitch = scene.rotator.pitchDeg * Geo.DEG2RAD
        val d = scene.cameraDistance
        val cp = cos(pitch)
        return Vec3(
            d * sin(yaw) * cp,
            d * sin(pitch),
            d * cos(yaw) * cp,
        )
    }

    private fun modelMatrix(): Mat4 {
        val tilt = Mat4.rotationX(GlobeConfig.AXIAL_TILT_DEG * Geo.DEG2RAD)
        val yaw = Mat4.rotationY(scene.rotator.yawDeg * Geo.DEG2RAD)
        val pitch = Mat4.rotationX(scene.rotator.pitchDeg * Geo.DEG2RAD)
        val m = Mat4.identity()
        m.multiply(tilt, yaw)
        val out = Mat4.identity()
        out.multiply(m, pitch)
        return out
    }

    // ---------- Mesh ----------

    private fun meshFor(level: LodLevel): GlMesh? {
        meshCache[level]?.let { return it }
        // 顶点位移接地形分类器：水域贴球，陆地按高程基准起伏，森林叠加 fbm 树冠凸起，
        // 雪线 >3500m / 极地冰盖由分类器判定；位移沿球面法线方向叠加。
        val mesh = SphereMesh.build(level, scene.radius) { lat, lng, _, _, _ ->
            terrainSource.sample(lat, lng).displacementUnits
        }
        val glMesh = GlMesh.upload(mesh)
        meshCache[level] = glMesh
        return glMesh
    }

    private fun cloudMesh(): GlMesh {
        cloudMesh?.let { return it }
        val mesh = SphereMesh.build(
            GlobeConfig.CLOUD_SEGMENTS,
            GlobeConfig.CLOUD_SEGMENTS,
            GlobeConfig.CLOUD_RADIUS,
        )
        return GlMesh.upload(mesh).also { cloudMesh = it }
    }

    private fun shellMesh(radius: Float, cache: GlMesh?): GlMesh {
        cache?.let { return it }
        val mesh = SphereMesh.build(
            GlobeConfig.CLOUD_SEGMENTS,
            GlobeConfig.CLOUD_SEGMENTS,
            radius,
        )
        return GlMesh.upload(mesh)
    }

    // ---------- Passes ----------

    private fun drawSurface(model: Mat4, eye: Vec3) {
        val prog = progSurface ?: return
        val mesh = meshFor(scene.currentLod) ?: return
        prog.use()

        bindMesh(prog, mesh)

        GLES20.glUniformMatrix4fv(prog.uniform("uProj"), 1, false, proj.m, 0)
        GLES20.glUniformMatrix4fv(prog.uniform("uView"), 1, false, view.m, 0)
        GLES20.glUniformMatrix4fv(prog.uniform("uModel"), 1, false, model.m, 0)
        GLES20.glUniform3f(
            prog.uniform("uSunDir"),
            scene.sunDir.x.toFloat(), scene.sunDir.y.toFloat(), scene.sunDir.z.toFloat(),
        )
        GLES20.glUniform3f(
            prog.uniform("uCameraPos"),
            eye.x.toFloat(), eye.y.toFloat(), eye.z.toFloat(),
        )

        bindTexture(prog, "uDayTex", 0, texDay)
        bindTexture(prog, "uNightTex", 1, texNight)
        bindTexture(prog, "uCoverTex", 2, texCover)
        bindTexture(prog, "uCloudTex", 3, texCloud)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    private fun drawClouds(model: Mat4) {
        val prog = progCloud ?: return
        val mesh = cloudMesh()
        prog.use()
        GLES20.glUniformMatrix4fv(prog.uniform("uProj"), 1, false, proj.m, 0)
        GLES20.glUniformMatrix4fv(prog.uniform("uView"), 1, false, view.m, 0)
        val spin = Mat4.rotationY(scene.cloudRotationDeg * Geo.DEG2RAD)
        val cloudModel = Mat4.identity()
        cloudModel.multiply(model, spin)
        GLES20.glUniformMatrix4fv(prog.uniform("uModel"), 1, false, cloudModel.m, 0)
        bindTexture(prog, "uCloudTex", 0, texCloud)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboPos)
        GLES20.glEnableVertexAttribArray(prog.attrib("aPos"))
        GLES20.glVertexAttribPointer(prog.attrib("aPos"), 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboUv)
        GLES20.glEnableVertexAttribArray(prog.attrib("aUv"))
        GLES20.glVertexAttribPointer(prog.attrib("aUv"), 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    private fun drawAtmosphereInner(model: Mat4, eye: Vec3) {
        val mesh = innerShell ?: shellMesh(GlobeConfig.ATMOS_INNER_RADIUS, null).also { innerShell = it }
        drawAtmosShell(progAtmos, model, eye, mesh)
    }

    private fun drawAtmosphereGlow(model: Mat4) {
        val mesh = outerShell ?: shellMesh(GlobeConfig.ATMOS_OUTER_RADIUS, null).also { outerShell = it }
        drawAtmosShell(progAtmosGlow, model, Vec3.ZERO, mesh)
    }

    private fun drawAtmosShell(prog: ShaderProgram?, model: Mat4, eye: Vec3, mesh: GlMesh) {
        prog ?: return
        prog.use()
        GLES20.glUniformMatrix4fv(prog.uniform("uProj"), 1, false, proj.m, 0)
        GLES20.glUniformMatrix4fv(prog.uniform("uView"), 1, false, view.m, 0)
        GLES20.glUniformMatrix4fv(prog.uniform("uModel"), 1, false, model.m, 0)
        GLES20.glUniform3f(
            prog.uniform("uCameraPos"),
            eye.x.toFloat(), eye.y.toFloat(), eye.z.toFloat(),
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboPos)
        GLES20.glEnableVertexAttribArray(prog.attrib("aPos"))
        GLES20.glVertexAttribPointer(prog.attrib("aPos"), 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboNormal)
        GLES20.glEnableVertexAttribArray(prog.attrib("aNormal"))
        GLES20.glVertexAttribPointer(prog.attrib("aNormal"), 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    private fun drawStars() {
        val prog = progStar ?: return
        prog.use()
        GLES20.glUniformMatrix4fv(prog.uniform("uProj"), 1, false, proj.m, 0)
        GLES20.glUniformMatrix4fv(prog.uniform("uView"), 1, false, view.m, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, starVbo)
        val stride = 8 * 4
        GLES20.glEnableVertexAttribArray(prog.attrib("aPos"))
        GLES20.glVertexAttribPointer(prog.attrib("aPos"), 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(prog.attrib("aSize"))
        GLES20.glVertexAttribPointer(prog.attrib("aSize"), 1, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glEnableVertexAttribArray(prog.attrib("aColor"))
        GLES20.glVertexAttribPointer(prog.attrib("aColor"), 4, GLES20.GL_FLOAT, false, stride, 16)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount)
    }

    private fun drawSun() {
        val prog = progSun ?: return
        val center = scene.sunPosition()
        val right = Vec3.UNIT_Y.cross(center).normalize()
        val up = center.cross(right).normalize()
        prog.use()
        GLES20.glUniformMatrix4fv(prog.uniform("uProj"), 1, false, proj.m, 0)
        GLES20.glUniformMatrix4fv(prog.uniform("uView"), 1, false, view.m, 0)
        GLES20.glUniform3f(prog.uniform("uCenter"), center.x.toFloat(), center.y.toFloat(), center.z.toFloat())
        GLES20.glUniform3f(prog.uniform("uCameraRight"), right.x.toFloat(), right.y.toFloat(), right.z.toFloat())
        GLES20.glUniform3f(prog.uniform("uCameraUp"), up.x.toFloat(), up.y.toFloat(), up.z.toFloat())
        GLES20.glUniform1f(prog.uniform("uRadius"), GlobeConfig.SUN_DISC_DIAMETER / 2f)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sunVbo)
        GLES20.glEnableVertexAttribArray(prog.attrib("aCorner"))
        GLES20.glVertexAttribPointer(prog.attrib("aCorner"), 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawLabels(model: Mat4) {
        val prog = progLabel ?: return
        val atlasBundle = atlas ?: return
        val visible = scene.visiblePlaces().filter { it.second > 0.01f }
        if (visible.isEmpty()) return

        // 每个地名一个 billboard 四边形；单 atlas 单次 draw call。
        // 顶点布局：pos3 + uv2 + fade1；淡出系数逐顶点传入 shader，不再被丢弃。
        val verts = ArrayList<Float>(visible.size * 4 * 6)
        visible.forEach { (place, fade) ->
            val dir = Geo.direction(place.lat, place.lng)
            val worldDir = model.transformPoint(dir).normalize()
            val pos = worldDir * (scene.radius * 1.01)
            val entry = atlasBundle.entries.firstOrNull { it.place.name == place.name } ?: return@forEach
            val halfW = 2.2f
            val halfH = 1.1f
            val right = Vec3.UNIT_Y.cross(worldDir).normalize()
            val up = worldDir.cross(right).normalize()
            val quads = arrayOf(
                pos - right * halfW - up * halfH to (entry.u0 to entry.v1),
                pos + right * halfW - up * halfH to (entry.u1 to entry.v1),
                pos - right * halfW + up * halfH to (entry.u0 to entry.v0),
                pos + right * halfW + up * halfH to (entry.u1 to entry.v0),
            )
            for ((p, uv) in quads) {
                verts.add(p.x.toFloat()); verts.add(p.y.toFloat()); verts.add(p.z.toFloat())
                verts.add(uv.first); verts.add(uv.second); verts.add(fade)
            }
        }
        if (verts.isEmpty()) return

        val arr = FloatArray(verts.size) { verts[it] }
        val id = labelBuffer.upload(arr)
        if (id == 0) return

        prog.use()
        GLES20.glUniformMatrix4fv(prog.uniform("uProj"), 1, false, proj.m, 0)
        GLES20.glUniformMatrix4fv(prog.uniform("uView"), 1, false, view.m, 0)
        GLES20.glUniform1f(prog.uniform("uOpacity"), 1f)
        bindTexture(prog, "uAtlas", 0, atlasTex)

        val stride = 6 * 4
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, id)
        GLES20.glEnableVertexAttribArray(prog.attrib("aPos"))
        GLES20.glVertexAttribPointer(prog.attrib("aPos"), 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(prog.attrib("aUv"))
        GLES20.glVertexAttribPointer(prog.attrib("aUv"), 2, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glEnableVertexAttribArray(prog.attrib("aFade"))
        GLES20.glVertexAttribPointer(prog.attrib("aFade"), 1, GLES20.GL_FLOAT, false, stride, 20)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, arr.size / 6)
    }

    // ---------- 工具 ----------

    private fun bindMesh(prog: ShaderProgram, mesh: GlMesh) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboPos)
        GLES20.glEnableVertexAttribArray(prog.attrib("aPos"))
        GLES20.glVertexAttribPointer(prog.attrib("aPos"), 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboNormal)
        GLES20.glEnableVertexAttribArray(prog.attrib("aNormal"))
        GLES20.glVertexAttribPointer(prog.attrib("aNormal"), 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboUv)
        GLES20.glEnableVertexAttribArray(prog.attrib("aUv"))
        GLES20.glVertexAttribPointer(prog.attrib("aUv"), 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.ibo)
    }

    private fun bindTexture(prog: ShaderProgram, name: String, unit: Int, texId: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(prog.uniform(name), unit)
    }

    private fun buildStarBuffer() {
        val stars = scene.starField.stars
        val data = FloatArray(stars.size * 8)
        stars.forEachIndexed { i, star ->
            val p = star.direction * GlobeConfig.STAR_RADIUS
            data[i * 8] = p.x.toFloat()
            data[i * 8 + 1] = p.y.toFloat()
            data[i * 8 + 2] = p.z.toFloat()
            data[i * 8 + 3] = star.sizePx
            val (r, g, b) = if (star.gold) {
                Triple(
                    GlobeConfig.GLOW_RED / 255f,
                    GlobeConfig.GLOW_GREEN / 255f,
                    GlobeConfig.GLOW_BLUE / 255f,
                )
            } else {
                Triple(0.92f, 0.94f, 1f)
            }
            data[i * 8 + 4] = r
            data[i * 8 + 5] = g
            data[i * 8 + 6] = b
            data[i * 8 + 7] = star.brightness
        }
        val buf = java.nio.ByteBuffer.allocateDirect(data.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(data).position(0)
        starVbo = uploadArrayBuffer(buf)
        starCount = stars.size
    }

    private fun buildSunBuffer() {
        val corners = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val buf = java.nio.ByteBuffer.allocateDirect(corners.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(corners).position(0)
        sunVbo = uploadArrayBuffer(buf)
    }

    private fun uploadArrayBuffer(buf: java.nio.Buffer): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buf.capacity() * 4, buf, GLES20.GL_STATIC_DRAW)
        return ids[0]
    }

    /** GL 网格 VBO/IBO 持有者。 */
    private class GlMesh(
        val vboPos: Int,
        val vboNormal: Int,
        val vboUv: Int,
        val ibo: Int,
        val indexCount: Int,
    ) {
        fun release() {
            GLES20.glDeleteBuffers(1, intArrayOf(vboPos, vboNormal, vboUv, ibo), 0)
        }

        companion object {
            fun upload(mesh: SphereMesh): GlMesh {
                val pos = java.nio.ByteBuffer.allocateDirect(mesh.positions.size * 4)
                    .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
                pos.put(mesh.positions).position(0)
                val nor = java.nio.ByteBuffer.allocateDirect(mesh.normals.size * 4)
                    .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
                nor.put(mesh.normals).position(0)
                val uv = java.nio.ByteBuffer.allocateDirect(mesh.uvs.size * 4)
                    .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
                uv.put(mesh.uvs).position(0)

                val ids = IntArray(4)
                GLES20.glGenBuffers(4, ids, 0)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, pos.capacity() * 4, pos, GLES20.GL_STATIC_DRAW)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[1])
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, nor.capacity() * 4, nor, GLES20.GL_STATIC_DRAW)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[2])
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, uv.capacity() * 4, uv, GLES20.GL_STATIC_DRAW)

                val short = ShortArray(mesh.indices.size) { mesh.indices[it].toShort() }
                val ibuf = java.nio.ByteBuffer.allocateDirect(short.size * 2)
                    .order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                ibuf.put(short).position(0)
                GLES20.glGenBuffers(1, ids, 3)
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ids[3])
                GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, short.size * 2, ibuf, GLES20.GL_STATIC_DRAW)

                return GlMesh(ids[0], ids[1], ids[2], ids[3], mesh.indices.size)
            }
        }
    }
}
