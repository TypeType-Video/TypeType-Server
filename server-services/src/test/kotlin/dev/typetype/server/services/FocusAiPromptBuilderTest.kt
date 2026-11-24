package dev.typetype.server.services

import dev.typetype.server.ai.AiImageInput
import dev.typetype.server.ai.AiResponseFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusAiPromptBuilderTest {
    @Test
    fun `includes title description channel user reason thumbnail and json score schema`() {
        val snapshot = FocusVideoSnapshot(
            title = "Linear Algebra Lecture 5",
            description = "Matrix spaces and nullspace examples",
            thumbnailUrl = "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            uploaderUrl = "https://www.youtube.com/channel/UCother",
            uploaderName = "Course Channel",
            duration = 1_800L,
        )
        val image = AiImageInput("image/jpeg", "AQID")
        val request = FocusAiPromptBuilder.build(
            kind = FocusAllowOnceKind.OUTSIDE_GROUP,
            snapshot = snapshot,
            reason = "Reviewing nullspace before homework",
            thumbnail = image,
        )

        val systemPrompt = request.systemPrompt.orEmpty()
        assertTrue(systemPrompt.contains("Strict Focus Mode"))
        assertTrue(systemPrompt.contains("OUTSIDE the user's study group"))
        assertTrue(systemPrompt.contains("Focus-Mode Legitimacy"))
        assertTrue(systemPrompt.contains("Credibility & Video Match"))
        assertTrue(systemPrompt.contains("Honesty alone does NOT pass"))
        assertTrue(systemPrompt.contains("gossip"))
        assertTrue(systemPrompt.contains("score 0-20"))
        assertTrue(request.userPrompt.contains("Linear Algebra Lecture 5"))
        assertTrue(request.userPrompt.contains("Matrix spaces and nullspace examples"))
        assertTrue(request.userPrompt.contains("Reviewing nullspace before homework"))
        assertTrue(request.userPrompt.contains("ThumbnailAttached: true"))
        assertEquals(listOf(image), request.images)
        assertEquals(AiResponseFormat.Json(FocusAiPromptBuilder.SCORE_SCHEMA), request.responseFormat)
        assertEquals(512, request.maxOutputTokens)

        val rewatchRequest = FocusAiPromptBuilder.build(
            kind = FocusAllowOnceKind.ENTERTAINMENT_REWATCH,
            snapshot = snapshot,
            reason = "Checking the timestamp of a meme clip",
            thumbnail = null,
        )
        assertTrue(rewatchRequest.systemPrompt.orEmpty().contains("REWATCHING"))
        assertTrue(rewatchRequest.userPrompt.contains("ThumbnailAttached: false"))
        assertEquals(emptyList<AiImageInput>(), rewatchRequest.images)
    }
}
