package io.legado.app.ui.globe.render

import android.opengl.GLES20
import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.math.Mat4
import io.legado.app.ui.globe.core.math.Vec3
import io.legado.app.ui.globe.core.replay.FactionPalette
import io.legado.app.ui.globe.core.replay.marker.GlobeMarker
import io.legado.app.ui.globe.core.scene.GlobeScene
import io.legado.app.ui.globe.gl.ShaderProgram
import io.legado.app.ui.globe.gl.Shaders

/**
 * 战役回放 GL pass（OpenGL ES 2.0 自绘，切片 18）。
 *
 * 职责（在既有 7 个球体 pass 之后叠加，draw call 增量控制在 4 个以内）：
 *  1. 势力色块壳：128×128 网格判点着色，半透明贴球；
 *  2. 行军高圆弧 ribbon：凸起 1.5 单位，阵营配色；
 *  3. 箭头锋芒 + 流动金色粒子：沿弧线方向，尾细锋尖；
 *  4. 城池光针：沿球心→地标法线穿出，顶部金点。
 *
 * 纯逻辑（大圆 / 判点 / 日程 / 回合）在 core.replay 层单测；本类只做几何组装与上传。
 * 注意：GL 编排与着色器需真机 / 模拟器验证。
 */
class ReplayPass {

    private var progColor: ShaderProgram? = null
    private var progPoint: ShaderProgram? = null

    private var powerVbo = 0
    private var powerVertCount = 0
    private var powerBuilt = false

    // 逐帧变化的动态流：ribbon / 箭头 / 粒子 / 光针 / 金点，VBO 与缓冲按容量复用，不逐帧建删。
    private val ribbonBuffer = GlDynamicBuffer()
    private val arrowBuffer = GlDynamicBuffer()
    private val particleBuffer = GlDynamicBuffer()
    private val needleBuffer = GlDynamicBuffer()
    private val dotBuffer = GlDynamicBuffer()

    // ---------- 生命周期 ----------

    fun onSurfaceCreated() {
        progColor = ShaderProgram.build(Shaders.REPLAY_COLOR_VERT, Shaders.REPLAY_COLOR_FRAG)
        progPoint = ShaderProgram.build(Shaders.REPLAY_POINT_VERT, Shaders.REPLAY_POINT_FRAG)
    }

    fun release() {
        if (powerVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(powerVbo), 0)
        powerVbo = 0
        powerBuilt = false
        ribbonBuffer.release()
        arrowBuffer.release()
        particleBuffer.release()
        needleBuffer.release()
        dotBuffer.release()
        progColor?.release()
        progPoint?.release()
        progColor = null
        progPoint = null
    }

    // ---------- 主入口 ----------

    fun draw(proj: Mat4, view: Mat4, model: Mat4, scene: GlobeScene) {
        val radius = scene.radius.toDouble()
        if (!powerBuilt) {
            buildPowerShell(scene)
            powerBuilt = true
        }
        drawPowerShell(proj, view, model)
        drawRoute(proj, view, model, scene, radius)
        drawMarkers(proj, view, model, scene, radius)
    }

    // ---------- 势力色块 ----------

    private fun buildPowerShell(scene: GlobeScene) {
        val quads = scene.replay.powers.shellQuads()
        if (quads.isEmpty()) return
        // 每块四边形 2 三角形 = 6 顶点，每顶点 pos3 + color4 = 7 float
        val data = ArrayList<Float>(quads.size * 6 * 7)
        val r = GlobeConfig.POWER_SHELL_RADIUS.toDouble()
        quads.forEach { quad ->
            val (cr, cg, cb) = FactionPalette.rgb(quad.colorHex)
            val a = quad.alpha
            val corners = quad.corners.map { it * r }
            // 0-1-2, 0-2-3
            val tris = listOf(0, 1, 2, 0, 2, 3)
            tris.forEach { idx ->
                val p = corners[idx]
                data.add(p.x.toFloat()); data.add(p.y.toFloat()); data.add(p.z.toFloat())
                data.add(cr); data.add(cg); data.add(cb); data.add(a)
            }
        }
        val arr = FloatArray(data.size) { data[it] }
        val buf = java.nio.ByteBuffer.allocateDirect(arr.size * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(arr).position(0)
        powerVbo = uploadArrayBuffer(buf)
        powerVertCount = arr.size / 7
    }

    private fun drawPowerShell(proj: Mat4, view: Mat4, model: Mat4) {
        if (powerVbo == 0 || powerVertCount == 0) return
        val prog = progColor ?: return
        prog.use()
        bindCommon(prog, proj, view, model)
        bindColorMesh(prog, powerVbo)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, powerVertCount)
    }

    // ---------- 行军路线 ----------

