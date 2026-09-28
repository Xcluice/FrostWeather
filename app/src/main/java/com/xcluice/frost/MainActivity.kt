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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs as fabs
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

val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun Modifier.glass(r: Int = 26): Modifier {
    val hz = LocalHaze.current
    val shape = RoundedCornerShape(r.dp)
    var m = this.clip(shape)
    if (hz != null) m = m.hazeEffect(hz) { blurRadius = 24.dp; noiseFactor = 0.04f; tints = listOf(HazeTint(Color.White.copy(.10f))) }
    return m.background(Brush.linearGradient(listOf(Color.White.copy(.22f), Color.White.copy(.05f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.7f), Color.White.copy(.05f), Color.White.copy(.25f))), shape)
}

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
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val c1 = if (night) Color(0xFF6366F1) else Color.White
    val c2 = if (night) Color(0xFF22D3EE) else Color(0xFF9EE7FF)
    val c3 = if (night) Color(0xFFC084FC) else Color(0xFFB39DFF)
    Canvas(Modifier.fillMaxSize()) {
        fun glow(c: Color, cx: Float, cy: Float, r: Float) {
            val o = Offset(cx, cy)
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), o, r), r, o)
        }
        glow(c1.copy(.40f), size.width * (.15f + .5f * a), size.height * (.10f + .10f * a), 320.dp.toPx())
        glow(c2.copy(.35f), size.width * (.85f - .5f * a), size.height * (.45f - .08f * a), 300.dp.toPx())
        glow(c3.copy(.35f), size.width * (.2f + .4f * a), size.height * (.85f - .1f * a), 340.dp.toPx())
    }
}

// ---------- live weather scene ----------
@Composable
fun Scene(code: Int, day: Int) {
    val tr = rememberInfiniteTransition(label = "s")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "t")
    val slow by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(90000, easing = LinearEasing)), label = "slow")
    val rain = code in 51..67 || code in 80..82 || code >= 95
    val snow = code in 71..77 || code == 85 || code == 86
    val night = day != 1
    Box(Modifier.fillMaxSize()) {
        if (code <= 2) Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            if (night) {
                for (i in 0 until 45) {
                    val al = .25f + .75f * fabs(sin(2f * PI.toFloat() * (t * (2 + i % 3) + i * .13f)))
                    drawCircle(Color.White.copy(al), (0.8f + (i % 3) * .5f).dp.toPx(), Offset(((i * 73) % 100) / 100f * w, ((i * 41) % 55) / 100f * h))
                }
                val c = Offset(w * .78f, h * .13f)
                drawCircle(Color(0x33BFDBFE), 60.dp.toPx(), c)
                drawCircle(Color(0xFFEFF6FF), 24.dp.toPx(), c)
                drawCircle(Color(0x22334155), 20.dp.toPx(), Offset(c.x + 10.dp.toPx(), c.y - 4.dp.toPx()))
            } else {
                val c = Offset(w * .8f, h * .12f)
                drawCircle(Brush.radialGradient(listOf(Color(0xE6FFF3B0), Color(0x00FFD54F)), c, 190.dp.toPx()), 190.dp.toPx(), c)
                drawCircle(Color(0xFFFFF1A8), 30.dp.toPx(), c)
            }
        }
        if (code >= 2) Canvas(Modifier.fillMaxSize()) {
            val col = if (night) Color(0xFF94A3B8).copy(.20f) else if (code >= 51) Color(0xFF334155).copy(.40f) else Color.White.copy(.38f)
            for (j in 0 until 5) {
                val x = ((slow * 3f + j * .2f) % 1f) * (size.width + 600f) - 300f
                val y = size.height * (.06f + .07f * j)
                for (k in 0 until 3) {
                    val c = Offset(x + k * 150f, y + (k % 2) * 40f); val r = 190f
                    drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), c, r), r, c)
                }
            }
        }
        if (code == 45 || code == 48) Canvas(Modifier.fillMaxSize()) {
            for (j in 0 until 4) {
                val y = size.height * (.15f + j * .2f) + 30f * sin(2f * PI.toFloat() * (t + j * .25f))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(.2f), Color.Transparent), startY = y, endY = y + 140.dp.toPx()), Offset(0f, y), Size(size.width, 140.dp.toPx()))
            }
        }
        if (rain || snow) Canvas(Modifier.fillMaxSize()) {
            val n = if (rain) 80 else 60
            for (i in 0 until n) {
                val k = if (rain) 3 + i % 3 else 1 + i % 2
                val y = ((t * k + (i * 0.618034f) % 1f) % 1f) * (size.height + 60f) - 30f
                val x0 = ((i * 37) % 100) / 100f * size.width
                if (rain) drawLine(Color.White.copy(.5f), Offset(x0, y), Offset(x0 - 6.dp.toPx(), y + 22.dp.toPx()), 1.5f.dp.toPx())
                else drawCircle(Color.White.copy(.85f), (1.5f + (i % 3)).dp.toPx(), Offset(x0 + sin(2f * PI.toFloat() * (t * 2 + i * .1f)) * 14.dp.toPx(), y))
            }
        }
        if (code >= 95) Canvas(Modifier.fillMaxSize()) {
            val ph = (t * 2f) % 1f
            val al = if (ph < .02f) .5f else if (ph in .06f..0.08f) .3f else 0f
            if (al > 0f) drawRect(Color.White.copy(al))
        }
    }
}

