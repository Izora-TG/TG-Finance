package com.tgfinance.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {
    @Test
    fun `accepts https and adds missing scheme`() {
        assertEquals(ServerUrl.Result.Valid("https://finance.example.com"), ServerUrl.normalize(" finance.example.com/ "))
        assertEquals(ServerUrl.Result.Valid("https://host:8443/tg"), ServerUrl.normalize("https://host:8443/tg/"))
    }

    @Test
    fun `rejects http and garbage`() {
        assertEquals(ServerUrl.Result.NotHttps, ServerUrl.normalize("http://finance.example.com"))
        assertEquals(ServerUrl.Result.NotHttps, ServerUrl.normalize("ftp://x"))
        assertEquals(ServerUrl.Result.Empty, ServerUrl.normalize("  "))
        assertEquals(ServerUrl.Result.Invalid, ServerUrl.normalize("https://"))
        assertEquals(ServerUrl.Result.Invalid, ServerUrl.normalize("https://user:pw@host"))
        assertEquals(ServerUrl.Result.Invalid, ServerUrl.normalize("https://host?x=1"))
    }
}