    private fun drawRoute(proj: Mat4, view: Mat4, model: Mat4, scene: GlobeScene, radius: Double) {
        val route = scene.replay.route() ?: return
        val progress = scene.replay.routeProgress()
        if (progress <= 0.001) return

        val (cr, cg, cb) = FactionPalette.rgb(route.colorHex)
        val points = route.sampledPoints(radius)
        if (points.size < 2) return
        val n = points.size

        // 已完成前缀：t ≤ progress
        val prefix = ArrayList<Vec3>()
        points.forEachIndexed { i, p ->
            val t = i.toDouble() / (n - 1)
            if (t <= progress) prefix.add(p)
        }
        if (prefix.size < 2) return

        // ribbon 顶点：每点左右各一，宽 ARC_WIDTH；尾部略收窄
        val ribbon = ArrayList<Float>()
        for (i in prefix.indices) {
            val p = prefix[i]
            val t = i.toDouble() / (n - 1)
            val tangent = route.sampleTangent(t)
            val radial = p.normalize()
            val side = radial.cross(tangent).normalize()
            if (side.length < 1e-6) return
            val taper = 0.55 + 0.45 * (i.toDouble() / (prefix.size - 1))
            val halfW = GlobeConfig.ARC_WIDTH * taper * 0.5
            val left = p + side * halfW
            val right = p - side * halfW
            putColorVert(ribbon, left, cr, cg, cb, 0.9f)
            putColorVert(ribbon, right, cr, cg, cb, 0.9f)
        }
        drawColorStrip(proj, view, model, ribbon, ribbonBuffer)

        // 箭头锋芒：在进度最前端，三角形尾窄锋尖
        val head = route.arrowHead(progress, radius)
        val side = head.radial.cross(head.tangent).normalize()
        if (side.length > 1e-6) {
            val size = GlobeConfig.ARROW_HEAD_SIZE.toDouble()
            val base = head.position - head.tangent * (size * 0.5)
            val tip = head.position + head.tangent * (size * 0.5)
            val arrow = ArrayList<Float>()
            putColorVert(arrow, base - side * size * 0.35, cr, cg, cb, 1f)
            putColorVert(arrow, base + side * size * 0.35, cr, cg, cb, 1f)
            putColorVert(arrow, tip, cr, cg, cb, 1f)
            drawColorTriangles(proj, view, model, arrow, arrowBuffer)
        }

        // 流动粒子：沿弧线方向向后错落分布
        val particles = ArrayList<Float>()
        val count = GlobeConfig.PARTICLES_PER_ROUTE
        for (k in 0 until count) {
            val t = progress - k * 0.055
            if (t <= 0.0) break
            val p = route.sample(t, radius)
            particles.add(p.x.toFloat()); particles.add(p.y.toFloat()); particles.add(p.z.toFloat())
            particles.add((3.5f - k * 0.15f).coerceAtLeast(1.5f))
            particles.add(cr); particles.add(cg); particles.add(cb); particles.add(0.95f)
        }
        if (particles.isNotEmpty()) drawPoints(proj, view, model, particles, particleBuffer)
    }

    // ---------- 城池光针 ----------

    private fun drawMarkers(proj: Mat4, view: Mat4, model: Mat4, scene: GlobeScene, radius: Double) {
        val markers = scene.replay.markers()
        if (markers.isEmpty()) return
        val (gr, gg, gb) = FactionPalette.rgb("#D4A54A")
        val needles = ArrayList<Float>()
        val dots = ArrayList<Float>()
        markers.forEach { marker ->
            val base = marker.needleBase(radius)
            val tip = marker.needleTip(radius)
            val dot = marker.topDotCenter(radius)
            val radial = marker.direction()
            val side = pickPerpendicular(radial)
            val halfW = 0.12
            val baseL = base - side * halfW
            val baseR = base + side * halfW
            val tipL = tip - side * halfW
            val tipR = tip + side * halfW
            // 每根光针独立两个三角形，避免多条针共用 strip 跨针连线
            putColorVert(needles, baseL, gr, gg, gb, 0.85f)
            putColorVert(needles, baseR, gr, gg, gb, 0.85f)
            putColorVert(needles, tipL, gr, gg, gb, 1f)
            putColorVert(needles, baseR, gr, gg, gb, 0.85f)
            putColorVert(needles, tipR, gr, gg, gb, 1f)
            putColorVert(needles, tipL, gr, gg, gb, 1f)
            dots.add(dot.x.toFloat()); dots.add(dot.y.toFloat()); dots.add(dot.z.toFloat())
            dots.add(GlobeConfig.NEEDLE_TOP_DOT_RADIUS * 3f)
            dots.add(gr); dots.add(gg); dots.add(gb); dots.add(1f)
        }
        if (needles.isNotEmpty()) drawColorTriangles(proj, view, model, needles, needleBuffer)
        if (dots.isNotEmpty()) drawPoints(proj, view, model, dots, dotBuffer)
    }