// ---------- sections ----------
@Composable
fun Hero(w: Wx) {
    val (emo, txt) = desc(w.cur.code, w.cur.isDay)
    val d = w.days[0]
    val (al, _) = aqiInfo(w.aqi)
    Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        W("${w.cur.temp.roundToInt()}°", 132, FontWeight.Thin)
        Spacer(Modifier.height(6.dp))
        Text("$emo $txt   ${d.min.roundToInt()}° / ${d.max.roundToInt()}°   Air quality: ${w.aqi} – $al",
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
fun AqiSummary(w: Wx) {
    val (label, col) = aqiInfo(w.aqi)
    val info = when {
        w.aqi <= 50 -> "Air quality is satisfactory, and air pollution poses little or no risk."
        w.aqi <= 100 -> "Air quality is acceptable; however, some pollutants may be a concern for unusually sensitive people."
        w.aqi <= 150 -> "Sensitive groups may experience health effects. Limit prolonged outdoor exertion."
        else -> "Everyone may experience health effects. Avoid outdoor activity."
    }
    Row(Modifier.fillMaxWidth().glass().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            W("🍃  $label", 20, FontWeight.SemiBold)
            Text(info, color = Color.White.copy(.85f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 7.dp.toPx(); val ins = sw / 2
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(Color.White.copy(.2f), 0f, 360f, false, Offset(ins, ins), sz, style = Stroke(sw))
                drawArc(col, -90f, (w.aqi / 300f).coerceIn(0f, 1f) * 360f, false, Offset(ins, ins), sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            W("${w.aqi}", 22, FontWeight.SemiBold)
        }
    }
}

@Composable
fun Tile(icon: String, label: String, value: String, unit: String, m: Modifier) =
    Column(m.glass(22).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        W(icon, 20); W(label, 13, a = .7f)
        Row(verticalAlignment = Alignment.Bottom) {
            W(value, 24, FontWeight.SemiBold)
            if (unit.isNotEmpty()) { Spacer(Modifier.width(3.dp)); Box(Modifier.padding(bottom = 3.dp)) { W(unit, 12, a = .8f) } }
        }
    }

@Composable
fun Tiles(w: Wx) {
    val c = w.cur
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("☀️", "UV", "${c.uv.roundToInt()}", uvLevel(c.uv), Modifier.weight(1f))
            Tile("🌡️", "Feels like", "${c.feels.roundToInt()}", "°", Modifier.weight(1f))
            Tile("💧", "Humidity", "${c.hum}", "%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("💨", "${compass16(c.dir)} wind", "${c.wind.roundToInt()}", "km/h", Modifier.weight(1f))
            Tile("🧭", "Air pressure", "${c.press}", "hPa", Modifier.weight(1f))
            Tile("👁️", "Visibility", "${c.vis.roundToInt()}", "km", Modifier.weight(1f))
        }
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
fun Hourly(w: Wx) = Glass("Next 24 hours · drag the curve") {
    val hs = w.hours; val n = hs.size
    var sel by remember { mutableStateOf(0) }
    val h = hs[sel.coerceIn(0, n - 1)]
    Row(verticalAlignment = Alignment.CenterVertically) {
        W(desc(h.code, h.isDay).first, 22); Spacer(Modifier.width(8.dp))
        W("${if (sel == 0) "Now" else hour12(h.time)}  ·  ${h.temp.roundToInt()}°  ·  💧${h.pop}%", 15, FontWeight.SemiBold)
    }
    val lo = hs.minOf { it.temp }; val hi = hs.maxOf { it.temp }; val range = (hi - lo).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(150.dp).pointerInput(n) {
        awaitPointerEventScope {
            while (true) {
                val p = awaitPointerEvent().changes.firstOrNull()
                if (p != null && p.pressed) sel = ((p.position.x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1)
            }
        }
    }) {
        val padX = 10.dp.toPx(); val top = 14.dp.toPx(); val bot = 30.dp.toPx()
        val cw = size.width - 2 * padX; val ch = size.height - top - bot
        val xs = FloatArray(n) { padX + it * cw / (n - 1) }
        val ys = FloatArray(n) { top + (1f - ((hs[it].temp - lo) / range).toFloat()) * ch }
        for (i in 0 until n) {
            val bh = hs[i].pop / 100f * 24.dp.toPx()
            drawRoundRect(Color(0xFF7DD3FC).copy(.45f), Offset(xs[i] - 3.dp.toPx(), size.height - bh), Size(6.dp.toPx(), bh), CornerRadius(3.dp.toPx()))
        }
        fun Path.curve() { moveTo(xs[0], ys[0]); for (i in 1 until n) { val cx = (xs[i - 1] + xs[i]) / 2f; cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]) } }
        val line = Path().apply { curve() }
        val fill = Path().apply { curve(); lineTo(xs[n - 1], size.height - bot); lineTo(xs[0], size.height - bot); close() }
        drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(.35f), Color.Transparent), startY = top, endY = size.height - bot))
        drawPath(line, Color.White, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val si = sel.coerceIn(0, n - 1)
        drawLine(Color.White.copy(.5f), Offset(xs[si], top), Offset(xs[si], size.height - bot), 1.dp.toPx())
        drawCircle(Color.White, 6.dp.toPx(), Offset(xs[si], ys[si]))
        drawCircle(Color(0xFF38BDF8), 3.dp.toPx(), Offset(xs[si], ys[si]))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(0, 6, 12, 18, 23).forEach { i -> if (i < n) W(if (i == 0) "Now" else hour12(hs[i].time), 11, a = .7f) }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(hs) { i, x ->
            val base = if (i == sel) Modifier.glass(20) else Modifier
            Column(base.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                W(if (i == 0) "NOW" else hour12(x.time), 13, if (i == 0) FontWeight.Bold else FontWeight.Normal, .9f)
                W(desc(x.code, x.isDay).first, 24)
                W("${x.temp.roundToInt()}°", 18, FontWeight.SemiBold)
                W("💧${x.pop}%", 12, a = .8f)
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
    val caps = listOf(Place("Srinagar", 34.0837, 74.7973, "Jammu & Kashmir, India"), Place("New York", 40.7128, -74.006, "United States"), Place("London", 51.5074, -0.1278, "United Kingdom"),
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
    val home = Place("Srinagar", 34.0837, 74.7973, "Jammu & Kashmir, India")

    fun refresh(p: Place) {
        scope.launch {
            refreshing = true; err = null
            try {
                val (raw, aqi) = withContext(Dispatchers.IO) { fetchRaw(p) to fetchAqi(p) }
                val nw = parse(p, raw, aqi); wx = nw; offline = false; WeatherWidget.push(ctx, nw)
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
        if (p == null) { msg = "Choose your city or use your location for accurate weather"; showSearch = true }
    }
    BackHandler(showSearch) { showSearch = false }

    val w = wx
    val hz = remember { HazeState() }
    CompositionLocalProvider(LocalHaze provides hz) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(hz).background(Brush.verticalGradient(sky(w?.cur?.code ?: 3, w?.cur?.isDay ?: 1)))) {
            Blobs(w?.cur?.isDay == 0)
            Scene(w?.cur?.code ?: 3, w?.cur?.isDay ?: 1)
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refresh(w?.place ?: home) }, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Column {
                            W(w?.place?.name ?: "Frost", 26, FontWeight.Bold)
                            if (w != null && w.place.sub.isNotBlank()) W("📍 ${w.place.sub}", 12, a = .75f)
                        } }
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
                    Hero(w); AqiSummary(w); Hourly(w); Daily(w); AqiCard(w); Tiles(w); SunCard(w); UvCard(w); WindCard(w)
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
                    W("Open-Meteo model data · updated ${w.now.takeLast(5)} local time · can differ slightly from station-based apps", 11, a = .6f)
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
}
