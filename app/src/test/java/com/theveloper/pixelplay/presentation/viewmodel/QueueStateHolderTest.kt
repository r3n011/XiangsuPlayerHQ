package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.MainCoroutineExtension
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.StorageFilter
import com.theveloper.pixelplay.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Covers the shuffle/play orchestration extracted from PlayerViewModel into QueueStateHolder
 * during Pass 2. The ViewModel delegates to these methods via the callback bundles; here we
 * exercise the real holder with a mocked [MusicRepository] and capturing callbacks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class QueueStateHolderTest {

    private val musicRepository: MusicRepository = mockk(relaxed = true)

    private fun holder() = QueueStateHolder(musicRepository)

    private fun song(id: String, title: String = "Song $id", track: Int = 0, disc: Int? = null) = Song(
        id = id,
        title = title,
        artist = "Artist",
        genre = "Rock",
        albumArtUriString = null,
        artistId = 1L,
        albumId = 1L,
        contentUriString = "content://dummy/$id",
        duration = 180_000L,
        bitrate = null,
        sampleRate = null,
        album = "Album",
        path = "path",
        mimeType = "audio/mpeg",
        trackNumber = track,
        discNumber = disc
    )

    private val song1 = song("1")
    private val song2 = song("2")
    private val song3 = song("3")

    private class CapturingShuffleCallbacks(
        scope: kotlinx.coroutines.CoroutineScope,
        storageFilter: StorageFilter = StorageFilter.ALL,
        albums: List<Album> = emptyList(),
        artists: List<Artist> = emptyList()
    ) {
        var played: Pair<List<Song>, String>? = null
        val callbacks = ShufflePlaybackCallbacks(
            scope = scope,
            currentStorageFilter = { storageFilter },
            albums = { albums },
            artists = { artists },
            playShuffled = { songs, queueName -> played = songs to queueName }
        )
    }

    @Test
    fun `shuffleAll resolves a random sample and dispatches shuffled playback`() = runTest {
        val songs = listOf(song1, song2, song3)
        coEvery { musicRepository.getRandomSongs(500) } returns songs
        val cb = CapturingShuffleCallbacks(this)

        holder().shuffleAll("All Songs (Shuffled)", cb.callbacks)
        advanceUntilIdle()

        assertEquals(songs, cb.played?.first)
        assertEquals("All Songs (Shuffled)", cb.played?.second)
    }

    @Test
    fun `shuffleAll does not dispatch when the sample is empty`() = runTest {
        coEvery { musicRepository.getRandomSongs(500) } returns emptyList()
        val cb = CapturingShuffleCallbacks(this)

        holder().shuffleAll(callbacks = cb.callbacks)
        advanceUntilIdle()

        assertNull(cb.played)
    }

    @Test
    fun `playRandom dispatches the all-songs shuffled queue`() = runTest {
        val songs = listOf(song3, song1)
        coEvery { musicRepository.getRandomSongs(500) } returns songs
        val cb = CapturingShuffleCallbacks(this)

        holder().playRandom(cb.callbacks)
        advanceUntilIdle()

        assertEquals(songs, cb.played?.first)
        assertEquals("All Songs (Shuffled)", cb.played?.second)
    }

    @Test
    fun `shuffleFavorites resolves favorites for the active storage filter`() = runTest {
        val favorites = listOf(song1, song2)
        coEvery { musicRepository.getFavoriteSongsOnce(StorageFilter.ONLINE) } returns favorites
        val cb = CapturingShuffleCallbacks(this, storageFilter = StorageFilter.ONLINE)

        holder().shuffleFavorites(cb.callbacks)
        advanceUntilIdle()

        assertEquals(favorites, cb.played?.first)
        assertEquals("Liked Songs (Shuffled)", cb.played?.second)
    }

    @Test
    fun `shuffleRandomAlbum picks an album and dispatches its songs under the album name`() = runTest {
        val album = Album(
            id = 77L,
            title = "Album Roulette",
            artist = "Artist A",
            year = 2024,
            dateAdded = 0L,
            albumArtUriString = null,
            songCount = 2
        )
        val albumSongs = listOf(song1, song2)
        every { musicRepository.getSongsForAlbum(album.id) } returns flowOf(albumSongs)
        val cb = CapturingShuffleCallbacks(this, albums = listOf(album))

        holder().shuffleRandomAlbum(cb.callbacks)
        advanceUntilIdle()

        assertEquals(albumSongs, cb.played?.first)
        assertEquals("Album Roulette", cb.played?.second)
    }

    @Test
    fun `shuffleRandomAlbum is a no-op when there are no albums`() = runTest {
        val cb = CapturingShuffleCallbacks(this, albums = emptyList())

        holder().shuffleRandomAlbum(cb.callbacks)
        advanceUntilIdle()

        assertNull(cb.played)
    }

    @Test
    fun `shuffleRandomArtist picks an artist and dispatches their songs under the artist name`() = runTest {
        val artist = Artist(id = 88L, name = "Artist Roulette", songCount = 2)
        val artistSongs = listOf(song3, song1)
        every { musicRepository.getSongsForArtist(artist.id) } returns flowOf(artistSongs)
        val cb = CapturingShuffleCallbacks(this, artists = listOf(artist))

        holder().shuffleRandomArtist(cb.callbacks)
        advanceUntilIdle()

        assertEquals(artistSongs, cb.played?.first)
        assertEquals("Artist Roulette", cb.played?.second)
    }

    @Test
    fun `playAlbum orders songs by disc then track then title`() = runTest {
        val album = Album(
            id = 5L,
            title = "Ordered Album",
            artist = "Artist A",
            year = 2024,
            dateAdded = 0L,
            albumArtUriString = null,
            songCount = 3
        )
        // Intentionally out of order: disc 2 track 1, disc 1 track 2, disc 1 track 1.
        val disc2track1 = song("a", track = 1, disc = 2)
        val disc1track2 = song("b", track = 2, disc = 1)
        val disc1track1 = song("c", track = 1, disc = 1)
        every { musicRepository.getSongsForAlbum(album.id) } returns
            flowOf(listOf(disc2track1, disc1track2, disc1track1))

        var playedSongs: List<Song>? = null
        var startSong: Song? = null
        var sheetShown = false
        val cb = PlaybackSourceCallbacks(
            scope = this,
            playSongs = { songs, start, _, _ -> playedSongs = songs; startSong = start },
            showSheet = { sheetShown = true }
        )

        holder().playAlbum(album, cb)
        // playAlbum hops to Dispatchers.IO, so the virtual scheduler can't drain it; join the
        // launched child coroutine instead.
        coroutineContext.job.children.forEach { it.join() }

        assertEquals(listOf(disc1track1, disc1track2, disc2track1), playedSongs)
        assertEquals(disc1track1, startSong)
        assertEquals(true, sheetShown)
    }

    @Test
    fun `playAlbum does not dispatch when the album has no songs`() = runTest {
        val album = Album(
            id = 6L,
            title = "Empty Album",
            artist = "Artist A",
            year = 2024,
            dateAdded = 0L,
            albumArtUriString = null,
            songCount = 0
        )
        every { musicRepository.getSongsForAlbum(album.id) } returns flowOf(emptyList())

        var dispatched = false
        var sheetShown = false
        val cb = PlaybackSourceCallbacks(
            scope = this,
            playSongs = { _, _, _, _ -> dispatched = true },
            showSheet = { sheetShown = true }
        )

        holder().playAlbum(album, cb)
        coroutineContext.job.children.forEach { it.join() }

        assertEquals(false, dispatched)
        assertEquals(false, sheetShown)
    }

    @Test
    fun `playArtist dispatches the artist songs and reveals the sheet`() = runTest {
        val artist = Artist(id = 9L, name = "Some Artist", songCount = 2)
        val artistSongs = listOf(song1, song2)
        every { musicRepository.getSongsForArtist(artist.id) } returns flowOf(artistSongs)

        var playedSongs: List<Song>? = null
        var queueName: String? = null
        var sheetShown = false
        val cb = PlaybackSourceCallbacks(
            scope = this,
            playSongs = { songs, _, name, _ -> playedSongs = songs; queueName = name },
            showSheet = { sheetShown = true }
        )

        holder().playArtist(artist, cb)
        coroutineContext.job.children.forEach { it.join() }

        assertEquals(artistSongs, playedSongs)
        assertEquals("Some Artist", queueName)
        assertEquals(true, sheetShown)
    }
}
