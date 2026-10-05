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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun Modifier.tap(f: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = f)

fun Modifier.glass(r: Int = 22): Modifier = this
    .clip(RoundedCornerShape(r.dp))
    .background(Color.White.copy(.14f))

@Composable
fun W(s: String, sz: Int = 14, w: FontWeight = FontWeight.Normal, a: Float = 1f, c: Color = Color.White, align: TextAlign? = null) =
    Text(s, color = c.copy(a), fontSize = sz.sp, fontWeight = w, textAlign = align)

@Composable
fun Icon(res: Int, size: Int = 22, tint: Color = Color.White) =
    androidx.compose.foundation.Image(painterResource(res), null, Modifier.size(size.dp), colorFilter = ColorFilter.tint(tint))

@Composable
fun WIcon(res: Int, size: Int = 22) =
    androidx.compose.foundation.Image(painterResource(res), null, Modifier.size(size.dp))

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
    a <= 200 -> "Unhealthy" to Color(0xFFEF4444)
    else -> "Severe" to Color(0xFFA855F7)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
fun Skyline(night: Boolean) {
    val t = rememberInfiniteTransition(label = "s")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "a")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        if (night) {
            for (i in 0 until 40) {
                val al = .3f + .7f * kotlin.math.abs(sin(2f * PI.toFloat() * (a * (2 + i % 3) + i * .13f)))
                drawCircle(Color.White.copy(al), (0.8f + (i % 3) * .5f).dp.toPx(), Offset(((i * 73) % 100) / 100f * w, ((i * 41) % 55) / 100f * h))
            }
        }
        for (j in 0 until 4) {
            val x = ((a * 2f + j * .25f) % 1f) * (w + 500f) - 250f
            val y = h * (.08f + j * .09f)
            val col = if (night) Color.White.copy(.06f) else Color.White.copy(.35f)
            drawOval(col, Offset(x, y), Size(320f, 110f))
            drawOval(col, Offset(x + 90f, y + 20f), Size(220f, 90f))
        }
    }
}

