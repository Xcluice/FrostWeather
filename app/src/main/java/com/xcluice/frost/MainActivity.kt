package com.xcluice.frost

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------- helpers ----------
@Composable
fun Modifier.tap(f: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = f)

fun Modifier.glass(r: Int = 26): Modifier = this
    .clip(RoundedCornerShape(r.dp))
    .background(Brush.linearGradient(listOf(Color.White.copy(.26f), Color.White.copy(.07f))))
    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.7f), Color.White.copy(.05f), Color.White.copy(.25f))), RoundedCornerShape(r.dp))

@Composable
fun W(s: String, sz: Int = 14, w: FontWeight = FontWeight.Normal, a: Float = 1f, c: Color = Color.White) =
    Text(s, color = c.copy(a), fontSize = sz.sp, fontWeight = w)

@Composable
fun Glass(title: String, mod: Modifier = Modifier, body: @Composable ColumnScope.() -> Unit) =
    Column(mod.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        W(title.uppercase(), 11, FontWeight.SemiBold, .65f)
        body()
    }

@Composable
fun Stat(l: String, v: String, m: Modifier) =
    Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) { W(l, 12, a = .65f); W(v, 16, FontWeight.SemiBold) }

fun sky(code: Int, day: Int): List<Color> = if (day != 1) when {
    code >= 95 -> listOf(Color(0xFF0B0820), Color(0xFF2D1B69), Color(0xFF4B3F8F))
    code >= 51 -> listOf(Color(0xFF0B1220), Color(0xFF1B2A44), Color(0xFF2E4468))
    else -> listOf(Color(0xFF0B1026), Color(0xFF1E2A5A), Color(0xFF4B3F8F))
} else when {
    code >= 95 -> listOf(Color(0xFF1A1633), Color(0xFF3E3573), Color(0xFF6A5FA8))
    code in 71..77 || code == 85 || code == 86 -> listOf(Color(0xFF6E8FB8), Color(0xFFA9C5E3), Color(0xFFE4F0FB))
    code >= 51 -> listOf(Color(0xFF263A55), Color(0xFF456A94), Color(0xFF7FA2C4))
    code >= 2 -> listOf(Color(0xFF43546B), Color(0xFF7B8FA8), Color(0xFFB6C4D6))
    else -> listOf(Color(0xFF1D6FE8), Color(0xFF4FA8F5), Color(0xFF9BD5FF))
}

fun aqiInfo(a: Int): Pair<String, Color> = when {
    a <= 50 -> "Good" to Color(0xFF4ADE80)
    a <= 100 -> "Moderate" to Color(0xFFFACC15)
    a <= 150 -> "Poor" to Color(0xFFFB923C)
    else -> "Severe" to Color(0xFFEF4444)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

// ---------- liquid background ----------
@Composable
fun Blobs(night: Boolean) {
    val t = rememberInfiniteTransition(label = "b")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val c1 = if (night) Color(0xFF6366F1) else Color.White
    val c2 = if (night) Color(0xFF22D3EE) else Color(0xFF9EE7FF)
    val c3 = if (night) Color(0xFFC084FC) else Color(0xFFB39DFF)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.offset((-60 + 140 * a).dp, (60 + 80 * a).dp).size(280.dp).blur(80.dp).background(c1.copy(.5f), CircleShape))
        Box(Modifier.align(Alignment.CenterEnd).offset((40 - 120 * a).dp, (100 - 60 * a).dp).size(240.dp).blur(80.dp).background(c2.copy(.5f), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset((30 + 90 * a).dp, (-40 * a).dp).size(300.dp).blur(90.dp).background(c3.copy(.45f), CircleShape))
    }
}

