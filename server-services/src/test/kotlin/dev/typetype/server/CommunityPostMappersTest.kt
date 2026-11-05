package dev.typetype.server

import dev.typetype.server.services.toCommunityPostItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import org.schabi.newpipe.extractor.community.CommunityPostInfoItem
import org.schabi.newpipe.extractor.community.CommunityPostInfoItem.PollChoice
import org.schabi.newpipe.extractor.community.CommunityPostInfoItem.PollType
import org.schabi.newpipe.extractor.community.CommunityPostInfoItem.PostType
import org.schabi.newpipe.extractor.stream.Description

class CommunityPostMappersTest {

    private fun image(url: String, size: Int) = Image(url, size, size, ResolutionLevel.UNKNOWN)

    private fun post(
        type: PostType,
        images: List<List<Image>> = emptyList(),
        choices: List<PollChoice> = emptyList(),
        sharedUrl: String = "",
        shared: CommunityPostInfoItem? = null,
    ) = CommunityPostInfoItem(
        0, "post-1", "https://www.youtube.com/post/post-1",
        Description("Hello <b>world</b>", Description.HTML), "Hello world",
        "Blender", "UC1", "https://www.youtube.com/channel/UC1",
        listOf(image("https://yt3.ggpht.com/a=s48", 48), image("https://yt3.ggpht.com/a=s176", 176)),
        "3 days ago", null,
        "1.2K", 1200, "", CommunityPostInfoItem.NO_COUNT,
        type, images, choices, PollType.IMAGE, "", "4.5K votes", 4500,
        sharedUrl, "Making of", listOf(image("https://i.ytimg.com/vi/x/hq.jpg", 360)), shared,
    )

    @Test
    fun `keeps the largest variant of each image and drops unknown counts`() {
        val item = post(
            PostType.IMAGE,
            images = listOf(
                listOf(image("https://yt3.ggpht.com/i1=s288", 288), image("https://yt3.ggpht.com/i1=s1080", 1080)),
                listOf(image("https://yt3.ggpht.com/i2=s640", 640)),
            ),
        ).toCommunityPostItem()

        assertEquals("image", item.type)
        assertEquals(listOf("https://yt3.ggpht.com/i1=s1080", "https://yt3.ggpht.com/i2=s640"), item.images)
        assertEquals("https://yt3.ggpht.com/a=s176", item.authorAvatarUrl)
        assertEquals(1200L, item.likeCount)
        assertNull(item.commentCount)
        assertEquals("Hello <b>world</b>", item.text)
        assertNull(item.poll)
        assertNull(item.video)
    }

    @Test
    fun `maps polls, shared videos and reposts`() {
        val original = post(PostType.VIDEO, sharedUrl = "https://www.youtube.com/watch?v=x")
        val item = post(
            PostType.SHARED_POST,
            choices = listOf(PollChoice("Yes", listOf(image("https://yt3.ggpht.com/c=s200", 200))), PollChoice("No", emptyList())),
            shared = original,
        ).toCommunityPostItem()

        assertEquals("shared_post", item.type)
        assertEquals("image", item.poll?.type)
        assertEquals(listOf("Yes", "No"), item.poll?.choices?.map { it.text })
        assertEquals("https://yt3.ggpht.com/c=s200", item.poll?.choices?.first()?.imageUrl)
        assertEquals(4500L, item.poll?.voteCount)
        assertEquals("video", item.sharedPost?.type)
        assertEquals("https://www.youtube.com/watch?v=x", item.sharedPost?.video?.url)
        assertEquals("https://i.ytimg.com/vi/x/hq.jpg", item.sharedPost?.video?.thumbnailUrl)
    }
}
