package dev.typetype.server

import dev.typetype.server.services.toCommentImage
import dev.typetype.server.services.toVoiceReply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import org.schabi.newpipe.extractor.comments.CommentAudioAttachment
import org.schabi.newpipe.extractor.comments.CommentsInfoItem

class CommentMediaMappersTest {

    private fun image(url: String, size: Int) = Image(url, size, size, ResolutionLevel.UNKNOWN)

    @Test
    fun `comment images keep the largest animated variant or the reason it is missing`() {
        val gif = CommentsInfoItem(0, "https://www.youtube.com/watch?v=x", "a").apply {
            pictures = listOf(image("https://yt3.ggpht.com/g=s100", 100), image("https://yt3.ggpht.com/g=s352", 352))
            pictureAnimation = CommentsInfoItem.PictureAnimation.ANIMATED
        }
        val missing = CommentsInfoItem(0, "https://www.youtube.com/watch?v=x", "b").apply {
            pictureUnavailableText = "This GIF is not available"
        }

        assertEquals("https://yt3.ggpht.com/g=s352", gif.toCommentImage()?.url)
        assertTrue(gif.toCommentImage()?.animated == true)
        assertNull(missing.toCommentImage()?.url)
        assertEquals("This GIF is not available", missing.toCommentImage()?.unavailableText)
        assertNull(CommentsInfoItem(0, "https://www.youtube.com/watch?v=x", "c").toCommentImage())
    }

    @Test
    fun `voice replies are playable only with a media url`() {
        val playable = CommentsInfoItem(0, "https://www.youtube.com/watch?v=x", "a").apply {
            audioAttachment = CommentAudioAttachment("m1", "https://www.youtube.com/watch?v=m1", 13_049, "Thanks!", true, "")
        }
        val transcriptOnly = CommentsInfoItem(0, "https://www.youtube.com/watch?v=x", "b").apply {
            audioAttachment = CommentAudioAttachment("", "", -1, "Thanks!", true, "Voice reply unavailable")
        }

        assertEquals(13_049L, playable.toVoiceReply()?.durationMs)
        assertTrue(playable.toVoiceReply()?.playable == true)
        assertNull(playable.toVoiceReply()?.unavailableText)
        assertFalse(transcriptOnly.toVoiceReply()?.playable == true)
        assertEquals(0L, transcriptOnly.toVoiceReply()?.durationMs)
        assertEquals("Voice reply unavailable", transcriptOnly.toVoiceReply()?.unavailableText)
    }
}