// ---------- sections ----------
@Composable
fun Hero(w: Wx) {
    val (emo, txt) = desc(w.cur.code, w.cur.isDay)
    val d = w.days[0]
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        W(w.place.name, 30, FontWeight.SemiBold)
        if (w.place.sub.isNotBlank()) W(w.place.sub, 13, a = .7f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            W(emo, 60); Spacer(Modifier.width(10.dp)); W("${w.cur.temp.roundToInt()}°", 104, FontWeight.Thin)
        }
        W(txt, 20, FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        W("Feels like ${w.cur.feels.roundToInt()}°   ↑${d.max.roundToInt()}°  ↓${d.min.roundToInt()}°", 15, a = .85f)
    }
}

@Composable
fun Atmos(w: Wx) = Glass("Atmosphere") {
    val c = w.cur
    Row(Modifier.fillMaxWidth()) {
        Stat("💨 Wind", "${c.wind.roundToInt()} km/h ${compass16(c.dir)}", Modifier.weight(1f))
        Stat("🌬️ Gusts", "${c.gust.roundToInt()} km/h", Modifier.weight(1f))
        Stat("☀️ UV", "${c.uv.roundToInt()} · ${uvLevel(c.uv)}", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth()) {
        Stat("🧭 Pressure", "${c.press} hPa", Modifier.weight(1f))
        Stat("👁️ Visibility", "${"%.1f".format(c.vis)} km", Modifier.weight(1f))
        Stat("☁️ Cloud", "${c.cloud}%", Modifier.weight(1f))
    }
}

@Composable
fun Hourly(w: Wx) = Glass("Next 24 hours") {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(w.hours) { i, h ->
            val base = if (i == 0) Modifier.glass(20) else Modifier
            Column(base.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                W(if (i == 0) "NOW" else hour12(h.time), 13, if (i == 0) FontWeight.Bold else FontWeight.Normal, .9f)
                W(desc(h.code, h.isDay).first, 24)
                W("${h.temp.roundToInt()}°", 18, FontWeight.SemiBold)
                W("💧${h.pop}%", 12, a = .8f)
            }
        }
    }
}

@Composable
fun Daily(w: Wx) = Glass("7-day forecast") {
    val lo = w.days.minOf { it.min }; val hi = w.days.maxOf { it.max }; val span = (hi - lo).coerceAtLeast(1.0)
    w.days.forEachIndexed { i, d ->
        val s = ((d.min - lo) / span).toFloat(); val e = ((d.max - lo) / span).toFloat()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(78.dp)) { W(dayName(d.date, i), 15, FontWeight.Medium) }
            W(desc(d.code, 1).first, 20)
            Box(Modifier.width(50.dp), contentAlignment = Alignment.Center) { W("💧${d.pop}%", 11, a = .75f) }
            W("${d.min.roundToInt()}°", 15, a = .7f)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(.15f))) {
                Row(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(s.coerceAtLeast(.001f)))
                    Box(Modifier.weight((e - s).coerceAtLeast(.03f)).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF60A5FA), Color(0xFFFB923C)))))
                    Spacer(Modifier.weight((1f - e).coerceAtLeast(.001f)))
                }
            }
            Spacer(Modifier.width(8.dp))
            W("${d.max.roundToInt()}°", 15, FontWeight.SemiBold)
        }
    }
}

@Composable
fun SunCard(w: Wx) = Glass("Sun & moon") {
    val d = w.days[0]
    val rise = mins(d.rise); val set = mins(d.set); val now = mins(w.now)
    val night = now < rise || now > set
    val f = ((now - rise).toFloat() / (set - rise).coerceAtLeast(1)).coerceIn(0f, 1f)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val y0 = size.height - 8.dp.toPx(); val x0 = 12.dp.toPx(); val x2 = size.width - x0
        val cx = size.width / 2f; val cy = -size.height * 0.55f
        drawLine(Color.White.copy(.25f), Offset(0f, y0), Offset(size.width, y0), 1.dp.toPx())
        val path = Path().apply { moveTo(x0, y0); quadraticBezierTo(cx, cy, x2, y0) }
        drawPath(path, Color.White.copy(.55f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))))
        val u = 1f - f
        val x = u * u * x0 + 2 * u * f * cx + f * f * x2
        val y = u * u * y0 + 2 * u * f * cy + f * f * y0
        val col = if (night) Color(0xFFBFDBFE) else Color(0xFFFBBF24)
        drawCircle(col.copy(.3f), 20.dp.toPx(), Offset(x, y))
        drawCircle(col, 9.dp.toPx(), Offset(x, y))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        W("🌅 Sunrise  ${d.rise}", 14, FontWeight.Medium); W("🌇 Sunset  ${d.set}", 14, FontWeight.Medium)
    }
}

