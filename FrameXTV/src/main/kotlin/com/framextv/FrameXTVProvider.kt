package com.framextv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class FrameXTVProvider : MainAPI() {
    override var mainUrl = "https://framextv.xyz"
    override var name = "frameXTV"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true

    companion object {
        private const val API_URL = "https://api.framextv.tech"
        private const val ANIME_API_URL = "https://api.framextv.tech/anime"
        private const val ANILIST_API_URL = "https://graphql.anilist.co"

        private val defaultHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://framextv.xyz/",
            "Origin" to "https://framextv.xyz"
        )
    }

    override val mainPage = mainPageOf(
        "trending_movies" to "Trending Movies",
        "popular_movies" to "Popular Movies",
        "popular_tv" to "Popular TV Shows",
        "popular_anime" to "Popular Anime",
        "new_releases" to "New Releases",
        "action_movies" to "Action Movies",
        "scifi_movies" to "Sci-Fi Movies",
        "thriller_movies" to "Thriller Movies",
        "comedy_movies" to "Comedy Movies",
        "airing_anime" to "Airing Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val response = app.get("$API_URL/api/home", headers = defaultHeaders).parsedSafe<HomeResponse>()
            ?: return null

        val rail = response.rails?.get(request.data)
        val items = rail?.results?.mapNotNull { it.toSearchResponse() } ?: emptyList()

        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$API_URL/api/search?q=$encoded"
        val response = app.get(url, headers = defaultHeaders).parsedSafe<SearchResponseDto>()
            ?: return emptyList()

        return response.results?.mapNotNull { it.toSearchResponse() } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val parsed = parseUrlIdentifiers(url) ?: return null
        val (mediaType, mediaId) = parsed

        if (mediaType == "anime") {
            val query = """
                query (${'$'}id: Int) {
                  Media(id: ${'$'}id, type: ANIME, isAdult: false) {
                    id
                    title { english romaji native }
                    coverImage { extraLarge large }
                    bannerImage
                    description
                    episodes
                    format
                    genres
                    averageScore
                    seasonYear
                  }
                }
            """.trimIndent()

            val animeRes = try {
                app.post(
                    ANILIST_API_URL,
                    json = mapOf("query" to query, "variables" to mapOf("id" to mediaId.toIntOrNull())),
                    headers = mapOf("Content-Type" to "application/json", "User-Agent" to defaultHeaders["User-Agent"]!!)
                ).parsedSafe<AniListResponse>()
            } catch (e: Exception) {
                null
            }

            val media = animeRes?.data?.media
            val title = media?.title?.english ?: media?.title?.romaji ?: media?.title?.native ?: "Anime $mediaId"
            val poster = media?.coverImage?.extraLarge ?: media?.coverImage?.large
            val backdrop = media?.bannerImage
            val year = media?.seasonYear
            val plot = media?.description?.replace(Regex("<[^>]*>"), "")?.trim()
            val genres = media?.genres

            val totalEpisodes = media?.episodes ?: 12
            val episodes = (1..totalEpisodes).map { epNum ->
                newEpisode(
                    EpisodeData(
                        id = mediaId,
                        type = "anime",
                        season = 1,
                        episode = epNum
                    )
                ) {
                    this.name = "Episode $epNum"
                    this.season = 1
                    this.episode = epNum
                }
            }

            return newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.plot = plot
                this.tags = genres
                addEpisodes(DubStatus.Subbed, episodes)
            }
        }

        val detailsUrl = "$API_URL/api/details?id=$mediaId&type=$mediaType"
        val details = app.get(detailsUrl, headers = defaultHeaders).parsedSafe<MediaDetailsDto>()
            ?: return null

        val title = details.title ?: details.originalTitle ?: "Unknown"
        val poster = details.poster
        val backdrop = details.backdrop
        val year = details.year?.toIntOrNull()
        val plot = details.overview
        val genres = details.genres

        return if (mediaType == "tv") {
            val episodes = mutableListOf<Episode>()
            val seasons = details.seasons ?: listOf(SeasonDto(number = 1, name = "Season 1"))

            for (season in seasons) {
                val sNum = season.number ?: continue
                try {
                    val seasonUrl = "$API_URL/api/season?id=$mediaId&season=$sNum"
                    val seasonRes = app.get(seasonUrl, headers = defaultHeaders).parsedSafe<SeasonEpisodesDto>()
                    seasonRes?.episodes?.forEach { ep ->
                        val epNum = ep.number ?: return@forEach
                        episodes.add(
                            newEpisode(
                                EpisodeData(
                                    id = mediaId,
                                    type = "tv",
                                    season = sNum,
                                    episode = epNum
                                )
                            ) {
                                this.name = ep.name ?: "Episode $epNum"
                                this.season = sNum
                                this.episode = epNum
                                this.posterUrl = ep.still
                                this.description = ep.overview
                            }
                        )
                    }
                } catch (e: Exception) {
                    // Continue fetching remaining seasons
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.plot = plot
                this.tags = genres
            }
        } else {
            val epData = EpisodeData(
                id = mediaId,
                type = "movie"
            ).toJson()

            newMovieLoadResponse(title, url, TvType.Movie, epData) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.plot = plot
                this.tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val epData = tryParseJson<EpisodeData>(data) ?: return false
        val mediaId = epData.id
        val mediaType = epData.type
        val season = epData.season
        val episode = epData.episode

        val seenUrls = mutableSetOf<String>()
        var foundAnyLink = false

        suspend fun emitSources(sources: List<StreamSourceDto>?) {
            for (src in sources ?: emptyList()) {
                var streamUrl = src.url?.trim() ?: continue
                if (streamUrl.startsWith("/")) {
                    streamUrl = "$API_URL$streamUrl"
                }
                if (seenUrls.contains(streamUrl)) continue

                // Skip tcloud: chunks use steganographic PNG headers that fail in ExoPlayer
                if (src.quality?.contains("tcloud", ignoreCase = true) == true) continue

                seenUrls.add(streamUrl)

                val quality = parseQuality(src.quality)
                val isHls = src.type.equals("hls", ignoreCase = true) || streamUrl.contains(".m3u8")

                val linkHeaders = mutableMapOf<String, String>()
                src.headers?.let { linkHeaders.putAll(it) }

                val referer = if (streamUrl.contains("spatula.hybrows.workers.dev")) {
                    linkHeaders["Origin"] = "https://www.rivestream.app"
                    "https://www.rivestream.app/"
                } else if (streamUrl.contains("megaplay.buzz") || mediaType == "anime") {
                    linkHeaders["Origin"] = "https://megaplay.buzz"
                    "https://megaplay.buzz/"
                } else {
                    "https://framextv.xyz/"
                }

                val cleanServerName = when {
                    src.quality.equals("dcloud", ignoreCase = true) || src.server?.contains("PrimeVids", ignoreCase = true) == true ->
                        "PrimeVids - 1080p [Fast HLS]"
                    src.server?.contains("Citadel", ignoreCase = true) == true -> {
                        if (src.quality?.contains("720") == true) "Citadel - 720p [English]"
                        else if (src.quality?.contains("480") == true) "Citadel - 480p [English]"
                        else "Citadel - ${src.quality ?: "HLS"}"
                    }
                    else -> {
                        val qLabel = when (quality) {
                            Qualities.P1080.value -> "1080p"
                            Qualities.P720.value -> "720p"
                            Qualities.P480.value -> "480p"
                            Qualities.P2160.value -> "4K"
                            else -> src.quality ?: "Auto"
                        }
                        "${src.server ?: "frameXTV"} - $qLabel"
                    }
                }

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = cleanServerName,
                        url = streamUrl,
                        type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = referer
                        this.quality = quality
                        this.headers = linkHeaders
                    }
                )
                foundAnyLink = true
            }
        }

        fun emitSubtitles(subs: List<SubtitleDto>?) {
            subs?.forEach { sub ->
                val subUrl = sub.url?.trim() ?: return@forEach
                val lang = sub.label ?: sub.language ?: sub.lang ?: "Sub"
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = lang,
                        url = subUrl
                    )
                )
            }
        }

        if (mediaType == "anime") {
            // Anime Stream resolution via framextv anime backend
            try {
                val animeStreamUrl = "$ANIME_API_URL/api/stream?id=$mediaId&type=anime&episode=$episode"
                val streamRes = app.get(animeStreamUrl, headers = defaultHeaders).parsedSafe<StreamResponseDto>()
                emitSources(streamRes?.sources)
                emitSubtitles(streamRes?.subtitles)
                streamRes?.sources?.forEach { s -> emitSubtitles(s.subtitles) }
            } catch (e: Exception) {
                // Non-fatal
            }

            try {
                val subUrl = "$ANIME_API_URL/api/subtitles?id=$mediaId&type=anime&episode=$episode"
                val subRes = app.get(subUrl, headers = defaultHeaders).parsedSafe<SubtitlesResponseDto>()
                emitSubtitles(subRes?.subtitles)
            } catch (e: Exception) {
                // Non-fatal
            }

            return foundAnyLink
        }

        // VOD (Movie / TV) Stream Resolution
        val tvQuery = if (mediaType == "tv") "&season=$season&episode=$episode" else ""

        // Strategy 1: Default stream source (Headhunter / VidLink etc.)
        try {
            val defaultUrl = "$API_URL/api/stream?id=$mediaId&type=$mediaType$tvQuery"
            val defaultRes = app.get(defaultUrl, headers = defaultHeaders).parsedSafe<StreamResponseDto>()
            emitSources(defaultRes?.sources)
            emitSubtitles(defaultRes?.subtitles)
        } catch (e: Exception) {
            // Non-fatal
        }

        // Strategy 2: Bowler XVIII (PrimeVids & Citadel multi-tier HLS)
        try {
            val bowlerUrl = "$API_URL/api/stream?id=$mediaId&type=$mediaType$tvQuery&provider=bowler"
            val bowlerRes = app.get(bowlerUrl, headers = defaultHeaders).parsedSafe<StreamResponseDto>()
            emitSources(bowlerRes?.sources)
            emitSubtitles(bowlerRes?.subtitles)
        } catch (e: Exception) {
            // Non-fatal
        }

        // Strategy 3: Miner XIX (High-speed Movy cluster)
        try {
            val minerUrl = "$API_URL/api/stream?id=$mediaId&type=$mediaType$tvQuery&provider=miner"
            val minerRes = app.get(minerUrl, headers = defaultHeaders).parsedSafe<StreamResponseDto>()
            emitSources(minerRes?.sources)
            emitSubtitles(minerRes?.subtitles)
        } catch (e: Exception) {
            // Non-fatal
        }

        // Strategy 4: Super Barbarian / CineJoy (Direct Client-Side AES-GCM Decryption)
        try {
            if (extractCineJoy(mediaId, mediaType, season, episode, callback)) {
                foundAnyLink = true
            }
        } catch (e: Exception) {
            // Non-fatal
        }

        // Dedicated Subtitles via OpenSubtitles CDN
        try {
            val subsUrl = "$API_URL/api/subtitles?id=$mediaId&type=$mediaType$tvQuery"
            val subRes = app.get(subsUrl, headers = defaultHeaders).parsedSafe<SubtitlesResponseDto>()
            emitSubtitles(subRes?.subtitles)
        } catch (e: Exception) {
            // Non-fatal
        }

        return foundAnyLink
    }

    private fun base64UrlDecode(str: String): ByteArray {
        var s = str.replace('-', '+').replace('_', '/')
        while (s.length % 4 != 0) {
            s += "="
        }
        return Base64.getDecoder().decode(s)
    }

    private fun decryptAesGcm(keyBase64Url: String, aadBase64Url: String, encryptedBytes: ByteArray): String? {
        return try {
            val keyBytes = base64UrlDecode(keyBase64Url)
            val aadBytes = base64UrlDecode(aadBase64Url)

            val iv = encryptedBytes.copyOfRange(0, 12)
            val ciphertextAndTag = encryptedBytes.copyOfRange(12, encryptedBytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(aadBytes)
            val decrypted = cipher.doFinal(ciphertextAndTag)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun extractCineJoy(
        mediaId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val isTv = mediaType == "tv"
        val serverList = listOf("Lisbon", "Solara")
        var found = false

        for (server in serverList) {
            try {
                val targetUrl = StringBuilder("https://api.wing.st/?title=movie&type=")
                    .append(if (isTv) "series" else "movie")
                    .append("&year=&imdb=&tmdb=").append(mediaId)
                    .append("&server=").append(server)
                if (isTv) {
                    targetUrl.append("&season=").append(season ?: 1)
                        .append("&episode=").append(episode ?: 1)
                }

                val encUrl = "https://enc-dec.app/api/enc-cinejoy?url=" + URLEncoder.encode(targetUrl.toString(), "UTF-8")
                val encRes = app.get(
                    encUrl,
                    headers = mapOf("Accept" to "application/json", "User-Agent" to defaultHeaders["User-Agent"]!!)
                ).parsedSafe<CineJoyEncResponse>()

                val dataB64 = encRes?.result?.data ?: continue
                val state = encRes.result.state ?: continue
                val responseKey = state.responseKey ?: continue
                val aad = state.aad ?: continue

                val reqBodyBytes = base64UrlDecode(dataB64)
                val mediaTypeOctet = "application/octet-stream".toMediaTypeOrNull()
                val requestBody = reqBodyBytes.toRequestBody(mediaTypeOctet)

                val gatewayRes = app.post(
                    "https://api.wing.st/g",
                    requestBody = requestBody,
                    headers = mapOf(
                        "Content-Type" to "application/octet-stream",
                        "Accept" to "application/json, text/plain, */*",
                        "User-Agent" to defaultHeaders["User-Agent"]!!
                    )
                )

                val encBytes = gatewayRes.body.byteStream().readBytes()
                if (encBytes.size <= 28) continue

                val decryptedJson = decryptAesGcm(responseKey, aad, encBytes) ?: continue
                val parsed = tryParseJson<CineJoyDecryptedData>(decryptedJson)
                val streams = parsed?.data?.stream ?: emptyList()

                val cinejoyHeaders = mapOf(
                    "Referer" to "https://cinejoy.pk/",
                    "Origin" to "https://cinejoy.pk",
                    "User-Agent" to defaultHeaders["User-Agent"]!!
                )

                for (stream in streams) {
                    val playlistUrl = stream.playlist?.trim() ?: continue
                    if (!playlistUrl.startsWith("http")) continue

                    if (server == "Lisbon") {
                        // Quick probe: Lisbon fails with HTTP 502 on some titles (causes Error 2004 in ExoPlayer)
                        val probe = try {
                            app.get(playlistUrl, headers = cinejoyHeaders, timeout = 2)
                        } catch (e: Exception) {
                            null
                        }
                        if (probe == null || probe.code != 200) {
                            continue // Skip dead transcoder
                        }

                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "Super Barbarian (Lisbon) - 1080p",
                                url = playlistUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "https://cinejoy.pk/"
                                this.quality = Qualities.P1080.value
                                this.headers = cinejoyHeaders
                            }
                        )
                        found = true
                    } else if (server == "Solara") {
                        // Solara returns an adaptive HLS master playlist with 1080p, 720p and 360p
                        val masterRes = try {
                            app.get(playlistUrl, headers = cinejoyHeaders, timeout = 3)
                        } catch (e: Exception) {
                            null
                        }
                        if (masterRes == null || masterRes.code != 200) continue

                        val lines = masterRes.text.lines()
                        var sub1080Url: String? = null
                        var sub720Url: String? = null

                        for (i in lines.indices) {
                            val line = lines[i]
                            if (line.startsWith("#EXT-X-STREAM-INF")) {
                                val nextUrl = lines.getOrNull(i + 1)?.trim()
                                if (nextUrl != null && nextUrl.startsWith("http")) {
                                    if (line.contains("1920") || line.contains("1080")) {
                                        sub1080Url = nextUrl
                                    } else if (line.contains("1280") || line.contains("720")) {
                                        sub720Url = nextUrl
                                    }
                                }
                            }
                        }

                        if (sub1080Url != null) {
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "Super Barbarian (Solara) - 1080p [High Bitrate]",
                                    url = sub1080Url,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = "https://cinejoy.pk/"
                                    this.quality = Qualities.P1080.value
                                    this.headers = cinejoyHeaders
                                }
                            )
                            found = true
                        }

                        if (sub720Url != null) {
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = "Super Barbarian (Solara) - 720p",
                                    url = sub720Url,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = "https://cinejoy.pk/"
                                    this.quality = Qualities.P720.value
                                    this.headers = cinejoyHeaders
                                }
                            )
                            found = true
                        }

                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "Super Barbarian (Solara) - Auto [Adaptive]",
                                url = playlistUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "https://cinejoy.pk/"
                                this.quality = Qualities.P1080.value
                                this.headers = cinejoyHeaders
                            }
                        )
                        found = true
                    }
                }
            } catch (e: Exception) {
                // Continue to next CineJoy server
            }
        }
        return found
    }

    private fun MediaItem.toSearchResponse(): SearchResponse? {
        val itemId = id?.toString() ?: return null
        val itemTitle = title ?: name ?: return null
        val normalizedType = type?.lowercase() ?: "movie"
        val posterUrl = poster ?: image

        val itemYear = year?.toIntOrNull()

        return when (normalizedType) {
            "anime" -> {
                val itemUrl = "$mainUrl/watch/anime/$itemId"
                newAnimeSearchResponse(itemTitle, itemUrl, TvType.Anime) {
                    this.posterUrl = posterUrl
                    this.year = itemYear
                }
            }
            "tv" -> {
                val itemUrl = "$mainUrl/watch/tv/$itemId"
                newTvSeriesSearchResponse(itemTitle, itemUrl, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.year = itemYear
                }
            }
            else -> {
                val itemUrl = "$mainUrl/watch/movie/$itemId"
                newMovieSearchResponse(itemTitle, itemUrl, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.year = itemYear
                }
            }
        }
    }

    private fun parseUrlIdentifiers(url: String): Pair<String, String>? {
        // Format: /watch/{type}/{id} or /embed/{type}/{id}
        val matchTypeAndId = Regex("/(?:watch|embed)/([a-zA-Z]+)/([0-9]+)").find(url)
        if (matchTypeAndId != null) {
            val type = matchTypeAndId.groupValues[1].lowercase()
            val id = matchTypeAndId.groupValues[2]
            return Pair(
                when (type) {
                    "anime" -> "anime"
                    "tv" -> "tv"
                    else -> "movie"
                },
                id
            )
        }

        // Format: /{type}/{id}
        val matchDirect = Regex("/(anime|tv|movie)/([0-9]+)").find(url)
        if (matchDirect != null) {
            return Pair(matchDirect.groupValues[1].lowercase(), matchDirect.groupValues[2])
        }

        // Format: /watch/{id}
        val matchJustId = Regex("/watch/([0-9]+)").find(url)
        if (matchJustId != null) {
            return Pair("movie", matchJustId.groupValues[1])
        }

        return null
    }

    private fun parseQuality(quality: String?): Int {
        if (quality == null) return Qualities.Unknown.value
        return when {
            quality.contains("4K", ignoreCase = true) || quality.contains("2160", ignoreCase = true) -> Qualities.P2160.value
            quality.contains("1080", ignoreCase = true) || quality.contains("dcloud", ignoreCase = true) -> Qualities.P1080.value
            quality.contains("720", ignoreCase = true) || quality.contains("tcloud", ignoreCase = true) -> Qualities.P720.value
            quality.contains("480", ignoreCase = true) -> Qualities.P480.value
            quality.contains("360", ignoreCase = true) -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

    private fun extractNestedUrl(spatulaUrl: String): String? {
        return try {
            val params = spatulaUrl.substringAfter("?").split("&")
            val urlParam = params.find { it.startsWith("url=") }?.substringAfter("url=")
            urlParam?.let { URLDecoder.decode(it, "UTF-8") }
        } catch (e: Exception) {
            null
        }
    }
}
