package com.dragplayer.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ---------- Colors ----------
private val BgTop = Color(0xFF0E0B1F)
private val BgBottom = Color(0xFF05050B)
private val Violet = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF22D3EE)
private val Pink = Color(0xFFF472B6)
private val Glass = Color.White.copy(alpha = 0.06f)

private val SmoothDark = darkColorScheme(
    primary = Violet,
    secondary = Cyan,
    tertiary = Pink,
    background = BgBottom,
    surface = BgBottom,
    onBackground = Color.White,
    onSurface = Color.White,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            // Dark theme is always on (default)
            MaterialTheme(colorScheme = SmoothDark) {
                Surface(modifier = Modifier.fillMaxSize(), color = BgBottom) {
                    PlayerApp()
                }
            }
        }
    }
}

data class MediaFile(
    val id: String,
    val title: String,
    val durationMs: Long,
    val uri: Uri,
    val isAudio: Boolean,
) {
    fun toMediaItem(): MediaItem =
        MediaItem.Builder().setMediaId(id).setUri(uri).build()
}

// ---------- Permission gate ----------
@Composable
fun PlayerApp() {
    val context = LocalContext.current
    val perms = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun hasAll(): Boolean = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(hasAll()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = perms.all { p -> result[p] == true } }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(perms)
    }

    if (granted) {
        MainScreen()
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedLogo(logoSize = 120.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    "Smooth Player",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Allow access to your videos and music so Smooth Player can find and play them.",
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { launcher.launch(perms) },
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                    shape = RoundedCornerShape(24.dp),
                ) { Text("Allow access", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
            }
        }
    }
}

// ---------- Main screen ----------
@Composable
fun MainScreen() {
    val context = LocalContext.current

    var videos by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var audios by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }          // 0 = videos, 1 = music
    var queueTab by remember { mutableIntStateOf(-1) }    // which list is loaded in the player
    var playingId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val exo = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                playingId = mediaItem?.mediaId
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(Unit) {
        videos = withContext(Dispatchers.IO) { loadMedia(context, audio = false) }
        audios = withContext(Dispatchers.IO) { loadMedia(context, audio = true) }
        loaded = true
    }

    // Save the custom order whenever it changes
    LaunchedEffect(videos) { if (loaded) saveOrder(context, false, videos) }
    LaunchedEffect(audios) { if (loaded) saveOrder(context, true, audios) }

    fun listFor(t: Int) = if (t == 0) videos else audios
    fun setList(t: Int, l: List<MediaFile>) {
        if (t == 0) videos = l else audios = l
    }

    fun playItem(t: Int, item: MediaFile) {
        val list = listFor(t)
        val index = list.indexOfFirst { it.id == item.id }
        if (index < 0) return
        if (queueTab != t) {
            exo.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
            exo.prepare()
            queueTab = t
        } else {
            exo.seekTo(index, 0L)
        }
        playingId = item.id
        exo.play()
    }

    fun moveItem(t: Int, from: Int, to: Int) {
        val list = listFor(t)
        if (from !in list.indices || to !in list.indices) return
        setList(t, list.toMutableList().apply { add(to, removeAt(from)) })
        if (queueTab == t) exo.moveMediaItem(from, to)
    }

    // Video dragging: put any item right after the one that is playing
    fun playNext(t: Int, item: MediaFile) {
        val list = listFor(t)
        val from = list.indexOfFirst { it.id == item.id }
        if (from < 0) return
        if (queueTab != t) {
            Toast.makeText(context, "Start playing something in this tab first", Toast.LENGTH_SHORT).show()
            return
        }
        val current = list.indexOfFirst { it.id == playingId }
        if (current < 0 || from == current) return
        val to = if (from < current) current else current + 1
        moveItem(t, from, to)
    }

    val current = (videos + audios).firstOrNull { it.id == playingId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
            .navigationBarsPadding(),
    ) {
        // ----- Header with animated logo in the top-right corner -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Smooth Player",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Text(
                    "${videos.size} videos  •  ${audios.size} songs",
                    fontSize = 13.sp,
                    color = Cyan,
                )
            }
            AnimatedLogo(logoSize = 56.dp)
        }

        SegmentedTabs(
            selected = tab,
            labels = listOf("Videos", "Music"),
            onSelect = { tab = it },
        )

        Spacer(Modifier.height(12.dp))

        // ----- Player area -----
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            if (current != null) {
                if (current.isAudio) {
                    AudioNowPlaying(current.title, isPlaying, exo)
                } else {
                    AndroidView(
                        factory = { ctx ->
                            (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView)
                                .apply { player = exo }
                        },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black),
                    )
                }
            }
        }

        if (current != null) Spacer(Modifier.height(8.dp))

        // ----- Lists -----
        if (tab == 0) {
            MediaList(
                items = videos,
                loaded = loaded,
                emptyText = "No videos found on this phone",
                playingId = playingId,
                isPlaying = isPlaying,
                onTap = { playItem(0, it) },
                onPlayNext = { playNext(0, it) },
                onMove = { from, to -> moveItem(0, from, to) },
            )
        } else {
            MediaList(
                items = audios,
                loaded = loaded,
                emptyText = "No music found on this phone",
                playingId = playingId,
                isPlaying = isPlaying,
                onTap = { playItem(1, it) },
                onPlayNext = { playNext(1, it) },
                onMove = { from, to -> moveItem(1, from, to) },
            )
        }
    }
}

