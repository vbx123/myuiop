package com.subtitle.studio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF4DD0E1),
                    onPrimary = Color(0xFF00252B),
                    primaryContainer = Color(0xFF0E4A55),
                    onPrimaryContainer = Color(0xFFE6FAFD),
                    secondary = Color(0xFFFFB74D),
                    background = Color(0xFF0F1418),
                    onBackground = Color(0xFFE6EDF3),
                    surface = Color(0xFF161C22),
                    onSurface = Color(0xFFE6EDF3),
                    surfaceVariant = Color(0xFF222B33),
                    onSurfaceVariant = Color(0xFFB8C4CE),
                    error = Color(0xFFFF6B6B)
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    EditorScreen()
                }
            }
        }
    }
}

/** مخزن الترجمات */
class SubStore {
    val subs = mutableStateListOf<Sub>()
    val pending = mutableStateListOf<String>()
    val syncedIds = mutableStateListOf<Long>()
    private var counter = 1L

    private fun newId(): Long = counter++

    fun replaceAll(list: List<Sub>) {
        val fresh = list.map { it.copy(id = newId()) }
        subs.clear()
        subs.addAll(fresh)
        pending.clear()
        syncedIds.clear()
    }

    fun add(startMs: Long, endMs: Long, text: String): Sub {
        val s = Sub(newId(), startMs, endMs, text)
        val current = subs.toList().map {
            if (it.startMs < startMs && it.endMs > startMs) it.copy(endMs = startMs) else it
        }
        val sorted = (current + s).sortedBy { it.startMs }
        subs.clear()
        subs.addAll(sorted)
        return s
    }

    fun update(sub: Sub) {
        val sorted = subs.toList().map { if (it.id == sub.id) sub else it }.sortedBy { it.startMs }
        subs.clear()
        subs.addAll(sorted)
    }

    fun remove(id: Long) {
        val i = subs.indexOfFirst { it.id == id }
        if (i >= 0) subs.removeAt(i)
        syncedIds.remove(id)
    }

    fun clear() {
        subs.clear()
        pending.clear()
        syncedIds.clear()
    }
}

fun queryName(ctx: Context, uri: Uri): String {
    try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i) ?: ""
            }
        }
    } catch (_: Exception) {
    }
    return uri.lastPathSegment ?: "media"
}