@Composable
fun UvCard(w: Wx) = Glass("UV index") {
    val uv = w.cur.uv
    Row(verticalAlignment = Alignment.Bottom) {
        W("${uv.roundToInt()}", 42, FontWeight.Light); Spacer(Modifier.width(10.dp))
        Box(Modifier.padding(bottom = 8.dp)) { W(uvLevel(uv), 17, FontWeight.Medium) }
    }
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val h = size.height
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF4ADE80), Color(0xFFFACC15), Color(0xFFFB923C), Color(0xFFEF4444), Color(0xFFA855F7))),
            Offset(0f, h * .3f), Size(size.width, h * .4f), CornerRadius(h, h))
        val x = ((uv / 12.0).toFloat()).coerceIn(0f, 1f) * size.width
        val px = x.coerceIn(h / 2, size.width - h / 2)
        drawCircle(Color.White, h * .5f, Offset(px, h / 2))
        drawCircle(Color.Black.copy(.25f), h * .5f, Offset(px, h / 2), style = Stroke(1.5f.dp.toPx()))
    }
}

@Composable
fun WindCard(w: Wx) = Glass("Wind & compass") {
    val c = w.cur
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Canvas(Modifier.size(140.dp)) {
            val ctr = center; val r = size.minDimension / 2 - 4.dp.toPx()
            drawCircle(Color.White.copy(.08f), r, ctr)
            drawCircle(Color.White.copy(.4f), r, ctr, style = Stroke(1.5f.dp.toPx()))
            for (i in 0 until 36) {
                val a = i * 10f * (PI.toFloat() / 180f)
                val len = if (i % 9 == 0) 10.dp.toPx() else if (i % 3 == 0) 6.dp.toPx() else 3.dp.toPx()
                drawLine(Color.White.copy(.45f), Offset(ctr.x + sin(a) * (r - len), ctr.y - cos(a) * (r - len)),
                    Offset(ctr.x + sin(a) * r, ctr.y - cos(a) * r), 1.dp.toPx())
            }
            drawIntoCanvas { cv ->
                val p = android.graphics.Paint().apply {
                    isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSize = 13.sp.toPx(); isFakeBoldText = true
                }
                listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (t, deg) ->
                    p.color = if (t == "N") 0xFFFF5252.toInt() else 0xCCFFFFFF.toInt()
                    val a = deg * PI.toFloat() / 180f; val rr = r - 24.dp.toPx()
                    cv.nativeCanvas.drawText(t, ctr.x + sin(a) * rr, ctr.y - cos(a) * rr + 5.dp.toPx(), p)
                }
            }
            rotate(c.dir, ctr) {
                val arrow = Path().apply {
                    moveTo(ctr.x, ctr.y - r * 0.62f); lineTo(ctr.x - 9.dp.toPx(), ctr.y + r * 0.18f)
                    lineTo(ctr.x, ctr.y + r * 0.05f); lineTo(ctr.x + 9.dp.toPx(), ctr.y + r * 0.18f); close()
                }
                drawPath(arrow, Color(0xFF38BDF8))
            }
            drawCircle(Color.White, 3.dp.toPx(), ctr)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            W("${c.wind.roundToInt()} km/h", 24, FontWeight.SemiBold)
            W("Gusts ${c.gust.roundToInt()} km/h", 13, a = .8f)
            W("From ${compass16(c.dir)} (${c.dir.roundToInt()}°)", 13, a = .8f)
            W("Today's peak ${w.days[0].wind.roundToInt()} km/h", 13, a = .8f)
        }
    }
}

