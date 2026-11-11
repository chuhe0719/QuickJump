package com.chuhe.quickmax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageSourceDetectorTest {
    @Test
    fun detectPlatforms() {
        assertEquals("淘宝", PageSourceDetector.detect("淘宝待取快递 取件码 8-2-1234 我的淘宝"))
        assertEquals("拼多多", PageSourceDetector.detect("拼多多 驿站自提 91-2-3235"))
        assertEquals("菜鸟", PageSourceDetector.detect("菜鸟驿站 身份码 取件"))
        assertEquals("京东", PageSourceDetector.detect("京东物流 待取件"))
    }

    @Test
    fun noGuessWhenNoClue() {
        assertNull(PageSourceDetector.detect("随便一段文字 8-2-1234"))
        assertNull(PageSourceDetector.detect(""))
    }
}
