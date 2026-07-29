package com.theveloper.pixelplay.data.jellyfin.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JellyfinCredentialsTest {

    @Test
    fun `connectionValidationError accepts normalized https urls`() {
        val credentials = JellyfinCredentials(
            serverUrl = " https://media.example.com/jellyfin/ ",
            username = "user",
            password = "pass"
        )

        assertNull(credentials.connectionValidationError())
        assertEquals("https://media.example.com/jellyfin", credentials.normalizedServerUrl)
    }

    @Test
    fun `connectionValidationError accepts local http urls`() {
        val localUrls = listOf(
            "http://192.168.1.20:8096",
            "http://mynas:8096",
            "http://nas.lan:8096",
            "http://jellyfin.home.arpa:8096",
            "http://[::1]:8096",
            "http://100.101.102.103:8096"
        )

        localUrls.forEach { url ->
            val credentials = JellyfinCredentials(
                serverUrl = url,
                username = "user",
                password = "pass"
            )

            assertNull(credentials.connectionValidationError(), "Expected $url to be accepted")
        }
    }

    @Test
    fun `connectionValidationError rejects remote http urls`() {
        val credentials = JellyfinCredentials(
            serverUrl = "http://media.example.com",
            username = "user",
            password = "pass"
        )

        assertEquals(
            "Use https:// for remote Jellyfin servers. HTTP is only allowed for local network addresses.",
            credentials.connectionValidationError()
        )
    }

    @Test
    fun `connectionValidationError rejects embedded credentials`() {
        val credentials = JellyfinCredentials(
            serverUrl = "https://user:secret@media.example.com",
            username = "user",
            password = "pass"
        )

        assertEquals(
            "Server URL must not contain embedded credentials",
            credentials.connectionValidationError()
        )
    }
}
