package io.legado.app.ui.globe.gl

import android.opengl.GLES20

/**
 * 着色器程序封装：编译 / 链接 / 属性与 uniform 位置缓存。
 * 编译失败返回 null，渲染层降级跳过该 pass，不崩溃（规格：边界必须兜底）。
 */
class ShaderProgram private constructor(val program: Int) {

    private val attribCache = HashMap<String, Int>()
    private val uniformCache = HashMap<String, Int>()

    fun use() = GLES20.glUseProgram(program)

    fun attrib(name: String): Int = attribCache.getOrPut(name) {
        GLES20.glGetAttribLocation(program, name)
    }

    fun uniform(name: String): Int = uniformCache.getOrPut(name) {
        GLES20.glGetUniformLocation(program, name)
    }

    fun release() {
        if (program != 0) GLES20.glDeleteProgram(program)
    }

    companion object {
        fun build(vertSrc: String, fragSrc: String): ShaderProgram? {
            val vert = compile(GLES20.GL_VERTEX_SHADER, vertSrc) ?: return null
            val frag = compile(GLES20.GL_FRAGMENT_SHADER, fragSrc) ?: return null
            val program = GLES20.glCreateProgram()
            if (program == 0) return null
            GLES20.glAttachShader(program, vert)
            GLES20.glAttachShader(program, frag)
            GLES20.glLinkProgram(program)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
            if (linked[0] == 0) {
                GLES20.glDeleteProgram(program)
                return null
            }
            return ShaderProgram(program)
        }

        private fun compile(type: Int, src: String): Int? {
            val shader = GLES20.glCreateShader(type)
            if (shader == 0) return null
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                GLES20.glDeleteShader(shader)
                return null
            }
            return shader
        }
    }
}
