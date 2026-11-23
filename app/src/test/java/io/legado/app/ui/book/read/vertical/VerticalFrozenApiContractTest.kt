package io.legado.app.ui.book.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 落文 · 冻结 API 契约反射校验（契约索引 §2 竖排引擎 / 总纲三引擎冻结清单）。
 *
 * 冻结签名（不可改）：
 * - `VerticalLayoutEngine.layout(text: String, viewWidthPx: Int, viewHeightPx: Int): VerticalPage`
 * - `VerticalLayoutEngine.hitTest(x: Float, y: Float): CharHit?`
 * - `VerticalLayoutEngine.invalidateCache()`
 * - 模型构造：`VerticalPage(columns, pageIndex, progress)` / `Column(cells, left, top)` /
 *   `CharCell(char, offset, x, y, width, height)` / `CharHit(char, offset, paragraphText, pos)`
 *
 * 本测试在纯 JVM 内用反射校验方法/构造器参数类型与字段名；下游 U5 阅读页壳按此对接，
 * 任一签名被改动，本测试即失败。
 */
class VerticalFrozenApiContractTest {

    private fun method(clazz: Class<*>, name: String, vararg params: Class<*>): Method =
        clazz.getMethod(name, *params)

    private fun <T> constructor(clazz: Class<T>, vararg params: Class<*>): Constructor<T> =
        clazz.getConstructor(*params)

    @Test
    fun `engine frozen methods keep exact signatures`() {
        val engine = VerticalLayoutEngine::class.java

        val layout = method(engine, "layout", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        assertEquals(VerticalPage::class.java, layout.returnType)

        val hitTest = method(engine, "hitTest", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        assertEquals(CharHit::class.java, hitTest.returnType)

        val invalidate = method(engine, "invalidateCache")
        assertEquals(Void.TYPE, invalidate.returnType)
    }

    @Test
    fun `verticalPage constructor keeps frozen order and types`() {
        val ctor = constructor(
            VerticalPage::class.java,
            List::class.java,
            Int::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        )
        assertNotNull(ctor)
        assertFields(VerticalPage::class.java, "columns", "pageIndex", "progress")
    }

    @Test
    fun `column constructor keeps frozen order and types`() {
        val ctor = constructor(
            Column::class.java,
            List::class.java,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        )
        assertNotNull(ctor)
        assertFields(Column::class.java, "cells", "left", "top")
    }

    @Test
    fun `charCell constructor keeps frozen order and types`() {
        val ctor = constructor(
            CharCell::class.java,
            Character.TYPE,
            Int::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
        )
        assertNotNull(ctor)
        assertFields(CharCell::class.java, "char", "offset", "x", "y", "width", "height")
    }

    @Test
    fun `charHit constructor keeps frozen order and types`() {
        val ctor = constructor(
            CharHit::class.java,
            Character.TYPE,
            Int::class.javaPrimitiveType,
            String::class.java,
            Int::class.javaPrimitiveType,
        )
        assertNotNull(ctor)
        assertFields(CharHit::class.java, "char", "offset", "paragraphText", "pos")
    }

    private fun assertFields(clazz: Class<*>, vararg names: String) {
        val fieldNames = clazz.declaredFields.map { it.name }.toSet()
        names.forEach { name ->
            assertTrue("${clazz.simpleName} 缺少冻结字段 $name", fieldNames.contains(name))
        }
    }
}
