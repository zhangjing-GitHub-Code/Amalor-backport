package me.acardia.amalor.xposed.systemui

internal object BatteryTextGeometry {
    /** Horizontal battery bodies and their detached terminals occupy separate alpha columns. */
    fun bodyBounds(pixels: IntArray, width: Int, height: Int): IntArray? {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
        var start = -1
        var top = height
        var bottom = 0
        var best: IntArray? = null
        for (x in 0..width) {
            var columnTop = height
            var columnBottom = 0
            if (x < width) for (y in 0 until height) {
                if (pixels[y * width + x] ushr 24 != 0) {
                    columnTop = minOf(columnTop, y)
                    columnBottom = y + 1
                }
            }
            if (columnBottom > columnTop) {
                if (start < 0) start = x
                top = minOf(top, columnTop)
                bottom = maxOf(bottom, columnBottom)
            } else if (start >= 0) {
                if (best == null || x - start > best[2] - best[0]) best = intArrayOf(start, top, x, bottom)
                start = -1
                top = height
                bottom = 0
            }
        }
        return best
    }

    /** The OEM's oversized-text branch negates this offset in RTL. */
    fun centerOffset(contentCenter: Float, bodyCenter: Float, rtl: Boolean): Float =
        (contentCenter - bodyCenter) * if (rtl) -1f else 1f
}
