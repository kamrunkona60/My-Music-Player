package com.example.mymusicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

data class Song(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long)

class MainActivity : ComponentActivity() {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    override fun onStart() {
        super.onStart()
        future = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        future?.addListener({ controller = future?.get() }, mainExecutor)
    }
    override fun onStop() { controller = null; future?.let { MediaController.releaseFuture(it) }; future = null; super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MusicApp(this) } }
    fun play(songs: List<Song>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(songs.map { MediaItem.Builder().setUri(it.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).build()).build() }, index, 0L)
        c.prepare(); c.play()
    }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun isPlaying() = controller?.isPlaying == true
    fun title() = controller?.mediaMetadata?.title?.toString() ?: "Choose a song"
    fun artist() = controller?.mediaMetadata?.artist?.toString() ?: "Your music library"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicApp(activity: MainActivity) {
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var granted by remember { mutableStateOf(hasPermission(activity)) }
    var tab by remember { mutableIntStateOf(0) }
    var dark by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("Choose a song") }
    var artist by remember { mutableStateOf("Your music library") }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var eq by remember { mutableStateOf(false) }
    var band by remember { mutableStateOf(List(5) { 0 }) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (it) songs = loadSongs(activity) }
    LaunchedEffect(Unit) { if (granted) songs = loadSongs(activity) else launcher.launch(permissionName()) }
    LaunchedEffect(Unit) { while (true) { playing = activity.isPlaying(); title = activity.title(); artist = activity.artist(); kotlinx.coroutines.delay(600) } }
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFB8F36B), background = Color(0xFF10120E), surface = Color(0xFF1B1E18)) else lightColorScheme(primary = Color(0xFF456A16))) {
        Scaffold(topBar = { TopAppBar(title = { Column { Text("LemonTunes", fontWeight = FontWeight.Black); Text("YOUR SOUND. YOUR SPACE.", style = MaterialTheme.typography.labelSmall) } }, actions = { IconButton(onClick = { dark = !dark }) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Theme") } }) }, bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().clickable { tab = 2 }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Album, null, modifier = Modifier.size(38.dp)); Column(Modifier.weight(1f).padding(horizontal = 8.dp)) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold); Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                    IconButton(onClick = { activity.previous() }) { Icon(Icons.Default.SkipPrevious, "Previous") }; IconButton(onClick = { activity.toggle(); playing = !playing }) { Icon(if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, "Play pause", modifier = Modifier.size(34.dp)) }; IconButton(onClick = { activity.next() }) { Icon(Icons.Default.SkipNext, "Next") }
                }
                NavigationBar { NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Library") }); NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.QueueMusic, null) }, label = { Text("Playlist") }); NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Equalizer, null) }, label = { Text("Player") }) }
            }
        }) { pad ->
            when (tab) {
                0, 1 -> Column(Modifier.padding(pad).fillMaxSize().padding(18.dp)) {
                    Text(if (tab == 0) "Your library" else "Playlist", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("${songs.size} tracks on this device", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(14.dp))
                    if (!granted) Button(onClick = { launcher.launch(permissionName()) }) { Text("Allow audio access") }
                    else if (songs.isEmpty()) Text("No music found. Add audio files to your phone.")
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) { itemsIndexed(songs, key = { _, s -> s.id }) { i, s -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).clickable { activity.play(songs, i) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(32.dp)); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(s.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1) }; Text(time(s.duration), style = MaterialTheme.typography.labelSmall); Icon(Icons.Default.PlayArrow, null) } } }
                }
                else -> Column(Modifier.padding(pad).fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(22.dp)); Text("NOW PLAYING", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(26.dp)); Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Album, null, modifier = Modifier.size(150.dp), tint = MaterialTheme.colorScheme.primary) }; Spacer(Modifier.height(24.dp)); Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(artist, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(22.dp)); Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { IconButton(onClick = { shuffle = !shuffle }) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }; IconButton(onClick = { activity.previous() }) { Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(36.dp)) }; IconButton(onClick = { activity.toggle(); playing = !playing }) { Icon(if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, "Play", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary) }; IconButton(onClick = { activity.next() }) { Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(36.dp)) }; IconButton(onClick = { repeat = !repeat }) { Icon(Icons.Default.Repeat, "Repeat", tint = if (repeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } }; Spacer(Modifier.height(18.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Equalizer, null); Text("Equalizer", modifier = Modifier.weight(1f).padding(start = 10.dp), fontWeight = FontWeight.Bold); Switch(eq, { eq = it }) }; if (eq) { band.forEachIndexed { i, v -> Row(verticalAlignment = Alignment.CenterVertically) { Text(listOf("60", "230", "910", "3.6k", "14k")[i], modifier = Modifier.width(44.dp)); Slider(v.toFloat(), { n -> band = band.toMutableList().also { it[i] = n.toInt() } }, valueRange = -10f..10f) } }; Text("Equalizer controls are a preview; audio effect will be added later.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

fun permissionName() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
fun hasPermission(c: Context) = Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(permissionName()) == PackageManager.PERMISSION_GRANTED
fun loadSongs(c: Context): List<Song> {
    val result = mutableListOf<Song>(); val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
    c.contentResolver.query(uri, cols, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cur ->
        val id = cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val title = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artist = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val duration = cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (cur.moveToNext()) { val n = cur.getLong(id); result.add(Song(n, cur.getString(title) ?: "Unknown title", cur.getString(artist)?.takeUnless { it == "<unknown>" } ?: "Unknown artist", Uri.withAppendedPath(uri, n.toString()), cur.getLong(duration))) }
    }; return result
}
fun time(ms: Long): String { val s = (ms.coerceAtLeast(0)) / 1000; return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }
