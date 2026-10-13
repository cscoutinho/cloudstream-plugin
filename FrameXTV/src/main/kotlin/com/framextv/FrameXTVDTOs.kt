package com.framextv

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeResponse(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("hero") val hero: MediaItem? = null,
    @JsonProperty("rails") val rails: Map<String, RailData>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RailData(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("page") val page: Int? = null,
    @JsonProperty("totalPages") val totalPages: Int? = null,
    @JsonProperty("totalResults") val totalResults: Int? = null,
    @JsonProperty("results") val results: List<MediaItem>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MediaItem(
    @JsonProperty("id") val id: Any? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("rating") val rating: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("backdrop") val backdrop: String? = null,
    @JsonProperty("year") val year: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("genres") val genres: List<String>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchResponseDto(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("results") val results: List<MediaItem>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MediaDetailsDto(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("id") val id: Any? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("originalTitle") val originalTitle: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("backdrop") val backdrop: String? = null,
    @JsonProperty("releaseDate") val releaseDate: String? = null,
    @JsonProperty("year") val year: String? = null,
    @JsonProperty("rating") val rating: String? = null,
    @JsonProperty("voteCount") val voteCount: Any? = null,
    @JsonProperty("genres") val genres: List<String>? = null,
    @JsonProperty("runtime") val runtime: Any? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("tagline") val tagline: String? = null,
    @JsonProperty("numberOfSeasons") val numberOfSeasons: Int? = null,
    @JsonProperty("numberOfEpisodes") val numberOfEpisodes: Int? = null,
    @JsonProperty("seasons") val seasons: List<SeasonDto>? = null,
    @JsonProperty("recommendations") val recommendations: List<MediaItem>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeasonDto(
    @JsonProperty("number") val number: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("episodeCount") val episodeCount: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeasonEpisodesDto(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("episodes") val episodes: List<EpisodeItemDto>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpisodeItemDto(
    @JsonProperty("id") val id: Any? = null,
    @JsonProperty("number") val number: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("still") val still: String? = null,
    @JsonProperty("airDate") val airDate: String? = null,
    @JsonProperty("rating") val rating: String? = null,
    @JsonProperty("overview") val overview: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamResponseDto(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("provider") val provider: String? = null,
    @JsonProperty("server") val server: String? = null,
    @JsonProperty("audio") val audio: String? = null,
    @JsonProperty("sources") val sources: List<StreamSourceDto>? = null,
    @JsonProperty("subtitles") val subtitles: List<SubtitleDto>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamSourceDto(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("server") val server: String? = null,
    @JsonProperty("skipProxy") val skipProxy: Boolean? = null,
    @JsonProperty("audio") val audio: String? = null,
    @JsonProperty("headers") val headers: Map<String, String>? = null,
    @JsonProperty("subtitles") val subtitles: List<SubtitleDto>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SubtitleDto(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("lang") val lang: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("format") val format: String? = null,
    @JsonProperty("source") val source: String? = null,
    @JsonProperty("isDefault") val isDefault: Boolean? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SubtitlesResponseDto(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("subtitles") val subtitles: List<SubtitleDto>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SeedResponseDto(
    @JsonProperty("seed") val seed: String? = null,
    @JsonProperty("ttlMs") val ttlMs: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AniListResponse(
    @JsonProperty("data") val data: AniListData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AniListData(
    @JsonProperty("Media") val media: AniListMedia? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AniListMedia(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: AniListTitle? = null,
    @JsonProperty("coverImage") val coverImage: AniListCover? = null,
    @JsonProperty("bannerImage") val bannerImage: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("episodes") val episodes: Int? = null,
    @JsonProperty("format") val format: String? = null,
    @JsonProperty("genres") val genres: List<String>? = null,
    @JsonProperty("averageScore") val averageScore: Int? = null,
    @JsonProperty("seasonYear") val seasonYear: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AniListTitle(
    @JsonProperty("english") val english: String? = null,
    @JsonProperty("romaji") val romaji: String? = null,
    @JsonProperty("native") val native: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AniListCover(
    @JsonProperty("extraLarge") val extraLarge: String? = null,
    @JsonProperty("large") val large: String? = null
)

data class EpisodeData(
    val id: String,
    val type: String,
    val season: Int? = null,
    val episode: Int? = null
)
