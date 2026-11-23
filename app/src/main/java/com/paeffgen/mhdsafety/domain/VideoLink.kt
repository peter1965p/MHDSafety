package com.paeffgen.mhdsafety.domain

/** Erkennt Video-Links (YouTube, Shorts, Instagram, TikTok) und liest Zutaten aus Videobeschreibungen. */
object VideoLink {
    const val YOUTUBE = "youtube"
    const val INSTAGRAM = "instagram"
    const val TIKTOK = "tiktok"

    private val youtubeId = Regex(
        "(?:youtube\\.com/(?:watch\\?(?:[^#\\s]*&)?v=|shorts/|embed/|live/)|youtu\\.be/)([A-Za-z0-9_-]{11})",
        RegexOption.IGNORE_CASE,
    )

    /** Die 11-stellige Video-ID oder null, wenn es kein YouTube-Link ist. */
    fun youtubeId(url: String): String? = youtubeId.find(url)?.groupValues?.get(1)

    /** "youtube", "instagram", "tiktok" oder null für normale Webseiten. */
    fun source(url: String): String? {
        val host = url.lowercase().substringAfter("://").substringBefore('/').removePrefix("www.").removePrefix("m.")
        return when {
            youtubeId(url) != null -> YOUTUBE
            host == "instagram.com" || host.endsWith(".instagram.com") -> INSTAGRAM
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> TIKTOK
            else -> null
        }
    }

    fun isVideo(url: String): Boolean = source(url) != null

    /** Einheitlicher Link zum Speichern (Shorts, youtu.be und m. → watch?v=). */
    fun canonical(url: String): String = youtubeId(url)?.let { "https://www.youtube.com/watch?v=$it" } ?: url

    /** Datenschutzfreundlicher Player ohne Cookies. */
    fun embedUrl(videoId: String): String = "https://www.youtube-nocookie.com/embed/$videoId?playsinline=1&rel=0"

    fun thumbnail(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private val einheit = Regex(
        "\\b(g|kg|mg|ml|l|liter|el|tl|essl[öo]ffel|teel[öo]ffel|prise|prisen|st[üu]ck|stk|dose|dosen|bund|zehe|zehen|" +
            "tasse|tassen|becher|packung|pck|p[äa]ckchen|scheibe|scheiben|cup|cups|tbsp|tsp|oz)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val mengeVorn = Regex("^\\s*(?:[-•*·▪➡️✅🔸]\\s*)?(\\d+[\\d,./½¼¾]*|½|¼|¾|eine?|zwei|drei)\\b", RegexOption.IGNORE_CASE)
    private val aufzaehlung = Regex("^\\s*[-•*·▪🔸✅]\\s*\\S")
    private val zutatenKopf = Regex("^\\s*(zutaten|ingredients|du brauchst|ihr braucht|für \\d+ (personen|portionen))\\b.*", RegexOption.IGNORE_CASE)
    private val abschnittsEnde = Regex(
        "^\\s*(zubereitung|anleitung|so geht'?s|instructions|method|schritt|step|links?|musik|music|abonnier|folg|follow|#)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Zutatenzeilen aus einer Videobeschreibung: bevorzugt der Block nach „Zutaten:“,
     * sonst Zeilen, die nach Menge + Einheit aussehen. Leere Liste, wenn nichts Brauchbares da ist.
     */
    fun ingredientsFromDescription(description: String): List<String> {
        val zeilen = description.lines().map { it.trim() }
        val kopf = zeilen.indexOfFirst { zutatenKopf.matches(it) }
        val kandidaten = if (kopf >= 0) {
            zeilen.drop(kopf + 1)
                .takeWhile { !abschnittsEnde.containsMatchIn(it) }
                .filter { it.isNotBlank() && (mengeVorn.containsMatchIn(it) || einheit.containsMatchIn(it) || aufzaehlung.containsMatchIn(it)) }
        } else {
            zeilen.filter { mengeVorn.containsMatchIn(it) && einheit.containsMatchIn(it) && it.length <= 80 }
        }
        return kandidaten
            .map { it.replace(Regex("^[-•*·▪🔸✅➡️\\s]+"), "").trim() }
            .filter { it.length in 2..100 && !it.contains("http") }
            .distinct()
            .take(40)
    }
}
