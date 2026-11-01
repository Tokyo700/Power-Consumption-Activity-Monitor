package com.aditya.powermonitor.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun `plain values are written as is`() {
        assertEquals("YouTube", Csv.escape("YouTube"))
        assertEquals("42", Csv.escape(42))
    }

    @Test
    fun `null becomes an empty field`() {
        assertEquals("", Csv.escape(null))
    }

    @Test
    fun `a comma forces quotes`() {
        assertEquals("\"Maps, Navigation\"", Csv.escape("Maps, Navigation"))
    }

    @Test
    fun `quotes inside are doubled`() {
        assertEquals("\"The \"\"Best\"\" App\"", Csv.escape("The \"Best\" App"))
    }

    @Test
    fun `line breaks force quotes`() {
        assertEquals("\"two\nlines\"", Csv.escape("two\nlines"))
    }

    @Test
    fun `a line joins escaped fields with commas`() {
        assertEquals("a,\"b,c\",,3", Csv.line(listOf("a", "b,c", null, 3)))
    }
}
