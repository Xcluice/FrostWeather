package com.xcluice.frost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

data class Hour(val time: String, val temp: Double, val pop: Int, val mm: Double, val snow: Double)
data class Day(val date: String, val max: Double, val min: Double, val rise: String, val set: String)
data class Wx(val city: String, val temp: Double, val code: Int, val hours: List<Hour>, val days: List<Day>)

fun fetch(name: String?): Wx {
    var lat = 52.52; var lon = 13.41; var city = "Berlin"
    if (name != null) {
        val g = JSONObject(URL("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" +
            URLEncoder.encode(name, "UTF-8")).readText()).getJSONArray("results").getJSONObject(0)
        lat = g.getDouble("latitude"); lon = g.getDouble("longitude"); city = g.getString("name")
    }
    val j = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
        "&current=temperature_2m,weather_code" +
        "&hourly=temperature_2m,precipitation_probability,precipitation,showers,rain,snowfall" +
        "&daily=sunrise,sunset,temperature_2m_max,temperature_2m_min&timezone=auto").readText())
    val cur = j.getJSONObject("current")
    val h = j.getJSONObject("hourly")
    val ht = h.getJSONArray("time")
    val start = (0 until ht.length()).firstOrNull { ht.getString(it).take(13) >= cur.getString("time").take(13) } ?: 0
    val hours = (start until minOf(start + 24, ht.length())).map {
        Hour(ht.getString(it), h.getJSONArray("temperature_2m").getDouble(it),
            h.getJSONArray("precipitation_probability").optInt(it),
            h.getJSONArray("precipitation").optDouble(it, 0.0),
            h.getJSONArray("snowfall").optDouble(it, 0.0))
    }
    val d = j.getJSONObject("daily")
    val days = (0 until d.getJSONArray("time").length()).map {
        Day(d.getJSONArray("time").getString(it), d.getJSONArray("temperature_2m_max").getDouble(it),
            d.getJSONArray("temperature_2m_min").getDouble(it),
            d.getJSONArray("sunrise").getString(it).takeLast(5), d.getJSONArray("sunset").getString(it).takeLast(5))
    }
    return Wx(city, cur.getDouble("temperature_2m"), cur.getInt("weather_code"), hours, days)
}

fun desc(c: Int): Pair<String, String> = when (c) {
    0 -> "☀️" to "Clear sky"; 1 -> "🌤️" to "Mainly clear"; 2 -> "⛅" to "Partly cloudy"; 3 -> "☁️" to "Overcast"
    45, 48 -> "🌫️" to "Fog"; in 51..57 -> "🌦️" to "Drizzle"; in 61..67 -> "🌧️" to "Rain"
    in 71..77 -> "❄️" to "Snow"; in 80..82 -> "🌧️" to "Showers"; 85, 86 -> "🌨️" to "Snow showers"
    in 95..99 -> "⛈️" to "Thunderstorm"; else -> "🌡️" to "Unknown"
}

fun bg(c: Int): List<Color> = when (c) {
    0, 1 -> listOf(Color(0xFF1E6FD9), Color(0xFF7CC4FF))
    2, 3, 45, 48 -> listOf(Color(0xFF3B4B63), Color(0xFF8FA3BD))
    in 71..77, 85, 86 -> listOf(Color(0xFF6C8DB5), Color(0xFFDCEBFA))
    in 95..99 -> listOf(Color(0xFF1B1633), Color(0xFF4A3B7A))
    else -> listOf(Color(0xFF1F3550), Color(0xFF4F7CA8))
}

fun Modifier.glass(r: Int = 28) = this
    .clip(RoundedCornerShape(r.dp))
    .background(Brush.linearGradient(listOf(Color.White.copy(.30f), Color.White.copy(.08f))))
    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.65f), Color.White.copy(.08f))), RoundedCornerShape(r.dp))

@Composable
fun W(s: String, sz: Int = 14, bold: Boolean = false, a: Float = 1f) =
    Text(s, color = Color.White.copy(a), fontSize = sz.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
fun Blobs() {
    val t = rememberInfiniteTransition(label = "b")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.offset((-60 + 140 * a).dp, (60 + 80 * a).dp).size(280.dp).blur(80.dp).background(Color.White.copy(.55f), CircleShape))
        Box(Modifier.align(Alignment.CenterEnd).offset((40 - 120 * a).dp, (100 - 60 * a).dp).size(240.dp).blur(80.dp).background(Color(0xFF9EE7FF).copy(.6f), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset((30 + 90 * a).dp, (-40 * a).dp).size(300.dp).blur(90.dp).background(Color(0xFFB39DFF).copy(.5f), CircleShape))
    }
}

@Composable
fun App() {
    var wx by remember { mutableStateOf<Wx?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun load(n: String?) = scope.launch {
        err = null
        try { wx = withContext(Dispatchers.IO) { fetch(n) } } catch (e: Exception) { err = "Couldn't load weather" }
    }
    LaunchedEffect(Unit) { load(null) }
    val code = wx?.code ?: 3
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(bg(code)))) {
        Blobs()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextField(q, { q = it }, Modifier.fillMaxWidth().glass(24), singleLine = true,
                placeholder = { W("Search city", 15, a = .7f) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (q.isNotBlank()) load(q.trim()) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.White))
            err?.let { W(it, 14, a = .9f) }
            val w = wx
            if (w == null && err == null) W("Loading…", 16, a = .8f)
            if (w != null) {
                val (emo, txt) = desc(w.code)
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    W(w.city, 26, true); W(emo, 64)
                    W("${w.temp.toInt()}°", 84, true); W(txt, 18, a = .85f)
                    W("H:${w.days[0].max.toInt()}°  L:${w.days[0].min.toInt()}°", 15, a = .8f)
                }
                Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    W("NEXT 24 HOURS", 12, true, .7f)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(w.hours) { h ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                W(h.time.takeLast(5), 13, a = .8f)
                                W("${h.temp.toInt()}°", 18, true)
                                W("💧${h.pop}%", 12, a = .85f)
                                W(if (h.snow > 0) "❄ ${h.snow}cm" else "${h.mm}mm", 11, a = .7f)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val chip = Modifier.weight(1f).glass(22).padding(14.dp)
                    Column(chip) { W("SUNRISE", 11, true, .7f); W("🌅 ${w.days[0].rise}", 18, true) }
                    Column(chip) { W("SUNSET", 11, true, .7f); W("🌇 ${w.days[0].set}", 18, true) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val chip = Modifier.weight(1f).glass(22).padding(14.dp)
                    Column(chip) { W("RAIN CHANCE", 11, true, .7f); W("${w.hours.maxOf { it.pop }}%", 18, true) }
                    Column(chip) { W("PRECIP / SNOW", 11, true, .7f); W("${"%.1f".format(w.hours.sumOf { it.mm })}mm · ${"%.1f".format(w.hours.sumOf { it.snow })}cm", 15, true) }
                }
                Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    W("7-DAY FORECAST", 12, true, .7f)
                    w.days.forEachIndexed { i, d ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                W(if (i == 0) "Today" else LocalDate.parse(d.date).dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, 16)
                            }
                            W("${d.min.toInt()}°", 16, a = .7f); Spacer(Modifier.width(14.dp)); W("${d.max.toInt()}°", 16, true)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
