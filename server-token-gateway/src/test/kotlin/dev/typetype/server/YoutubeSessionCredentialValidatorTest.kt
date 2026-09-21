package dev.typetype.server

import dev.typetype.server.services.YoutubeSessionCredentialValidator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoutubeSessionCredentialValidatorTest {
    @Test
    fun `validates YouTube session cookie and po token shape`() {
        assertTrue(YoutubeSessionCredentialValidator.isValid("SID=abc; SAPISID=def", "po-token-value"))
        assertTrue(
            YoutubeSessionCredentialValidator.isValid(
                "__Secure-3PSID=abc; __Secure-3PAPISID=def",
                "po-token-value",
            ),
        )
        assertFalse(YoutubeSessionCredentialValidator.isValid("SAPISID=def", "po-token-value"))
        assertFalse(YoutubeSessionCredentialValidator.isValid("SID=abc", "po-token-value"))
        assertFalse(YoutubeSessionCredentialValidator.isValid("SID=abc", "short"))
    }
}
