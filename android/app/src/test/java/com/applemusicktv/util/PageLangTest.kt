package com.applemusicktv.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the language precedence of the :8080 control page: `?lang=` wins, then the browser's
 * `Accept-Language`, then English. Getting this wrong is visible — a Chinese user landing on
 * an English page cannot read the setup steps — and it is the only server-side decision the
 * page's language depends on.
 */
class PageLangTest {

    @Test
    fun `query parameter wins over the browser header`() {
        assertEquals(PageLang.ZH, PageLang.resolve("zh", "en-US,en;q=0.9"))
        assertEquals(PageLang.EN, PageLang.resolve("en", "zh-CN,zh;q=0.9"))
        // A tag we do not ship falls through instead of turning the page into English by accident.
        assertEquals(PageLang.ZH, PageLang.resolve("fr", "zh-CN"))
    }

    @Test
    fun `accepts the common spellings of both languages`() {
        assertEquals(PageLang.ZH, PageLang.resolve("ZH-CN", null))
        assertEquals(PageLang.EN, PageLang.resolve(" en ", "zh-CN"))
    }

    @Test
    fun `falls back to the first accept-language tag`() {
        assertEquals(PageLang.ZH, PageLang.resolve(null, "zh-CN,zh;q=0.9,en;q=0.8"))
        assertEquals(PageLang.EN, PageLang.resolve(null, "en-US,en;q=0.9,zh;q=0.8"))
        // Traditional Chinese still gets the (simplified) Chinese page — one zh translation.
        assertEquals(PageLang.ZH, PageLang.resolve(null, "zh-Hant-TW"))
        assertEquals(PageLang.EN, PageLang.resolve(null, "  en-GB ;q=0.9"))
    }

    @Test
    fun `defaults to english when nothing tells us otherwise`() {
        assertEquals(PageLang.EN, PageLang.resolve(null, null))
        assertEquals(PageLang.EN, PageLang.resolve("", ""))
        assertEquals(PageLang.EN, PageLang.resolve(null, "*"))
    }
}
