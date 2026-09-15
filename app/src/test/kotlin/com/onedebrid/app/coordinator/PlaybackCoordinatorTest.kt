package com.onedebrid.app.coordinator

import com.google.common.truth.Truth.assertThat
import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.provider.debrid.DebridProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {

    private lateinit var mockDebridProvider: FakeDebridProvider
    private lateinit var playbackCoordinator: PlaybackCoordinator

    @Before
    fun setup() {
        mockDebridProvider = FakeDebridProvider()
        playbackCoordinator = PlaybackCoordinator(mockDebridProvider)
    }

    @Test
    fun loadStream_successfulResolution_updatesStateToReady() = runTest {
        val testHash = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
        val expectedUrl = "https://download.real-debrid.com/cdn/movie.mkv"
        
        mockDebridProvider.nextResult = ProviderResult.Success(
            StreamSource(
                url = expectedUrl,
                filename = "movie.mkv",
                filesize = 1024L,
                isCached = true
            )
        )

        val media = Media(
            id = "550",
            imdbId = "tt0137523",
            title = "Fight Club",
            type = MediaType.MOVIE
        )

        playbackCoordinator.loadStream(media, testHash)

        val state = playbackCoordinator.state.value
        assertThat(state.resolvedUrl).isEqualTo(expectedUrl)
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun loadStream_providerFailure_updatesStateWithError() = runTest {
        val testHash = "invalid_or_uncached_hash"
        mockDebridProvider.nextResult = ProviderResult.Error(
            ProviderError.NoStreamsFound("Stream unavailable or uncached")
        )

        val media = Media(
            id = "550",
            imdbId = "tt0137523",
            title = "Fight Club",
            type = MediaType.MOVIE
        )

        playbackCoordinator.loadStream(media, testHash)

        val state = playbackCoordinator.state.value
        assertThat(state.resolvedUrl).isNull()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNotNull()
    }

    @Test
    fun updatePlaybackPosition_savesResumePositionCorrectly() = runTest {
        val mediaId = "550"
        val positionMs = 45000L // 45 seconds in

        playbackCoordinator.updatePosition(mediaId, positionMs)

        val savedPosition = playbackCoordinator.getResumePosition(mediaId)
        assertThat(savedPosition).isEqualTo(positionMs)
    }
}

/**
 * Lightweight fake provider implementation for isolated coordinator testing.
 */
private class FakeDebridProvider : DebridProvider {
    var nextResult: ProviderResult<StreamSource> = ProviderResult.Error(
        ProviderError.Unknown("No fake result configured")
    )

    override suspend fun resolveStream(hash: String): ProviderResult<StreamSource> {
        return nextResult
    }
}