@Composable
fun Hero(w: Wx) {
    val txt = cond(w.cur.code, w.cur.isDay)
    val d = w.days[0]
    val (al, _) = aqiInfo(w.aqi)
    Column(Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Top) {
            W("${w.cur.temp.roundToInt()}", 118, FontWeight.Thin)
            W("°", 40, FontWeight.Thin, a = .9f)
        }
        Spacer(Modifier.height(10.dp))
        Text("$txt   ${d.min.roundToInt()}° / ${d.max.roundToInt()}°   Air quality: ${w.aqi} \u2013 $al",
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
fun AqiSummary(w: Wx) {
    val (label, col) = aqiInfo(w.aqi)
    val info = when {
        w.aqi <= 50 -> "Air quality is satisfactory, and air pollution poses little or no risk."
        w.aqi <= 100 -> "Air quality is acceptable; however, for some pollutants there may be a moderate concern for sensitive people."
        w.aqi <= 150 -> "Sensitive groups may experience health effects. Limit prolonged outdoor exertion."
        else -> "Everyone may experience health effects. Avoid outdoor activity."
    }
    Row(Modifier.fillMaxWidth().glass().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(R.drawable.ic_leaf, 18); Spacer(Modifier.width(6.dp)); W(label, 19, FontWeight.SemiBold) }
            Text(info, color = Color.White.copy(.85f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 7.dp.toPx(); val ins = sw / 2
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(Color.White.copy(.2f), 0f, 360f, false, Offset(ins, ins), sz, style = Stroke(sw))
                drawArc(col, -90f, (w.aqi / 300f).coerceIn(0f, 1f) * 360f, false, Offset(ins, ins), sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            W("${w.aqi}", 21, FontWeight.SemiBold)
        }
    }
}

@Composable
fun Hourly(w: Wx) = Column(Modifier.fillMaxWidth().glass().padding(vertical = 16.dp)) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(horizontal = 18.dp)) {
        items(w.hours) { h ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                W(if (h === w.hours[0]) "Now" else t12(h.time).dropLast(2), 13, a = .8f)
                WIcon(iconFor(h.code, h.isDay), 28)
                W("${h.temp.roundToInt()}°", 16, FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun Daily(w: Wx) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) w.days else w.days.take(4)
    Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        shown.forEachIndexed { i, d ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(116.dp)) { W("${md(d.date)}  ${dayName(d.date, i)}", 18, FontWeight.SemiBold) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { WIcon(iconFor(d.code, 1), 24) }
                Box(Modifier.width(94.dp), contentAlignment = Alignment.CenterEnd) {
                    W("${d.min.roundToInt()}° / ${d.max.roundToInt()}°", 16, FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(.12f)).tap { expanded = !expanded }
            .padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            W(if (expanded) "Show less" else "${w.days.size}-day weather forecast", 14, FontWeight.Medium)
        }
    }
}

@Composable
fun Tile(res: Int, label: String, value: String, unit: String, m: Modifier) =
    Column(m.glass(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(res, 20, Color.White.copy(.85f)); W(label, 13, a = .7f)
        Row(verticalAlignment = Alignment.Bottom) {
            W(value, 22, FontWeight.SemiBold)
            if (unit.isNotEmpty()) { Spacer(Modifier.width(3.dp)); Box(Modifier.padding(bottom = 3.dp)) { W(unit, 12, a = .8f) } }
        }
    }

@Composable
fun Tiles(w: Wx) {
    val c = w.cur
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(R.drawable.ic_uv, "UV", "${c.uv.roundToInt()}", uvLevel(c.uv), Modifier.weight(1f))
            Tile(R.drawable.ic_thermo, "Feels like", "${c.feels.roundToInt()}", "°", Modifier.weight(1f))
            Tile(R.drawable.ic_drop, "Humidity", "${c.hum}", "%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(R.drawable.ic_wind, "${compass16(c.dir)} wind", "${c.wind.roundToInt()}", "km/h", Modifier.weight(1f))
            Tile(R.drawable.ic_gauge, "Air pressure", "${c.press}", "hPa", Modifier.weight(1f))
            Tile(R.drawable.ic_eye, "Visibility", "${c.vis.roundToInt()}", "km", Modifier.weight(1f))
        }
    }
}

@Composable
fun SunCard(w: Wx) = Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    val d = w.days[0]
    val rise = mins(d.rise); val set = mins(d.set); val now = mins(w.now)
    val f = ((now - rise).toFloat() / (set - rise).coerceAtLeast(1)).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(R.drawable.ic_sunrise, 18); Spacer(Modifier.width(6.dp)); W("Sunrise", 13, a = .75f) }
        Row(verticalAlignment = Alignment.CenterVertically) { W("Sunset", 13, a = .75f); Spacer(Modifier.width(6.dp)); Icon(R.drawable.ic_sunset, 18) }
    }
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        drawRoundRect(Color.White.copy(.2f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        drawRoundRect(Color.White, size = Size(size.width * f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        drawCircle(Color.White, size.height * .9f, Offset(size.width * f, size.height / 2))
        drawCircle(Color(0xFF1D6FE8), size.height * .35f, Offset(size.width * f, size.height / 2))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        W(d.rise, 20, FontWeight.SemiBold); W(d.set, 20, FontWeight.SemiBold)
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
    val caps = listOf(Place("Srinagar", 34.0837, 74.7973, "Jammu & Kashmir, India"), Place("New York", 40.7128, -74.006, "United States"),
        Place("London", 51.5074, -0.1278, "United Kingdom"), Place("Tokyo", 35.6762, 139.6503, "Japan"),
        Place("Paris", 48.8566, 2.3522, "France"), Place("New Delhi", 28.6139, 77.209, "India"))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(.6f)).tap { onClose() }) {
        val shape = RoundedCornerShape(28.dp)
        Column(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth().clip(shape).background(Color(0xF00F172A))
            .tap { }.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { W("Locations", 22, FontWeight.Bold) }
                Box(Modifier.tap { onClose() }.padding(8.dp)) { W("Close", 14, a = .8f) }
            }
            TextField(q, { q = it.filter { c -> c.isLetterOrDigit() || c in " ,.'-" }.take(60) }, Modifier.fillMaxWidth().glass(20),
                singleLine = true, placeholder = { W("Search city or place", 15, a = .6f) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.White))
            Row(Modifier.fillMaxWidth().glass(20).tap { onGps() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_pin, 18); Spacer(Modifier.width(8.dp)); W("Use my current location", 15, FontWeight.Medium)
            }
            if (msg != null) W(msg, 13, a = .85f)
            if (busy) W("Searching\u2026", 13, a = .7f)
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
    Row(Modifier.clip(shape).background(Color.White.copy(if (active) .32f else .12f)).tap(onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (active) W("\u25CF ", 10, c = Color(0xFF4ADE80))
        W(t, 14)
        if (onDel != null) { Spacer(Modifier.width(8.dp)); Box(Modifier.tap(onDel)) { W("\u2715", 13, a = .8f) } }
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
    val home = Place("Srinagar", 34.0837, 74.7973, "Jammu & Kashmir, India")

    fun refresh(p: Place) {
        scope.launch {
            refreshing = true; err = null
            try {
                val (raw, aqi) = withContext(Dispatchers.IO) { fetchRaw(p) to fetchAqi(p) }
                val nw = parse(p, raw, aqi); wx = nw; offline = false
                ctx.saveWx(p, raw, aqi); WeatherWidget.push(ctx, nw)
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
            msg = "Locating\u2026"
            try {
                val loc = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
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
        if (p == null) { msg = "Choose your city or use your location for accurate weather"; showSearch = true }
    }
    BackHandler(showSearch) { showSearch = false }

    val w = wx
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(sky(w?.cur?.code ?: 3, w?.cur?.isDay ?: 1)))) {
        Skyline(w?.cur?.isDay == 0)
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refresh(w?.place ?: home) }, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        W(w?.place?.name ?: "Frost Weather", 26, FontWeight.Bold)
                        if (offline) W("Offline \u00b7 showing last saved forecast", 12, a = .75f)
                    }
                    Box(Modifier.size(40.dp).glass(20).tap { showSearch = true }, contentAlignment = Alignment.Center) { Icon(R.drawable.ic_list_add, 20) }
                }
                if (err != null) {
                    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        W("Couldn't load weather", 16, FontWeight.SemiBold)
                        W(err ?: "", 12, a = .85f)
                        FChip("Retry", false, { refresh(w?.place ?: home) })
                    }
                }
                if (w == null && err == null) W("Loading\u2026", 16, a = .8f)
                if (w != null) {
                    Hero(w); AqiSummary(w); Hourly(w); Daily(w); Tiles(w); SunCard(w)
                    W("Open-Meteo model data \u00b7 updated ${w.now.takeLast(5)} local time", 11, a = .55f, align = TextAlign.Center)
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