// ---------- Animated logo ----------
@Composable
fun AnimatedLogo(logoSize: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "logo")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing)),
        label = "rotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val glow by transition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    Box(modifier = modifier.size(logoSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            // soft pulsing glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Violet.copy(alpha = glow), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
            )
            // rotating neon ring
            rotate(rotation) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(Cyan, Violet, Pink, Cyan)),
                    radius = radius - 3.dp.toPx(),
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = "Smooth Player logo",
            modifier = Modifier
                .size(logoSize * 0.56f)
                .scale(pulse),
        )
    }
}

// ---------- Tabs ----------
@Composable
fun SegmentedTabs(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Glass)
            .padding(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val bg by animateColorAsState(
                if (i == selected) Violet else Color.Transparent,
                animationSpec = tween(300),
                label = "tabBg",
            )
            val fg by animateColorAsState(
                if (i == selected) Color.White else Color.White.copy(alpha = 0.6f),
                animationSpec = tween(300),
                label = "tabFg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = fg, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------- Audio now playing ----------
@Composable
fun AudioNowPlaying(title: String, isPlaying: Boolean, exo: ExoPlayer) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Violet.copy(alpha = 0.35f), Cyan.copy(alpha = 0.18f), Pink.copy(alpha = 0.22f))
                )
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EqualizerBars(active = isPlaying, color = Cyan, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AndroidView(
            factory = { ctx ->
                PlayerControlView(ctx).apply {
                    player = exo
                    setShowTimeoutMs(0)
                    show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
fun EqualizerBars(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val h1 by transition.animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "h1"
    )
    val h2 by transition.animateFloat(
        0.5f, 1f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "h2"
    )
    val h3 by transition.animateFloat(
        0.2f, 0.9f, infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "h3"
    )
    Canvas(modifier = modifier) {
        val w = size.width / 5f
        val heights = if (active) listOf(h1, h2, h3) else listOf(0.3f, 0.3f, 0.3f)
        heights.forEachIndexed { i, h ->
            val barH = size.height * h
            drawRoundRect(
                color = color,
                topLeft = Offset(i * 2 * w, size.height - barH),
                size = Size(w, barH),
                cornerRadius = CornerRadius(w / 2f),
            )
        }
    }
}

// ---------- List with drag-to-sort ----------
@Composable
fun MediaList(
    items: List<MediaFile>,
    loaded: Boolean,
    emptyText: String,
    playingId: String?,
    isPlaying: Boolean,
    onTap: (MediaFile) -> Unit,
    onPlayNext: (MediaFile) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.index, to.index)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    if (loaded && items.isEmpty()) {
        Text(
            emptyText,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(24.dp),
        )
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { item ->
            ReorderableItem(reorderState, key = item.id) { isDragging ->
                val active = item.id == playingId
                val scale by animateFloatAsState(if (isDragging) 1.04f else 1f, label = "dragScale")
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) Violet.copy(alpha = 0.22f) else Glass
                    ),
                    border = if (active) BorderStroke(
                        1.dp, Brush.horizontalGradient(listOf(Cyan, Violet, Pink))
                    ) else null,
                    elevation = CardDefaults.cardElevation(if (isDragging) 14.dp else 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onTap(item) },
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Thumb(item, Modifier.size(60.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (active) {
                                    EqualizerBars(
                                        active = isPlaying,
                                        color = Cyan,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    formatDuration(item.durationMs),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                )
                            }
                        }
                        IconButton(onClick = { onPlayNext(item) }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Play next", tint = Cyan)
                        }
                        // Long-press this handle and drag to sort
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag to sort",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .longPressDraggableHandle()
                                .padding(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Thumb(item: MediaFile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, item.id) {
        value = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver
                        .loadThumbnail(item.uri, android.util.Size(256, 256), null)
                        .asImageBitmap()
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Violet.copy(alpha = 0.45f), Cyan.copy(alpha = 0.30f)))),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                if (item.isAudio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

// ---------- Helpers ----------
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun loadMedia(context: Context, audio: Boolean): List<MediaFile> {
    val result = mutableListOf<MediaFile>()
    val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val idName = if (audio) MediaStore.Audio.Media._ID else MediaStore.Video.Media._ID
    val nameName = if (audio) MediaStore.Audio.Media.DISPLAY_NAME else MediaStore.Video.Media.DISPLAY_NAME
    val durName = if (audio) MediaStore.Audio.Media.DURATION else MediaStore.Video.Media.DURATION
    val dateName = if (audio) MediaStore.Audio.Media.DATE_ADDED else MediaStore.Video.Media.DATE_ADDED
    val prefix = if (audio) "a" else "v"

    context.contentResolver.query(
        collection, arrayOf(idName, nameName, durName), null, null, "$dateName DESC"
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(idName)
        val nameCol = c.getColumnIndexOrThrow(nameName)
        val durCol = c.getColumnIndexOrThrow(durName)
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            result += MediaFile(
                id = "$prefix$id",
                title = (c.getString(nameCol) ?: "Untitled").substringBeforeLast('.'),
                durationMs = c.getLong(durCol),
                uri = ContentUris.withAppendedId(collection, id),
                isAudio = audio,
            )
        }
    }

    val saved = context.getSharedPreferences("smoothplayer", Context.MODE_PRIVATE)
        .getString(if (audio) "order_audio" else "order_video", "").orEmpty()
        .split(",").filter { it.isNotEmpty() }
    val rank = saved.withIndex().associate { it.value to it.index }
    // Items you already sorted keep their place; brand-new files go on top
    return result.sortedBy { rank[it.id] ?: -1 }
}

private fun saveOrder(context: Context, audio: Boolean, list: List<MediaFile>) {
    context.getSharedPreferences("smoothplayer", Context.MODE_PRIVATE)
        .edit()
        .putString(if (audio) "order_audio" else "order_video", list.joinToString(",") { it.id })
        .apply()
}
