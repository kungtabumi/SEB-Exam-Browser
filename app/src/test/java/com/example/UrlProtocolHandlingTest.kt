package com.example

import com.example.security.QrPayloadParser
import com.example.security.UrlSecurityValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlProtocolHandlingTest {

    @Test
    fun testNormalizeUrl_PreservesExplicitHttp() {
        val input = "http://192.168.1.100:8080"
        val result = UrlSecurityValidator.normalizeUrl(input)
        assertEquals("http://192.168.1.100:8080", result)
    }

    @Test
    fun testNormalizeUrl_PreservesCaseInsensitiveHttp() {
        val input = "HTTP://cbt.sman1kepanjen.local/exam"
        val result = UrlSecurityValidator.normalizeUrl(input)
        assertEquals("http://cbt.sman1kepanjen.local/exam", result)
    }

    @Test
    fun testNormalizeUrl_PreservesExplicitHttps() {
        val input = "https://moodle.sman1kepanjen.sch.id"
        val result = UrlSecurityValidator.normalizeUrl(input)
        assertEquals("https://moodle.sman1kepanjen.sch.id", result)
    }

    @Test
    fun testNormalizeUrl_ConvertsSebSchemes() {
        assertEquals("http://cbt.school.id", UrlSecurityValidator.normalizeUrl("seb://cbt.school.id"))
        assertEquals("https://cbt.school.id", UrlSecurityValidator.normalizeUrl("sebs://cbt.school.id"))
    }

    @Test
    fun testNormalizeUrl_DirectlyFollowsUserInput() {
        assertEquals("http://192.168.1.50", UrlSecurityValidator.normalizeUrl("http://192.168.1.50"))
        assertEquals("https://moodle.sman1kepanjen.sch.id", UrlSecurityValidator.normalizeUrl("https://moodle.sman1kepanjen.sch.id"))
        assertEquals("http://cbt.sman1kepanjen.sch.id", UrlSecurityValidator.normalizeUrl("http://cbt.sman1kepanjen.sch.id"))
        assertEquals("http://moodle.sman1kepanjen.sch.id", UrlSecurityValidator.normalizeUrl("moodle.sman1kepanjen.sch.id"))
    }

    @Test
    fun testQrPayloadParser_cleanUrl_HandlesHttpAndHttps() {
        assertEquals("http://192.168.1.200", QrPayloadParser.cleanUrl("http://192.168.1.200/"))
        assertEquals("http://192.168.1.200", QrPayloadParser.cleanUrl("HTTP://192.168.1.200/"))
        assertEquals("https://moodle.sman1kepanjen.sch.id", QrPayloadParser.cleanUrl("https://moodle.sman1kepanjen.sch.id/"))
        assertEquals("http://192.168.10.5:8080", QrPayloadParser.cleanUrl("192.168.10.5:8080"))
    }

    @Test
    fun testQrPayloadParser_parse_ExtractsHttpUrlsCorrectly() {
        val httpUrl = "http://192.168.1.150:8000/exam"
        val parsed = QrPayloadParser.parse(httpUrl)
        assertEquals("http://192.168.1.150:8000/exam", parsed.serverUrl)
        assertTrue(parsed.serverUrl.startsWith("http://"))
    }
}
