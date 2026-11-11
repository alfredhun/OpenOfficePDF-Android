package com.openofficepdf.engine.pdf

import android.graphics.RectF
import com.openofficepdf.core.memory.BitmapPool
import com.openofficepdf.core.model.SessionId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PdfTileRendererTest {

    private lateinit var tileRenderer: PdfTileRenderer

    @Before
    fun setUp() {
        // 创建测试用的无桩 BitmapPool
        val mockBitmapPool = object : BitmapPool {
            override fun acquire(width: Int, height: Int, config: android.graphics.Bitmap.Config): android.graphics.Bitmap {
                throw UnsupportedOperationException("JVM unit test mock")
            }
            override fun release(bitmap: android.graphics.Bitmap) {}
            override fun trim(level: Int) {}
            override fun clear() {}
            override fun poolSizeBytes(): Long = 0L
        }
        tileRenderer = PdfTileRenderer(mockBitmapPool)
    }

    @Test
    fun testComputeVisibleTiles_standardA4() {
        val pageWidthPt = 595.276f
        val pageHeightPt = 841.89f
        val zoomScale = 1.0f // 1pt = 1px

        // 视口覆盖整页
        val viewport = RectF(0f, 0f, 595.276f, 841.89f)
        val tiles = tileRenderer.computeVisibleTiles(
            pageIndex = 0,
            pageWidthPt = pageWidthPt,
            pageHeightPt = pageHeightPt,
            viewportRectPt = viewport,
            zoomScale = zoomScale,
            tileSizePx = 512
        )

        // 595.276 / 512 -> cols 0..1 (2列)
        // 841.89 / 512 -> rows 0..1 (2行)
        // 预期总共覆盖 2x2 = 4 个瓦片
        assertEquals(4, tiles.size)

        val firstTile = tiles[0]
        assertEquals(0, firstTile.first.tileCol)
        assertEquals(0, firstTile.first.tileRow)
        assertEquals(0f, firstTile.second.left, 0.01f)
        assertEquals(0f, firstTile.second.top, 0.01f)
        assertEquals(512f, firstTile.second.right, 0.01f)
        assertEquals(512f, firstTile.second.bottom, 0.01f)
    }

    @Test
    fun testComputeVisibleTiles_partialViewport() {
        val pageWidthPt = 595.276f
        val pageHeightPt = 841.89f
        val zoomScale = 2.0f // 缩放 2x，tile 512px 对应 256pt

        // 局部视口仅在右下角
        val viewport = RectF(300f, 600f, 500f, 800f)
        val tiles = tileRenderer.computeVisibleTiles(
            pageIndex = 0,
            pageWidthPt = pageWidthPt,
            pageHeightPt = pageHeightPt,
            viewportRectPt = viewport,
            zoomScale = zoomScale,
            tileSizePx = 512
        )

        // 300 / 256 = col 1; 500 / 256 = col 1
        // 600 / 256 = row 2; 800 / 256 = row 3
        assertTrue(tiles.isNotEmpty())
        for (tile in tiles) {
            assertTrue(tile.second.left <= viewport.right)
            assertTrue(tile.second.right >= viewport.left)
            assertTrue(tile.second.top <= viewport.bottom)
            assertTrue(tile.second.bottom >= viewport.top)
        }
    }
}
