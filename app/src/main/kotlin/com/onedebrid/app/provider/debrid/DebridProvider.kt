package com.onedebrid.app.provider.debrid

import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource

interface DebridProvider {
    val id: String
    val displayName: String
    
    /**
     * Takes a list of stream candidates and returns a map indicating which 
     * hashes are instantly available (cached) on the Debrid service.
     */
    suspend fun checkCacheStatus(candidates: List<StreamCandidate>): ProviderResult<Map<String, Boolean>>
    
    /**
     * Takes a single candidate, adds it to the Debrid service, selects the video file,
     * and unrestricts the link to return a playable StreamSource.
     */
    suspend fun resolveStream(candidate: StreamCandidate): ProviderResult<StreamSource>
}