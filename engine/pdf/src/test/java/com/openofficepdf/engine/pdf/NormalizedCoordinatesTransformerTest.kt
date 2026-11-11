package com.openofficepdf.engine.pdf

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizedCoordinatesTransformerTest {

    @Test
    fun testUserSpaceToNormalized_0Deg() {
        // 页面高度 800 pt，原始文字处于 y=700..750 (靠近顶部)
        val rawBounds = RectF(50f, 700f, 200f, 750f)
        val norm = NormalizedCoordinatesTransformer.userSpaceToNormalized(
            rawUserBounds = rawBounds,
            pageWidthPt = 600f,
            pageHeightPt = 800f,
            rotationDeg = 0
        )
        // 期望 top = 800 - 750 = 50, bottom = 800 - 700 = 100
        assertEquals(50f, norm.left, 0.001f)
        assertEquals(50f, norm.top, 0.001f)
        assertEquals(200f, norm.right, 0.001f)
        assertEquals(100f, norm.bottom, 0.001f)
    }

    @Test
    fun testNormalizedToScreenPixels() {
        val norm = RectF(10f, 20f, 110f, 120f)
        val screen = NormalizedCoordinatesTransformer.normalizedToScreenPixels(
            normalizedBounds = norm,
            zoomScale = 2.0f,
            scrollOffsetX = 10f,
            scrollOffsetY = 20f,
            dpiScale = 1.0f
        )
        // 2x 缩放 - 偏移: left = 10*2 - 10 = 10, top = 20*2 - 20 = 20
        assertEquals(10f, screen.left, 0.001f)
        assertEquals(20f, screen.top, 0.001f)
        assertEquals(210f, screen.right, 0.001f)
        assertEquals(220f, screen.bottom, 0.001f)
    }
}
