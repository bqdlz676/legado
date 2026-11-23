package io.legado.app.ui.globe.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 可复用的动态顶点缓冲（GL 层，OpenGL ES 2.0）。
 *
 * 逐帧变化的 ribbon / 箭头 / 粒子 / 标签不再每帧 `glGenBuffers` + `glDeleteBuffers`
 * （GPU 分配抖动叠加 GC 掉帧）；缓冲按容量复用：容量不足才扩容重建，
 * 容量足够时走 `glBufferSubData` 原地更新。
 */
internal class GlDynamicBuffer {

    private var vbo = 0
    private var capacity = 0
    private var buf: java.nio.FloatBuffer? = null

    /** 上传一帧顶点数据，返回绑定用 VBO id；空数组返回 0。 */
    fun upload(data: FloatArray): Int {
        if (data.isEmpty()) return 0
        val existing = buf
        if (vbo == 0 || existing == null || capacity < data.size) {
            capacity = data.size
            val b = ByteBuffer.allocateDirect(capacity * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            b.put(data).position(0)
            buf = b
            if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
            val ids = IntArray(1)
            GLES20.glGenBuffers(1, ids, 0)
            vbo = ids[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, capacity * 4, b, GLES20.GL_DYNAMIC_DRAW)
        } else {
            existing.position(0)
            existing.put(data).position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, data.size * 4, existing)
        }
        return vbo
    }

    fun release() {
        if (vbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        vbo = 0
        capacity = 0
        buf = null
    }
}
