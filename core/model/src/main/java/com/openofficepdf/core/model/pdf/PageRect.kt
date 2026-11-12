package com.openofficepdf.core.model.pdf

import android.graphics.RectF

/**
 * 纯 Kotlin 实现的页面矩形模型 ( Normalized Page Space / User Space )
 * 零依赖跨平台高精度几何计算，提供与 Android RectF 的双向无缝转换。
 */
data class PageRect(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isEmpty: Boolean get() = left >= right || top >= bottom

    fun sort() {
        if (left > right) {
            val t = left
            left = right
            right = t
        }
        if (top > bottom) {
            val t = top
            top = bottom
            bottom = t
        }
    }

    fun intersects(other: PageRect): Boolean {
        return left < other.right && other.left < right && top < other.bottom && other.top < bottom
    }

    fun toRectF(): RectF = createRectF(left, top, right, bottom)

    companion object {
        fun fromRectF(r: RectF): PageRect = PageRect(r.left, r.top, r.right, r.bottom)
        fun intersects(a: PageRect, b: PageRect): Boolean = a.intersects(b)
    }
}

fun RectF.toPageRect(): PageRect = PageRect.fromRectF(this)

/**
 * 安全的 RectF 工厂函数：直接给 public 字段赋值，彻底规避 JVM 单元测试中 android.jar Stub 构造函数不执行的问题
 */
fun createRectF(left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f): RectF {
    val r = RectF()
    r.left = left
    r.top = top
    r.right = right
    r.bottom = bottom
    return r
}

fun createRectF(source: RectF): RectF {
    return createRectF(source.left, source.top, source.right, source.bottom)
}

val RectF.safeWidth: Float get() = right - left
val RectF.safeHeight: Float get() = bottom - top
val RectF.isSafeEmpty: Boolean get() = left >= right || top >= bottom

fun RectF.safeSort() {
    if (left > right) {
        val t = left
        left = right
        right = t
    }
    if (top > bottom) {
        val t = top
        top = bottom
        bottom = t
    }
}

fun RectF.safeIntersects(other: RectF): Boolean {
    return left < other.right && other.left < right && top < other.bottom && other.top < bottom
}
