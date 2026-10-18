package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource

interface MediaRepository {
    suspend fun getMediaDetails(mediaId: String): RepositoryResult<Media>
    suspend fun getEpisodes(mediaId: String): RepositoryResult<List<Episode>>
    suspend fun getEpisodeById(mediaId: String, episodeId: String): RepositoryResult<Episode>
    suspend fun resolveStream(candidate: StreamCandidate): RepositoryResult<StreamSource>
    suspend fun checkCacheStatus(candidates: List<StreamCandidate>): RepositoryResult<Map<String, Boolean>>
    
    // Added includeAdult parameter
    suspend fun search(query: String, profileId: String, includeAdult: Boolean = false): RepositoryResult<List<SearchResult>>
    
    suspend fun searchStreamsByMedia(media: Media, episode: Episode?): RepositoryResult<List<StreamCandidate>>
}