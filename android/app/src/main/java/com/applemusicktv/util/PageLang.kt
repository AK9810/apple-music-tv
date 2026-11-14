package com.applemusicktv.util

/**
 * Which language the :8080 control page renders in.
 *
 * Precedence is `?lang=` → the browser's `Accept-Language` → English. The TV's own UI
 * language is deliberately *not* consulted: the page is read on the phone that opened it,
 * and a Chinese TV with an English phone should follow the phone. `?lang=` exists because
 * the page cannot set cookies (see ADR-0003) and a link is bookmarkable anyway.
 */
enum class PageLang(val tag: String, val htmlLang: String) {
    EN("en", "en"),
    ZH("zh", "zh-CN");

    companion object {
        /**
         * [queryLang] is the raw `?lang=` value; anything that is not a recognised tag falls
         * through to [acceptLanguage]. Only the first requested language decides — this page
         * has two languages, so weight-sorting the header buys nothing.
         */
        fun resolve(queryLang: String?, acceptLanguage: String?): PageLang {
            when (queryLang?.trim()?.lowercase()) {
                "zh", "zh-cn", "zh-hans", "zh-hant" -> return ZH
                "en", "en-us", "en-gb" -> return EN
            }
            val first = acceptLanguage.orEmpty()
                .substringBefore(',')
                .substringBefore(';')
                .trim()
                .lowercase()
            return if (first.startsWith("zh")) ZH else EN
        }
    }
}