    // ---------- 绘制工具 ----------

    private fun bindCommon(prog: ShaderProgram, proj: Mat4, view: Mat4, model: Mat4) {
        GLES20.glUniformMatrix4fv(prog.uniform("uProj"), 1, false, proj.m, 0)
        GLES20.glUniformMatrix4fv(prog.uniform("uView"), 1, false, view.m, 0)
        GLES20.glUniformMatrix4fv(prog.uniform("uModel"), 1, false, model.m, 0)
    }

    private fun bindColorMesh(prog: ShaderProgram, vbo: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(prog.attrib("aPos"))
        GLES20.glVertexAttribPointer(prog.attrib("aPos"), 3, GLES20.GL_FLOAT, false, 7 * 4, 0)
        GLES20.glEnableVertexAttribArray(prog.attrib("aColor"))
        GLES20.glVertexAttribPointer(prog.attrib("aColor"), 4, GLES20.GL_FLOAT, false, 7 * 4, 12)
    }

    private fun drawColorStrip(
        proj: Mat4, view: Mat4, model: Mat4,
        verts: ArrayList<Float>, buffer: GlDynamicBuffer,
    ) {
        if (verts.size < 14) return
        val prog = progColor ?: return
        prog.use()
        bindCommon(prog, proj, view, model)
        val arr = FloatArray(verts.size) { verts[it] }
        val vbo = buffer.upload(arr)
        if (vbo == 0) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(prog.attrib("aPos"))
        GLES20.glVertexAttribPointer(prog.attrib("aPos"), 3, GLES20.GL_FLOAT, false, 7 * 4, 0)
        GLES20.glEnableVertexAttribArray(prog.attrib("aColor"))
        GLES20.glVertexAttribPointer(prog.attrib("aColor"), 4, GLES20.GL_FLOAT, false, 7 * 4, 12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, arr.size / 7)
    }

    private fun drawColorTriangles(
        proj: Mat4, view: Mat4, model: Mat4,
        verts: ArrayList<Float>, buffer: GlDynamicBuffer,
    ) {
        if (verts.size < 21) return
        val prog = progColor ?: return
        prog.use()
        bindCommon(prog, proj, view, model)
        val arr = FloatArray(verts.size) { verts[it] }
        val vbo = buffer.upload(arr)
        if (vbo == 0) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(prog.attrib("aPos"))
        GLES20.glVertexAttribPointer(prog.attrib("aPos"), 3, GLES20.GL_FLOAT, false, 7 * 4, 0)
        GLES20.glEnableVertexAttribArray(prog.attrib("aColor"))
        GLES20.glVertexAttribPointer(prog.attrib("aColor"), 4, GLES20.GL_FLOAT, false, 7 * 4, 12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, arr.size / 7)
    }

    private fun drawPoints(
        proj: Mat4, view: Mat4, model: Mat4,
        verts: ArrayList<Float>, buffer: GlDynamicBuffer,
    ) {
        if (verts.isEmpty()) return
        val prog = progPoint ?: return
        prog.use()
        bindCommon(prog, proj, view, model)
        val arr = FloatArray(verts.size) { verts[it] }
        val vbo = buffer.upload(arr)
        if (vbo == 0) return
        val stride = 8 * 4
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(prog.attrib("aPos"))
        GLES20.glVertexAttribPointer(prog.attrib("aPos"), 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(prog.attrib("aSize"))
        GLES20.glVertexAttribPointer(prog.attrib("aSize"), 1, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glEnableVertexAttribArray(prog.attrib("aColor"))
        GLES20.glVertexAttribPointer(prog.attrib("aColor"), 4, GLES20.GL_FLOAT, false, stride, 16)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, arr.size / 8)
    }

    private fun putColorVert(out: ArrayList<Float>, p: Vec3, r: Float, g: Float, b: Float, a: Float) {
        out.add(p.x.toFloat()); out.add(p.y.toFloat()); out.add(p.z.toFloat())
        out.add(r); out.add(g); out.add(b); out.add(a)
    }

    private fun pickPerpendicular(dir: Vec3): Vec3 {
        val fallback = Vec3.UNIT_X
        val ref = if (Math.abs(dir.y) < 0.9) Vec3.UNIT_Y else Vec3.UNIT_X
        val side = ref.cross(dir).normalize()
        return if (side.length < 1e-6) fallback else side
    }

    private fun uploadArrayBuffer(buf: java.nio.Buffer): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buf.capacity() * 4, buf, GLES20.GL_STATIC_DRAW)
        return ids[0]
    }
}
