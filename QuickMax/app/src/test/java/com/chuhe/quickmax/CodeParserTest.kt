package com.chuhe.quickmax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeParserTest {
    @Test
    fun standardCodes() {
        assertEquals(listOf("8-2-1234"), CodeParser.parse("您的取件码 8-2-1234 请尽快领取"))
        assertEquals(listOf("12-34-5678"), CodeParser.parse("取件码：12-34-5678"))
    }

    @Test
    fun multipleAndDedup() {
        assertEquals(
            listOf("8-2-1234", "3-1-9021"),
            CodeParser.parse("包裹1 8-2-1234\n包裹2 3-1-9021\n重复 8-2-1234")
        )
        assertEquals(
            listOf("29-3-4304", "35-3-4305", "89-1-5104", "33-4-5415", "12-2-7788", "7-1-3306", "5-9-4412"),
            CodeParser.parse(
                "29-3-4304\n35-3-4305\n89-1-5104\n33-4-5415\n12-2-7788\n7-1-3306\n5-9-4412"
            )
        )
    }

    @Test
    fun fullWidthAndOcrNoise() {
        assertEquals(listOf("8-2-1234"), CodeParser.parse("取件码 ８－２－１２３４"))
        assertEquals(listOf("8-2-1234"), CodeParser.parse("取件码 8~2—1234"))
        assertEquals(listOf("8-2-1234"), CodeParser.parse("取件码 8 2 1234"))
    }

    @Test
    fun spaceAsSeparator() {
        assertEquals(listOf("29-3-4304"), CodeParser.parse("取件码 29 3 4304 请取件"))
        assertEquals(
            listOf("29-3-4304", "35-3-4305"),
            CodeParser.parse("29 3 4304\n35 3 4305")
        )
        assertEquals(listOf("29-3-4304"), CodeParser.parse("29-3 4304"))
        assertEquals(listOf("100-2-3138"), CodeParser.parse("驿站 100 2 3138"))
    }

    @Test
    fun letterPrefixedCodes() {
        assertEquals(listOf("A-5-3464"), CodeParser.parse("取件码 A-5-3464 请取件"))
        assertEquals(listOf("A-5-3464"), CodeParser.parse("a 5 3464"))
        assertEquals(listOf("B-12-3088"), CodeParser.parse("B-12-3088"))
        assertEquals(listOf("A-5-3464"), CodeParser.parse("Ａ－５－３４６４"))
        assertEquals("A-5-3464", CodeParser.canonicalManual("A 5 3464"))
        assertEquals(
            listOf("A-5-3464", "29-3-4304"),
            CodeParser.splitManual("A-5-3464 29 3 4304")
        )
        assertTrue(CodeParser.parse("abc def ghij").isEmpty())
        assertTrue(CodeParser.parse("the and for you").isEmpty())
    }

    @Test
    fun rejectsNoise() {
        assertTrue(CodeParser.parse("话费账单 2026-11-06 金额 400-820-8820").isEmpty())
        assertTrue(CodeParser.parse("快递单号 2024102212345678901").isEmpty())
        assertTrue(CodeParser.parse("验证码 778899").isEmpty())
        assertTrue(CodeParser.parse("时间 11-06 17:16").isEmpty())
    }

    @Test
    fun manualInput() {
        assertEquals("8-2-1234", CodeParser.canonicalManual("８－２－１２３４"))
        assertEquals("29-3-4304", CodeParser.canonicalManual("29 3 4304"))
        assertEquals("1234", CodeParser.canonicalManual("1234"))
        assertEquals(listOf("8-2-1234", "1234"), CodeParser.splitManual("8-2-1234 1234"))
        assertEquals(
            listOf("29-3-4304", "35-3-4305"),
            CodeParser.splitManual("29 3 4304 35 3 4305")
        )
        assertEquals(listOf("29-3-4304", "4305"), CodeParser.splitManual("29 3 4304 4305"))
    }
}