fun keyFor(uri: Uri): String = "subs_" + uri.toString().hashCode()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val prefs = remember { context.getSharedPreferences("studio", Context.MODE_PRIVATE) }
    val store = remember { SubStore() }
    val exo = remember {
        ExoPlayer.Builder(context).build().also { it.setSeekParameters(SeekParameters.EXACT) }
    }

    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaName by remember { mutableStateOf("") }
    var isVideo by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Sub?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var bulk by remember { mutableStateOf("") }
    var syncStarted by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val toast: (String) -> Unit = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    fun loadMedia(uri: Uri) {
        mediaUri = uri
        mediaName = queryName(context, uri)
        val type = context.contentResolver.getType(uri) ?: ""
        isVideo = !type.startsWith("audio")
        position = 0L
        duration = 0L
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.playWhenReady = false
        prefs.edit().putString("last_uri", uri.toString()).apply()
        val saved = prefs.getString(keyFor(uri), null)
        store.replaceAll(if (saved != null) SrtUtil.parse(saved) else emptyList())
        syncStarted = false
    }

    val openMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            loadMedia(uri)
        }
    }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val data = pendingExport
        if (uri != null && data != null) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data.toByteArray(Charsets.UTF_8)) }
                toast("تم الحفظ ✓")
            } catch (e: Exception) {
                toast("فشل الحفظ")
            }
        }
        pendingExport = null
    }

    val importDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                val list = SrtUtil.parse(raw)
                if (list.isEmpty()) {
                    toast("لم أجد ترجمات صالحة في الملف")
                } else {
                    store.replaceAll(list)
                    toast("تم استيراد ${list.size} ترجمة")
                }
            } catch (e: Exception) {
                toast("تعذر قراءة الملف")
            }
        }
    }

    fun export(ext: String) {
        if (store.subs.isEmpty()) {
            toast("لا توجد ترجمات للحفظ")
            return
        }
        val list = store.subs.toList()
        pendingExport = when (ext) {
            "vtt" -> SrtUtil.toVtt(list)
            "lrc" -> SrtUtil.toLrc(list)
            else -> SrtUtil.toSrt(list)
        }
        val base = mediaName.substringBeforeLast('.', mediaName).ifEmpty { "subtitles" }
        createDoc.launch("$base.$ext")
    }

    fun togglePlay() {
        if (mediaUri == null) {
            toast("افتح ملف فيديو أو صوت أولاً")
            return
        }
        if (exo.isPlaying) {
            exo.pause()
        } else {
            if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0L)
            exo.play()
        }
    }

    fun seekBy(delta: Long) {
        val target = (exo.currentPosition + delta).coerceAtLeast(0L)
        val max = if (duration > 0) duration else Long.MAX_VALUE
        exo.seekTo(target.coerceAtMost(max))
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = exo.duration.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: PlaybackException) {
                toast("تعذر تشغيل هذا الملف")
            }
        }
        exo.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) exo.pause()
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            exo.removeListener(listener)
            activity.lifecycle.removeObserver(observer)
            exo.release()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            position = exo.currentPosition
            delay(50)
        }
    }

    LaunchedEffect(Unit) {
        val last = prefs.getString("last_uri", null)
        if (last != null) {
            val u = Uri.parse(last)
            val ok = context.contentResolver.persistedUriPermissions.any { it.uri == u }
            if (ok) {
                try {
                    loadMedia(u)
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(mediaUri) {
        snapshotFlow { store.subs.toList() }.collect { list ->
            val u = mediaUri
            if (u != null) prefs.edit().putString(keyFor(u), SrtUtil.toSrt(list)).apply()
        }
    }

    val activeSub = store.subs.firstOrNull { position >= it.startMs && position < it.endMs }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (mediaName.isEmpty()) "استوديو الترجمة" else mediaName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp
                    )
                },
                actions = {
                    TextButton(onClick = { openMedia.launch(arrayOf("video/*", "audio/*")) }) { Text("فتح ملف") }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("حفظ ▾") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("حفظ SRT") }, onClick = { menuOpen = false; export("srt") })
                            DropdownMenuItem(text = { Text("حفظ VTT") }, onClick = { menuOpen = false; export("vtt") })
                            DropdownMenuItem(text = { Text("حفظ LRC (للأغاني)") }, onClick = { menuOpen = false; export("lrc") })
                            DropdownMenuItem(text = { Text("استيراد ملف ترجمة") }, onClick = {
                                menuOpen = false
                                importDoc.launch(arrayOf("*/*"))
                            })
                            DropdownMenuItem(text = { Text("حذف كل الترجمات") }, onClick = {
                                menuOpen = false
                                confirmClear = true
                            })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ===== المشغل =====
            Box(
                Modifier.fillMaxWidth().height(210.dp).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (mediaUri != null && isVideo) {
                    AndroidView(
                        factory = { c ->
                            PlayerView(c).apply {
                                useController = false
                                this.player = exo
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (mediaUri == null) "🎬" else "🎵", fontSize = 44.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (mediaUri == null) "افتح فيديو أو ملف صوتي من هاتفك" else mediaName,
                            color = Color(0xFFB8C4CE),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (mediaUri == null) {
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { openMedia.launch(arrayOf("video/*", "audio/*")) }) { Text("فتح ملف") }
                        }
                    }
                }
                if (activeSub != null) {
                    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomCenter) {
                        Text(
                            activeSub.text,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xAA000000))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // ===== شريط التقدم =====
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LtrText(SrtUtil.fmtShort(if (dragging) (dragValue * duration).toLong() else position), 13)
                LtrText(SrtUtil.fmtShort(duration), 13)
            }
            Slider(
                value = if (dragging) dragValue else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    if (duration > 0) exo.seekTo((dragValue * duration).toLong())
                    dragging = false
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            )

            // ===== أزرار التحكم =====
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CtrlBtn("-5ث") { seekBy(-5000) }
                CtrlBtn("-1ث") { seekBy(-1000) }
                Button(
                    onClick = { togglePlay() },
                    shape = CircleShape,
                    modifier = Modifier.size(62.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (isPlaying) PauseIcon(MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل", modifier = Modifier.size(36.dp))
                }
                CtrlBtn("+1ث") { seekBy(1000) }
                CtrlBtn("+5ث") { seekBy(5000) }
                CtrlBtn("${speed}x") {
                    val next = speeds[(speeds.indexOf(speed) + 1) % speeds.size]
                    speed = next
                    exo.setPlaybackSpeed(next)
                }
            }

            Spacer(Modifier.height(6.dp))

            // ===== التبويبات =====
            TabRow(selectedTabIndex = tab) {
                listOf("كتابة بالضغط", "مزامنة نص", "القائمة (${store.subs.size})").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 13.sp) })
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> {
                        // ----- وضع الكتابة بالضغط المطوّل -----
                        var holding by remember { mutableStateOf(false) }
                        val curText by rememberUpdatedState(text)
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text("اكتب نص الترجمة") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (holding) Color(0xFFE53935) else MaterialTheme.colorScheme.primary)
                                    .pointerInput(Unit) {
                                        detectTapGestures(onPress = {
                                            val t = curText.trim()
                                            if (t.isEmpty()) {
                                                toast("اكتب نص الترجمة أولاً")
                                            } else if (mediaUri == null) {
                                                toast("افتح ملف فيديو أو صوت أولاً")
                                            } else {
                                                val s = exo.currentPosition
                                                holding = true
                                                tryAwaitRelease()
                                                holding = false
                                                val e = exo.currentPosition
                                                store.add(s, maxOf(e, s + 400), t)
                                                text = ""
                                            }
                                        })
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (holding) "● جارٍ التسجيل... ارفع إصبعك عند نهاية الجملة"
                                    else "اضغط مطوّلاً طوال نطق الجملة",
                                    color = if (holding) Color.White else MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val t = text.trim()
                                    if (t.isEmpty()) {
                                        toast("اكتب نص الترجمة أولاً")
                                    } else if (mediaUri == null) {
                                        toast("افتح ملف فيديو أو صوت أولاً")
                                    } else {
                                        val s = exo.currentPosition
                                        val len = (t.length * 65L).coerceIn(1200L, 6000L)
                                        store.add(s, s + len, t)
                                        text = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("إضافة عند الموضع الحالي (مدة تلقائية)") }
                            Text(
                                "الطريقة: شغّل الملف، اكتب الجملة، ثم اضغط مطوّلاً على الزر الأزرق من بداية نطقها حتى نهايتها. يتم تسجيل التوقيت تلقائياً بدقة الملي ثانية.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    1 -> {
                        // ----- وضع مزامنة نص جاهز (كلمات أغنية مثلاً) -----
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!syncStarted) {
                                OutlinedTextField(
                                    value = bulk,
                                    onValueChange = { bulk = it },
                                    label = { Text("ألصق النص هنا (كل سطر = ترجمة)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 4,
                                    maxLines = 8
                                )
                                Button(
                                    onClick = {
                                        val lines = bulk.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                        if (lines.isEmpty()) {
                                            toast("ألصق النص أولاً")
                                        } else {
                                            store.pending.clear()
                                            store.pending.addAll(lines)
                                            store.syncedIds.clear()
                                            syncStarted = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("بدء المزامنة") }
                                Text(
                                    "ألصق الكلمات كلها، ثم شغّل الصوت واضغط زر «ثبّت» عند بداية كل سطر. التطبيق يضع التوقيت تلقائياً.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text("الأسطر المتبقية: ${store.pending.size}", fontWeight = FontWeight.Bold)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (store.pending.isEmpty()) "✓ تمت مزامنة كل الأسطر" else store.pending.first(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(18.dp)
                                    )
                                }
                                if (store.pending.size > 1) {
                                    Text(
                                        "التالي: " + store.pending[1],
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (mediaUri == null) {
                                            toast("افتح ملف فيديو أو صوت أولاً")
                                        } else {
                                            val t = exo.currentPosition
                                            val lastId = store.syncedIds.lastOrNull()
                                            if (lastId != null) {
                                                val prev = store.subs.firstOrNull { it.id == lastId }
                                                if (prev != null && t > prev.startMs) store.update(prev.copy(endMs = t))
                                            }
                                            if (store.pending.isNotEmpty()) {
                                                val line = store.pending.removeAt(0)
                                                var end = t + 3000L
                                                if (duration > 0) end = end.coerceAtMost(maxOf(duration, t + 500L))
                                                val added = store.add(t, end, line)
                                                store.syncedIds.add(added.id)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(86.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        if (store.pending.isEmpty()) "إنهاء آخر سطر هنا" else "ثبّت بداية السطر الآن",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val lastId = store.syncedIds.lastOrNull()
                                            val prev = store.subs.firstOrNull { it.id == lastId }
                                            val t = exo.currentPosition
                                            if (prev != null && t > prev.startMs) store.update(prev.copy(endMs = t))
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("إنهاء السطر هنا", fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            val lastId = store.syncedIds.lastOrNull()
                                            val prev = store.subs.firstOrNull { it.id == lastId }
                                            if (prev != null) {
                                                store.pending.add(0, prev.text)
                                                store.remove(prev.id)
                                                exo.seekTo(prev.startMs)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("تراجع", fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = { exo.seekTo(0L); if (mediaUri != null) exo.play() },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("من البداية", fontSize = 12.sp) }
                                }
                                TextButton(onClick = {
                                    syncStarted = false
                                    store.pending.clear()
                                    store.syncedIds.clear()
                                }) { Text("خروج من وضع المزامنة") }
                            }
                        }
                    }

                    else -> {
                        // ----- القائمة -----
                        if (store.subs.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "لا توجد ترجمات بعد",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val listState = rememberLazyListState()
                            val activeIndex = store.subs.indexOfFirst { it.id == activeSub?.id }
                            LaunchedEffect(activeIndex) {
                                if (activeIndex >= 0 && isPlaying) listState.animateScrollToItem(activeIndex)
                            }
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(store.subs, key = { _, s -> s.id }) { index, s ->
                                    val active = s.id == activeSub?.id
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .clickable { exo.seekTo(s.startMs) }
                                    ) {
                                        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                LtrText("${index + 1}   ${SrtUtil.fmtSrt(s.startMs)} → ${SrtUtil.fmtSrt(s.endMs)}", 11)
                                                Spacer(Modifier.height(2.dp))
                                                Text(s.text, fontSize = 15.sp)
                                            }
                                            IconButton(onClick = { editing = s }) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                                            IconButton(onClick = { store.remove(s.id) }) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val ed = editing
    if (ed != null) {
        EditDialog(
            sub = ed,
            now = position,
            onDismiss = { editing = null },
            onSave = { updated ->
                store.update(updated)
                editing = null
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("حذف كل الترجمات؟") },
            text = { Text("لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(onClick = {
                    store.clear()
                    syncStarted = false
                    confirmClear = false
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun EditDialog(sub: Sub, now: Long, onDismiss: () -> Unit, onSave: (Sub) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(sub.text) }
    var start by remember { mutableLongStateOf(sub.startMs) }
    var end by remember { mutableLongStateOf(sub.endMs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل الترجمة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                TimeRow("البداية", start, now) { start = it.coerceAtLeast(0L) }
                TimeRow("النهاية", end, now) { end = it.coerceAtLeast(0L) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
                if (clean.isEmpty()) {
                    Toast.makeText(context, "النص فارغ", Toast.LENGTH_SHORT).show()
                } else if (end <= start) {
                    Toast.makeText(context, "وقت النهاية لازم يكون بعد البداية", Toast.LENGTH_SHORT).show()
                } else {
                    onSave(sub.copy(startMs = start, endMs = end, text = clean))
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun TimeRow(label: String, value: Long, now: Long, onChange: (Long) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            LtrText(SrtUtil.fmtSrt(value), 14)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onChange(value - 100) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text("-0.1ث", fontSize = 12.sp) }
            TextButton(onClick = { onChange(value + 100) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text("+0.1ث", fontSize = 12.sp) }
            TextButton(onClick = { onChange(now) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text("الآن", fontSize = 12.sp) }
        }
    }
}

@Composable
fun LtrText(s: String, size: Int) {
    Text(
        s,
        fontSize = size.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    )
}

@Composable
fun CtrlBtn(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.height(40.dp).widthIn(min = 48.dp),
        colors = ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun PauseIcon(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(2) {
            Box(Modifier.size(width = 6.dp, height = 22.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}
