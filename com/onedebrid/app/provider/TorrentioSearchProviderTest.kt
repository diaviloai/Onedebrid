@Test
fun torrentioSearch_missingInfoHash_extractsHashFromUrlFallback() = runTest {
    // 1. Queue a Torrentio response where 'infoHash' is missing, 
    // but the torrent hash is present in the stream URL path/query parameter.
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

    // 2. Perform search
    val result = torrentioProvider.searchByMedia(media, SearchFilters())

    // 3. Verify success and check that the extracted hash matches expectedHash
    assertThat(result).isInstanceOf(ProviderResult.Success::class.java)
    val candidates = (result as ProviderResult.Success).data
    assertThat(candidates).hasSize(1)

    val candidate = candidates.first()
    assertThat(candidate.hash).isEqualTo(expectedHash)
}
