package com.onedebrid.app.provider.metadata.tmdb

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.error.asFailure
import com.onedebrid.app.domain.error.asSuccess
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.provider.metadata.ExternalIdType
import com.onedebrid.app.provider.metadata.MetadataProvider
import kotlinx.serialization.SerializationException
import okio.IOException
import retrofit2.HttpException
import javax.inject.Inject

class TmdbMetadataProvider @Inject constructor(
    private val api: TmdbApi
) : MetadataProvider {

    override val id: String = "tmdb"
    override val displayName: String = "TMDB"

    override suspend fun fetchMediaDetails(
        externalId: String,
        idType: ExternalIdType
    ): ProviderResult<Media> {
        if (idType != ExternalIdType.TMDB) {
            return ProviderError.NotFound.asFailure()
        }

        val tmdbId = externalId.toIntOrNull()
            ?: return ProviderError.NotFound.asFailure()

        return try {
            try {
                val movie = api.getMovieDetails(movieId = tmdbId)
                movie.toMedia().asSuccess()
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    val tv = api.getTvDetails(tvId = tmdbId)
                    tv.toMedia().asSuccess()
                } else {
                    throw e
                }
            }
        } catch (e: HttpException) {
            e.toProviderError().asFailure()
        } catch (e: IOException) {
            ProviderError.NetworkError.asFailure()
        } catch (e: SerializationException) {
            ProviderError.ParsingError(cause = e).asFailure()
        }
    }

    override suspend fun fetchEpisodes(
        externalId: String,
        idType: ExternalIdType,
        season: Int?
    ): ProviderResult<List<Episode>> {
        if (idType != ExternalIdType.TMDB) {
            return ProviderError.NotFound.asFailure()
        }

        val tmdbId = externalId.toIntOrNull()
            ?: return ProviderError.NotFound.asFailure()

        return try {
            val seasonNumbers: List<Int> = if (season != null) {
                listOf(season)
            } else {
                val tv = api.getTvDetails(tvId = tmdbId)
                (1..(tv.numberOfSeasons ?: 0)).toList()
            }

            val episodes = seasonNumbers.flatMap { seasonNumber ->
                val seasonDto = api.getTvSeason(tvId = tmdbId, seasonNumber = seasonNumber)
                seasonDto.episodes.map { it.toEpisode(mediaId = externalId) }
            }
            episodes.asSuccess()
        } catch (e: HttpException) {
            e.toProviderError().asFailure()
        } catch (e: IOException) {
            ProviderError.NetworkError.asFailure()
        } catch (e: SerializationException) {
            ProviderError.ParsingError(cause = e).asFailure()
        }
    }

    override suspend fun resolveExternalId(
        sourceId: String,
        sourceType: ExternalIdType,
        targetType: ExternalIdType
    ): ProviderResult<String?> =
        ProviderError.ServiceUnavailable.asFailure()

    // Pass includeAdult to the API
    override suspend fun searchMedia(query: String, includeAdult: Boolean): ProviderResult<List<Media>> =
        try {
            val response = api.searchMulti(query = query, includeAdult = includeAdult)
            val results = response.results
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
                .mapNotNull { it.toMedia() }
            results.asSuccess()
        } catch (e: HttpException) {
            e.toProviderError().asFailure()
        } catch (e: IOException) {
            ProviderError.NetworkError.asFailure()
        } catch (e: SerializationException) {
            ProviderError.ParsingError(cause = e).asFailure()
        }

    private fun TmdbMovieDetailsDto.toMedia(): Media = Media(
        id = id.toString(),
        title = title,
        type = MediaType.MOVIE,
        year = releaseDate?.take(4)?.toIntOrNull(),
        imdbId = imdbId ?: externalIds?.imdbId,
        tmdbId = id,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        rating = voteAverage,
        genres = genres?.mapNotNull { it.name } ?: emptyList()
    )

    private fun TmdbTvDetailsDto.toMedia(): Media = Media(
        id = id.toString(),
        title = name,
        type = MediaType.TV_SHOW,
        year = firstAirDate?.take(4)?.toIntOrNull(),
        imdbId = externalIds?.imdbId,
        tmdbId = id,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        rating = voteAverage,
        genres = genres?.mapNotNull { it.name } ?: emptyList()
    )

    private fun TmdbSearchResultDto.toMedia(): Media? {
        val mediaType = when (this.mediaType) {
            "movie" -> MediaType.MOVIE
            "tv" -> MediaType.TV_SHOW
            else -> return null
        }
        val resolvedTitle = title ?: name ?: return null
        val resolvedDate = releaseDate ?: firstAirDate

        return Media(
            id = id.toString(),
            title = resolvedTitle,
            type = mediaType,
            year = resolvedDate?.take(4)?.toIntOrNull(),
            imdbId = null,
            tmdbId = id,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            rating = voteAverage,
            genres = emptyList()
        )
    }

    private fun TmdbEpisodeDto.toEpisode(mediaId: String): Episode = Episode(
        id = id.toString(),
        mediaId = mediaId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        title = name,
        overview = overview,
        stillPath = stillPath,
        airDate = airDate,
        runtime = runtime
    )

    private fun HttpException.toProviderError(): ProviderError = when (code()) {
        401, 403 -> ProviderError.AuthenticationFailed
        404 -> ProviderError.NotFound
        429 -> ProviderError.RateLimited(retryAfterSeconds = null)
        in 500..599 -> ProviderError.ServiceUnavailable
        else -> ProviderError.ParsingError(cause = this)
    }
}