package com.theveloper.pixelplay.data.gdrive

object GDriveConstants {
    // TODO: Replace with your Google Cloud Console OAuth2 Web Client ID
    const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"

    val AUDIO_MIME_TYPES = setOf(
        "audio/mpeg", "audio/mp3", "audio/flac", "audio/wav", "audio/x-wav",
        "audio/mp4", "audio/x-m4a", "audio/aac", "audio/ogg",
        "audio/opus", "audio/x-aiff", "audio/alac", "audio/aiff",
        "audio/x-flac", "audio/vnd.wave", "audio/midi", "audio/x-midi",
        "audio/sp-midi", "audio/x-mid"
    )
}