@Composable
fun AqiCard(w: Wx) = Glass("Air quality") {
    val (label, col) = aqiInfo(w.aqi)
    Row(verticalAlignment = Alignment.Bottom) {
        W("${w.aqi}", 42, FontWeight.Light); Spacer(Modifier.width(10.dp))
        Box(Modifier.padding(bottom = 8.dp)) { W(label, 17, FontWeight.Medium, c = col) }
    }
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val h = size.height
        val g = Color(0xFF4ADE80); val y = Color(0xFFFACC15); val o = Color(0xFFFB923C); val r = Color(0xFFEF4444)
        drawRoundRect(Brush.horizontalGradient(0f to g, .25f to g, .25f to y, .5f to y, .5f to o, .75f to o, .75f to r, 1f to r),
            Offset(0f, h * .3f), Size(size.width, h * .4f), CornerRadius(h, h))
        val px = ((w.aqi / 200f).coerceIn(0f, 1f) * size.width).coerceIn(h / 2, size.width - h / 2)
        drawCircle(Color.White, h * .5f, Offset(px, h / 2))
        drawCircle(Color.Black.copy(.25f), h * .5f, Offset(px, h / 2), style = Stroke(1.5f.dp.toPx()))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("Good", "Mod", "Poor", "Severe").forEach { W(it, 11, a = .7f) }
    }
}

// ---------- search overlay ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(saved: List<Place>, current: Place?, msg: String?, onPick: (Place) -> Unit,
                  onDelete: (Place) -> Unit, onGps: () -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        val s = q.trim()
        if (s.length < 2) { res = emptyList(); return@LaunchedEffect }
        delay(350); busy = true
        res = try { withContext(Dispatchers.IO) { search(s) } } catch (e: Exception) { emptyList() }
        busy = false
    }
    val caps = listOf(Place("New York", 40.7128, -74.006, "United States"), Place("London", 51.5074, -0.1278, "United Kingdom"),
        Place("Tokyo", 35.6762, 139.6503, "Japan"), Place("Paris", 48.8566, 2.3522, "France"), Place("New Delhi", 28.6139, 77.209, "India"))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(.6f)).tap { onClose() }) {
        val shape = RoundedCornerShape(28.dp)
        Column(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth().clip(shape).background(Color(0xF00F172A))
            .border(1.dp, Color.White.copy(.2f), shape).tap { }.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { W("Locations", 22, FontWeight.Bold) }
                Box(Modifier.tap { onClose() }.padding(8.dp)) { W("✕", 18) }
            }
            TextField(q, { q = it.filter { c -> c.isLetterOrDigit() || c in " ,.'-" }.take(60) }, Modifier.fillMaxWidth().glass(20),
                singleLine = true, placeholder = { W("Search city or place", 15, a = .6f) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.White))
            Row(Modifier.fillMaxWidth().glass(20).tap { onGps() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                W("📍  Use my current location", 15, FontWeight.Medium)
            }
            if (msg != null) W(msg, 13, a = .85f)
            if (busy) W("Searching…", 13, a = .7f)
            res.forEach { p ->
                Column(Modifier.fillMaxWidth().tap { onPick(p) }.padding(vertical = 4.dp)) {
                    W(p.name, 16, FontWeight.Medium); if (p.sub.isNotBlank()) W(p.sub, 12, a = .6f)
                }
            }
            if (saved.isNotEmpty()) {
                W("SAVED LOCATIONS", 11, FontWeight.SemiBold, .6f)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    saved.forEach { p -> FChip(p.name, current != null && p.name == current.name && abs(p.lat - current.lat) < .05, { onPick(p) }) { onDelete(p) } }
                }
            }
            W("QUICK JUMP", 11, FontWeight.SemiBold, .6f)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                caps.forEach { p -> FChip(p.name, false, { onPick(p) }) }
            }
        }
    }
}

