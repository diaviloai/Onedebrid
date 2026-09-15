package com.onedebrid.app.provider

import com.google.common.truth.Truth.assertThat
import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.provider.debrid.realdebrid.RealDebridApi
import com.onedebrid.app.provider.debrid.realdebrid.RealDebridProvider
import com.onedebrid.app.provider.metadata.tmdb.TmdbApi
import com.onedebrid.app.provider.metadata.tmdb.TmdbMetadataProvider
import com.onedebrid.app.provider.search.SearchFilters
import com.onedebrid.app.provider.search.torrentio.TorrentioApi
import com.onedebrid.app.provider.search.torrentio.TorrentioSearchProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MediaDiscoveryIntegrationTest {

    @get:Rule
    val mockWebServerRule = MockWebServerRule()

    private lateinit var tmdbProvider: TmdbMetadataProvider
    private lateinit var torrentioProvider: TorrentioSearchProvider
    private lateinit var realDebridProvider: RealDebridProvider

    @Before
    fun setup() {
        val tmdbApi = mockWebServerRule.createApi(TmdbApi::class.java)
        val torrentioApi = mockWebServerRule.createApi(TorrentioApi::class.java)
        val realDebridApi = mockWebServerRule.createApi(RealDebridApi::class.java)

        tmdbProvider = TmdbMetadataProvider(tmdbApi)
        torrentioProvider = TorrentioSearchProvider(torrentioApi)
        realDebridProvider = RealDebridProvider(realDebridApi)
    }

    @Test
    fun endToEnd_cachedMovieDiscoveryToStreamResolution_success() = runTest {
        mockWebServerRule.enqueueResponse(
            """
            {
              "streams": [
                {
                  "name": "Torrentio\n4K",
                  "title": "Movie.2026.2160p.UHD.BluRay.x265 👤 150 💾 12.5 GB",
                  "infoHash": "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
                }
              ]
            }
            """.trimIndent()
        )

        mockWebServerRule.enqueueResponse("""{"id":"RD_MAG_123","uri":"magnet:?xt=urn:btih:a1b2c3..."}""")
        mockWebServerRule.enqueueResponse("""{}""")
        mockWebServerRule.enqueueResponse(
            """
            {
              "id": "RD_MAG_123",
              "filename": "Movie.2026.2160p.mkv",
              "status": "downloaded",
              "links": ["https://real-debrid.com/d/LINK123"]
            }
            """.trimIndent()
        )
        mockWebServerRule.enqueueResponse(
            """
            {
              "id": "RD_UNRESTRICT_999",
              "filename": "Movie.2026.2160p.mkv",
              "filesize": 13421772800,
              "link": "https://download.real-debrid.com/cdn/Movie.2026.2160p.mkv"
            }
            """.trimIndent()
        )

        val media = Media(
            id = "550",
            imdbId = "tt0137523",
            title = "Fight Club",
            type = MediaType.MOVIE
        )

        val searchResult = torrentioProvider.searchByMedia(media, SearchFilters())
        assertThat(searchResult).isInstanceOf(ProviderResult.Success::class.java)

        val candidates = (searchResult as ProviderResult.Success).data
        assertThat(candidates).hasSize(1)
        val selectedCandidate = candidates.first()
        assertThat(selectedCandidate.hash).isEqualTo("a1b2c3d4e5f60718293a4b5c6d7e8f9012345678")

        val streamResult = realDebridProvider.resolveStream(selectedCandidate.hash!!)
        assertThat(streamResult).isInstanceOf(ProviderResult.Success::class.java)

        val streamSource = (streamResult as ProviderResult.Success).data
        assertThat(streamSource.url).isEqualTo("https://download.real-debrid.com/cdn/Movie.2026.2160p.mkv")
        assertThat(streamSource.isCached).isTrue()
    }

    @Test
    fun torrentioSearch_missingInfoHash_extractsHashFromUrlFallback() = runTest {
        val expectedHash = "9d8c7b6a5f4e3d2c1b0a9f8e7d6c5b4a3f2e1d0c"
        mockWebServerRule.enqueueResponse(
            """
            {
              "streams": [
                {
                  "name": "Torrentio\n1080p",
                  "title": "Movie.2026.1080p.WEB-DL.x264 👤 45 💾 3.2 GB",
                  "url": "https://torrentio.strem.fun/resolve/realdebrid/$expectedHash/0/movie.mkv"
                }
              ]
            }
            """.trimIndent()
        )

        val media = Media(
            id = "100",
            imdbId = "tt0111161",
            title = "The Shawshank Redemption",
            type = MediaType.MOVIE
        )

        val result = torrentioProvider.searchByMedia(media, SearchFilters())

        assertThat(result).isInstanceOf(ProviderResult.Success::class.java)
        val candidates = (result as ProviderResult.Success).data
        assertThat(candidates).hasSize(1)

        val candidate = candidates.first()
        assertThat(candidate.hash).isEqualTo(expectedHash)
    }

    @Test
    fun torrentioSearch_tvShowWithSeasonAndEpisode_formatsPathCorrectly() = runTest {
        mockWebServerRule.enqueueResponse(
            """
            {
              "streams": [
                {
                  "name": "Torrentio\n1080p",
                  "title": "Breaking.Bad.S01E04.1080p.WEB-DL 👤 80 💾 1.5 GB",
                  "infoHash": "e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0"
                }
              ]
            }
            """.trimIndent()
        )

        val media = Media(
            id = "1396",
            imdbId = "tt0903747",
            title = "Breaking Bad",
            type = MediaType.TV_SHOW
        )

        val filters = SearchFilters(season = 1, episode = 4)
        val result = torrentioProvider.searchByMedia(media, filters)

        assertThat(result).isInstanceOf(ProviderResult.Success::class.java)

        val recordedRequest = mockWebServerRule.takeRequest()
        assertThat(recordedRequest).isNotNull()
        assertThat(recordedRequest!!.path).contains("tt0903747:1:4")
    }

    @Test
    fun realDebrid_unauthorizedHttp401_returnsProviderErrorAuthentication() = runTest {
        // Enqueue 401 Unauthorized response from Real-Debrid API
        mockWebServerRule.enqueueResponse(
            body = """{"error": "bad_token", "error_code": 8}""",
            code = 401
        )

        val result = realDebridProvider.resolveStream("a1b2c3d4e5f60718293a4b5c6d7e8f9012345678")

        assertThat(result).isInstanceOf(ProviderResult.Error::class.java)
        val error = (result as ProviderResult.Error).error
        assertThat(error).isInstanceOf(ProviderError.AuthenticationFailed::class.java)
    }
}
