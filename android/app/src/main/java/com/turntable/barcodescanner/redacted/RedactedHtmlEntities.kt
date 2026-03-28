package com.turntable.barcodescanner.redacted

/**
 * Decodes HTML / XML character references (numeric and named) per
 * [ISO 8859-1 / Latin-1 entity names](https://cs.stanford.edu/people/miles/iso8859.html)
 * and [HTML 4.01](https://www.w3.org/TR/html4/sgml/entities.html#h-24.2.1).
 *
 * Handles `&#decimal` / `&#decimal;`, `&#xhex` / `&#xhex;`, and `&name;` (named refs require `;`).
 * Iterates so `&amp;#233;` → é.
 */
object RedactedHtmlEntities {

    /** HTML 4.01 ISO 8859-1 entities in spec order (U+00A0 … U+00FF). */
    private val LATIN1_ENTITY_NAMES = arrayOf(
        // U+00A0–BF (32)
        "nbsp", "iexcl", "cent", "pound", "curren", "yen", "brvbar", "sect", "uml", "copy", "ordf",
        "laquo", "not", "shy", "reg", "macr", "deg", "plusmn", "sup2", "sup3", "acute", "micro",
        "para", "middot", "cedil", "sup1", "ordm", "raquo", "frac14", "frac12", "frac34", "iquest",
        // U+00C0–DF uppercase + × ÷ ß (32): Agrave…szlig
        "Agrave", "Aacute", "Acirc", "Atilde", "Auml", "Aring", "AElig", "Ccedil",
        "Egrave", "Eacute", "Ecirc", "Euml", "Igrave", "Iacute", "Icirc", "Iuml", "ETH", "Ntilde",
        "Ograve", "Oacute", "Ocirc", "Otilde", "Ouml", "times", "Oslash", "Ugrave", "Uacute",
        "Ucirc", "Uuml", "Yacute", "THORN", "szlig",
        // U+00E0–FF lowercase (32)
        "agrave", "aacute", "acirc", "atilde", "auml", "aring", "aelig", "ccedil",
        "egrave", "eacute", "ecirc", "euml", "igrave", "iacute", "icirc", "iuml", "eth", "ntilde",
        "ograve", "oacute", "ocirc", "otilde", "ouml", "divide", "oslash", "ugrave", "uacute",
        "ucirc", "uuml", "yacute", "thorn", "yuml",
    )

    private val NAMED_ENTITIES: Map<String, String> = buildMap {
        check(LATIN1_ENTITY_NAMES.size == 96) { "Latin-1 entity table must be 96 entries" }
        LATIN1_ENTITY_NAMES.forEachIndexed { i, name ->
            put(name, (0xA0 + i).toChar().toString())
        }
        // Core XML / HTML (ASCII specials)
        put("amp", "&")
        put("lt", "<")
        put("gt", ">")
        put("quot", "\"")
        put("apos", "'")
        put("num", "#")
        // Common punctuation / symbols (often seen next to Latin-1 content)
        put("euro", "\u20AC")
        put("ndash", "\u2013")
        put("mdash", "\u2014")
        put("hellip", "\u2026")
        put("lsquo", "\u2018")
        put("rsquo", "\u2019")
        put("sbquo", "\u201A")
        put("ldquo", "\u201C")
        put("rdquo", "\u201D")
        put("bdquo", "\u201E")
        put("dagger", "\u2020")
        put("Dagger", "\u2021")
        put("permil", "\u2030")
        put("lsaquo", "\u2039")
        put("rsaquo", "\u203A")
        put("bull", "\u2022")
        put("oline", "\u203E")
        put("frasl", "\u2044")
        put("trade", "\u2122")
        put("larr", "\u2190")
        put("uarr", "\u2191")
        put("rarr", "\u2192")
        put("darr", "\u2193")
        put("harr", "\u2194")
        put("crarr", "\u21B5")
        put("lArr", "\u21D0")
        put("uArr", "\u21D1")
        put("rArr", "\u21D2")
        put("dArr", "\u21D3")
        put("hArr", "\u21D4")
        put("forall", "\u2200")
        put("part", "\u2202")
        put("exist", "\u2203")
        put("empty", "\u2205")
        put("nabla", "\u2207")
        put("isin", "\u2208")
        put("notin", "\u2209")
        put("ni", "\u220B")
        put("prod", "\u220F")
        put("sum", "\u2211")
        put("minus", "\u2212")
        put("lowast", "\u2217")
        put("radic", "\u221A")
        put("prop", "\u221D")
        put("infin", "\u221E")
        put("ang", "\u2220")
        put("and", "\u2227")
        put("or", "\u2228")
        put("cap", "\u2229")
        put("cup", "\u222A")
        put("int", "\u222B")
        put("there4", "\u2234")
        put("sim", "\u223C")
        put("cong", "\u2245")
        put("asymp", "\u2248")
        put("ne", "\u2260")
        put("equiv", "\u2261")
        put("le", "\u2264")
        put("ge", "\u2265")
        put("sub", "\u2282")
        put("sup", "\u2283")
        put("nsub", "\u2284")
        put("sube", "\u2286")
        put("supe", "\u2287")
        put("oplus", "\u2295")
        put("otimes", "\u2297")
        put("perp", "\u22A5")
        put("sdot", "\u22C5")
        put("lceil", "\u2308")
        put("rceil", "\u2309")
        put("lfloor", "\u230A")
        put("rfloor", "\u230B")
        put("lang", "\u2329")
        put("rang", "\u232A")
        put("loz", "\u25CA")
        put("spades", "\u2660")
        put("clubs", "\u2663")
        put("hearts", "\u2665")
        put("diams", "\u2666")
    }