@Composable
fun FChip(t: String, active: Boolean, onClick: () -> Unit, onDel: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Row(Modifier.clip(shape).background(Color.White.copy(if (active) .32f else .12f))
        .border(1.dp, Color.White.copy(if (active) .7f else .2f), shape).tap(onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (active) W("● ", 10, c = Color(0xFF4ADE80))
        W(t, 14)
        if (onDel != null) { Spacer(Modifier.width(8.dp)); Box(Modifier.tap(onDel)) { W("✕", 13, a = .8f) } }
    }
}

// ---------- app ----------
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var wx by remember { mutableStateOf<Wx?>(null) }
    var saved by remember { mutableStateOf<List<Place>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }
    val home = Place("Berlin", 52.52, 13.41, "Germany")

    fun refresh(p: Place) {
        scope.launch {
            refreshing = true; err = null
            try {
                val (raw, aqi) = withContext(Dispatchers.IO) { fetchRaw(p) to fetchAqi(p) }
                wx = parse(p, raw, aqi); offline = false
                ctx.saveWx(p, raw, aqi)
            } catch (e: Exception) {
                if (wx == null) err = "${e.javaClass.simpleName}: ${e.message}" else offline = true
            }
            refreshing = false
        }
    }
    fun select(p: Place) {
        showSearch = false; msg = null
        if (saved.none { it.name == p.name && abs(it.lat - p.lat) < .05 && abs(it.lon - p.lon) < .05 }) {
            val ns = saved + p; saved = ns; scope.launch { ctx.saveList(ns) }
        }
        refresh(p)
    }
    fun gps() {
        scope.launch {
            msg = "Locating…"
            try {
                val loc = fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                if (loc == null) { msg = "Couldn't get location. Is GPS on?"; return@launch }
                val p = withContext(Dispatchers.IO) { reverse(loc.latitude, loc.longitude) }
                select(p)
            } catch (e: Exception) { msg = "Location failed: ${e.message}" }
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) gps() else msg = "Location permission denied"
    }
    fun useGps() {
        val ok = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (ok) gps() else perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(Unit) {
        val (p, cached, s) = ctx.readAll()
        saved = s; if (cached != null) wx = cached
        refresh(p ?: home)
    }
    BackHandler(showSearch) { showSearch = false }

    val w = wx
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(sky(w?.cur?.code ?: 3, w?.cur?.isDay ?: 1)))) {
        Blobs(w?.cur?.isDay == 0)
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refresh(w?.place ?: home) }, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { W("❄  Frost", 20, FontWeight.Bold) }
                    if (offline) { W("offline", 12, a = .8f); Spacer(Modifier.width(10.dp)) }
                    Box(Modifier.size(42.dp).glass(21).tap { showSearch = true }, contentAlignment = Alignment.Center) { W("+", 24) }
                }
                if (err != null) {
                    Glass("Couldn't load weather") {
                        W(err ?: "", 12, a = .85f)
                        FChip("Retry", false, { refresh(home) })
                    }
                }
                if (w == null && err == null) W("Loading…", 16, a = .8f)
                if (w != null) {
                    Hero(w); Atmos(w); Hourly(w); Daily(w); SunCard(w); UvCard(w); WindCard(w); AqiCard(w)
                    val d = w.days[0]
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Glass("Precipitation", Modifier.weight(1f)) {
                            W("${"%.1f".format(d.prcp)} mm", 28, FontWeight.Light)
                            W("Rain chance ${d.pop}%", 13, a = .8f)
                        }
                        Glass("Humidity", Modifier.weight(1f)) {
                            W("${w.cur.hum}%", 28, FontWeight.Light)
                            W(humLabel(w.cur.hum), 13, a = .8f)
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
        AnimatedVisibility(showSearch, enter = fadeIn(), exit = fadeOut()) {
            SearchOverlay(saved, w?.place, msg, { select(it) },
                { p -> val ns = saved.filter { it !== p }; saved = ns; scope.launch { ctx.saveList(ns) } },
                { useGps() }, { showSearch = false })
        }
    }
}
