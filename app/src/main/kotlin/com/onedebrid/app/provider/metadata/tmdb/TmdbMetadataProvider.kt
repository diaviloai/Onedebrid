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

/**
 * MetadataProvider implementation backed by The Movie Database (TMDB)
 * API v3 (https://api.themoviedb.org/3/).
 *
 * Replaces StubMetadataProvider as OneDebrid's first real
 * MetadataProvider. See currentsprint.md for the full design discussion
 * with Dia (the "real MetadataProvider" session).
 *
 * IMPORTANT — Media.id semantics (decided this session):
 * Media.id is the TMDB id, stringified (e.g. "550"), NOT the IMDb id.
 * This was a genuine open question (Media.id's meaning had never been
 * decided in code before this session — no real provider had existed to
 * force the decision) and was resolved in favor of TMDB id because
 * search/multi results carry it natively, with no extra resolve step,
 * whereas IMDb id is only available from TMDB's detail endpoints (and,
 * for TV, only via an additional append_to_response). Media.imdbId is a
 * separate, secondary field — populated only where available (see
 * fetchMediaDetails() below) — used specifically by
 * SearchProvider.searchByMedia() (Torrentio) downstream.
 *
 * IMPORTANT — search() vs searchMedia():
 * This mirrors the same real-provider-capability split documented on
 * TorrentioSearchProvider, but for the opposite reason. TMDB's
 * search/multi endpoint (this provider) is genuinely capable of
 * free-text title search — but it can never return an imdbId on a
 * search result, because TMDB's append_to_response mechanism (the only
 * way to get imdb_id from this API) is documented as working only on
 * detail endpoints, not search endpoints. So Media objects from
 * searchMedia() always have imdbId = null; a caller wanting a specific
 * item's imdbId must call fetchMediaDetails() on it afterward. See
 * MetadataProvider.searchMedia()'s doc comment for the full reasoning.
 *
 * IMPORTANT — movie vs TV asymmetry for imdb_id:
 * Movie detail responses include imdb_id at the top level with no
 * append needed. TV detail responses do NOT — they require
 * append_to_response=external_ids, which nests it under a separate
 * externalIds object instead. Both are requested by default (see
 * TmdbApi.kt) but mapped differently below (mapMovieToMedia() vs
 * mapTvToMedia()) to reflect this real difference, not for stylistic
 * consistency.
 *
 * KNOWN LIMITATION — fetchMediaDetails() media-type ambiguity:
 * Media.id alone (a TMDB id) does not indicate whether it belongs to a
 * movie or a TV show — TMDB's movie and TV id spaces are not shared,
 * but nothing in this app's domain model currently disambiguates them
 * before this call. This method tries /movie/{id} first, and only
 * falls back to /tv/{id} if that returns 404. This means every TV
 * lookup costs one extra (wasted) HTTP call today. A cleaner fix would
 * carry MediaType alongside the id through getMediaDetails()'s callers
 * — not done this session, flagged as a real, known cost rather than
 * hidden.
 *
 * KNOWN LIMITATION — fetchEpisodes(season = null):
 * Fetching "all seasons" requires first fetching TV details to learn
 * how many seasons exist, then fetching each season sequentially — an
 * N+1 call pattern, not a single call. TMDB does not offer a
 * single-call "all episodes" endpoint (verified, not assumed — see
 * currentsprint.md sourcing notes). Callers requesting a specific
 * season should always pass one explicitly where possible to avoid
 * this cost.
 *
 * KNOWN LIMITATION — resolveExternalId() not implemented:
 * TMDB's /find/{external_id} endpoint would implement this properly,
 * but nothing in the app currently calls resolveExternalId() at all —
 * building it now would be speculative. Returns
 * ProviderError.ServiceUnavailable, honestly signaling "not implemented
 * yet" rather than silently returning null-as-success (which would
 * incorrectly claim "looked it up, found nothing").
 */
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
            // This provider only looks up by its own native id type.
            // See class doc comment re: resolveExternalId() not being
            // implemented yet — that is the (currently missing) path
            // that would let a caller convert an IMDb/TVDB/Trakt id
            // into a TMDB id before calling this method.
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
                    // Not a movie id — try TV before giving up. See class
                    // doc comment re: this being a known, accepted cost.
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
                // "All seasons" requires learning the season count first.
                // See class doc comment re: this being an N+1 cost.
                val tv = api.getTvDetails(tvId = tmdbId)
                // TMDB numbers real seasons starting at 1 (0 is reserved
                // for "Specials"). Specials are excluded here — they are
                // not part of the show's normal episode structure and
                // OneDebrid has no UI concept for them yet.
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
        // Not implemented — see class doc comment. Honestly signals
        // "this capability doesn't exist yet" rather than pretending to
        // have looked something up.
        ProviderError.ServiceUnavailable.asFailure()

    override suspend fun searchMedia(query: String): ProviderResult<List<Media>> =
        try {
            val response = api.searchMulti(query = query)
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

    // --- DTO -> domain mapping ---

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

    /**
     * Returns null for entries that cannot be mapped to a usable Media —
     * in practice, this means a "person" result slipping through despite
     * the mediaType filter in searchMedia() (defensive; should not
     * happen), or a result missing both title and name.
     */
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
            // Never available from search/multi — see class doc comment.
            imdbId = null,
            tmdbId = id,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            rating = voteAverage,
            genres = emptyList() // search/multi returns genre_ids, not names; not resolved here
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