    /** Decimal code point; semicolon optional if followed by a non-digit (HTML5-style). */
    private val DECIMAL_REF = Regex("&#([0-9]{1,7})(?:;|(?![0-9]))")
    /** Hex code point after `x`; semicolon optional if followed by a non-hex digit. */
    private val HEX_REF = Regex("&#(?i)x([0-9a-f]{1,6})(?:;|(?![0-9a-fA-F]))")
    private val NAMED_REF = Regex("&([a-zA-Z][a-zA-Z0-9]*);")

    /**
     * Decodes numeric and named character references. Safe to call on BBCode or plain text before
     * further escaping. Do not run on raw untrusted HTML before sanitization (could expand `&lt;`).
     */
    fun decodeCharacterReferences(input: String): String {
        if (input.isEmpty()) return input
        var s = input
        repeat(10) {
            val pass = decodeNamedPass(decodeHexPass(decodeDecimalPass(s)))
            if (pass == s) return s
            s = pass
        }
        return s
    }

    private fun decodeDecimalPass(s: String): String =
        DECIMAL_REF.replace(s) { m ->
            val cp = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            codePointToDisplayString(cp)
        }

    private fun decodeHexPass(s: String): String =
        HEX_REF.replace(s) { m ->
            val cp = m.groupValues[1].toIntOrNull(16) ?: return@replace m.value
            codePointToDisplayString(cp)
        }

    private fun decodeNamedPass(s: String): String =
        NAMED_REF.replace(s) { m ->
            val name = m.groupValues[1]
            lookupNamedEntity(name) ?: m.value
        }

    private fun lookupNamedEntity(name: String): String? {
        NAMED_ENTITIES[name]?.let { return it }
        // Case-insensitive only for the five core escapes (HTML5 / common)
        return when (name.lowercase()) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "num" -> "#"
            else -> null
        }
    }

    /**
     * Maps a code point to a UTF-16 string for UI. Strips most C0 controls (keeps TAB/LF/CR);
     * NUL and DEL omitted.
     */
    private fun codePointToDisplayString(cp: Int): String =
        when {
            cp <= 0 -> ""
            cp == 9 || cp == 10 || cp == 13 -> String(Character.toChars(cp))
            cp in 1..8 || cp in 11..12 || cp in 14..31 -> ""
            cp == 127 -> ""
            cp in 0x80..0x9F -> String(Character.toChars(cp)) // Latin-1 / legacy control region
            cp in 32..0x10FFFF -> String(Character.toChars(cp))
            else -> "\uFFFD"
        }
}
