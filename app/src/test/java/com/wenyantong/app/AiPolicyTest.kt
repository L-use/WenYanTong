package com.wenyantong.app

import com.wenyantong.app.feature.ai.*
import org.junit.Assert.*
import org.junit.Test

class AiPolicyTest {
    @Test fun endpointsHandleRootAndCompleteUrlsWithoutDuplicatingPaths() {
        assertEquals("https://example.com/v1/chat/completions",AiPolicy.endpoint(AiSettings(baseUrl="https://example.com/v1/",model="test")))
        assertEquals("https://example.com/v1/chat/completions",AiPolicy.endpoint(AiSettings(baseUrl="https://example.com/v1/chat/completions",model="test")))
        assertEquals("https://example.com/v1/messages",AiPolicy.endpoint(AiSettings(AiProtocol.CLAUDE,"https://example.com/v1","test")))
    }
    @Test fun unsafeEndpointsNeverReceiveAnApiKey() {
        for(url in listOf("http://example.com/v1","https://user:pass@example.com/v1","https://example.com/v1?key=secret","https://example.com/#key","https:///v1")) {
            try { AiPolicy.endpoint(AiSettings(baseUrl=url,model="test"));fail(url) } catch(expected: IllegalArgumentException) { }
        }
    }
}
