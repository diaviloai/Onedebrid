package com.onedebrid.app.test

import com.google.common.truth.Truth.assertThat
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
        // 1. Queue Torrentio streams response containing a valid infoHash
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

        // 2. Queue Real-Debrid API sequence: addMagnet -> selectFiles -> getTorrentInfo -> unrestrictLink
        mockWebServerRule.enqueueResponse("""{"id":"RD_MAG_123","uri":"magnet:?xt=urn:btih:a1b2c3..."}""")
        mockWebServerRule.enqueueResponse("""{}""") // selectFiles OK
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

        // Step A: Search candidates via Torrentio for a movie with IMDb ID
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

        // Step B: Resolve candidate stream via Real-Debrid
        val streamResult = realDebridProvider.resolveStream(selectedCandidate.hash!!)
        assertThat(streamResult).isInstanceOf(ProviderResult.Success::class.java)

        val streamSource = (streamResult as ProviderResult.Success).data
        assertThat(streamSource.url).isEqualTo("https://download.real-debrid.com/cdn/Movie.2026.2160p.mkv")
        assertThat(streamSource.isCached).isTrue()
    }
}
