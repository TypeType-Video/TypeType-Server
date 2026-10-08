package dev.typetype.server

import dev.typetype.server.services.NewPipeInitializer
import dev.typetype.server.services.TypetypeYoutubeSessionPoTokenProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder

class NewPipeInitializerTest {
    @Test
    fun `default initialization preserves the configured token provider`() {
        val initialized = NewPipeInitializer.javaClass.getDeclaredField("initialized")
        val decoderUrl = NewPipeInitializer.javaClass.getDeclaredField("decoderServiceUrl")
        initialized.isAccessible = true
        decoderUrl.isAccessible = true
        val previousInitialized = initialized.get(null)
        val previousUrl = decoderUrl.get(null)
        mockkStatic(NewPipe::class)
        mockkStatic(YoutubeApiDecoder::class)
        mockkObject(TypetypeYoutubeSessionPoTokenProvider)
        try {
            initialized.set(null, false)
            decoderUrl.set(null, null)
            every { NewPipe.setYoutubePlayerClient(any()) } just Runs
            every { NewPipe.setYoutubeSessionPoTokenProvider(any()) } just Runs
            every { NewPipe.init(any<Downloader>()) } just Runs
            every { YoutubeApiDecoder.setLocalDecoder(any()) } just Runs
            every { TypetypeYoutubeSessionPoTokenProvider.configureAuthenticatedProvider(any()) } just Runs

            NewPipeInitializer.init("http://127.0.0.1:1")
            NewPipeInitializer.init()
            NewPipeInitializer.init()

            verify(exactly = 1) {
                TypetypeYoutubeSessionPoTokenProvider.configureAuthenticatedProvider(any())
            }
        } finally {
            initialized.set(null, previousInitialized)
            decoderUrl.set(null, previousUrl)
            unmockkObject(TypetypeYoutubeSessionPoTokenProvider)
            unmockkStatic(YoutubeApiDecoder::class)
            unmockkStatic(NewPipe::class)
        }
    }
}
