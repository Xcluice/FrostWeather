package com.xcluice.frost

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

val Context.store by preferencesDataStore("frost")
private val K_PLACE = stringPreferencesKey("place")
private val K_SAVED = stringPreferencesKey("saved")
private val K_WX = stringPreferencesKey("wx")
private val K_AQI = intPreferencesKey("aqi")

data class Place(val name: String, val lat: Double, val lon: Double, val sub: String = "")
data class Cur(val temp: Double, val feels: Double, val hum: Int, val code: Int, val isDay: Int, val cloud: Int,
               val press: Int, val wind: Double, val dir: Float, val gust: Double, val vis: Double, val uv: Double)
data class Hour(val time: String, val temp: Double, val pop: Int, val code: Int, val isDay: Int)
data class Day(val date: String, val max: Double, val min: Double, val code: Int, val rise: String, val set: String,
               val uv: Double, val prcp: Double, val pop: Int, val wind: Double)
data class Wx(val place: Place, val cur: Cur, val hours: List<Hour>, val days: List<Day>, val aqi: Int, val now: String)

fun Place.json(): JSONObject = JSONObject().put("n", name).put("a", lat).put("o", lon).put("s", sub)
fun JSONObject.place() = Place(getString("n"), getDouble("a"), getDouble("o"), optString("s"))

fun get(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 15000; c.readTimeout = 20000
    c.setRequestProperty("User-Agent", "FrostWeather/2.0")
    try {
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().readText()
    } finally { c.disconnect() }
}

fun fetchRaw(p: Place): String {
    require(p.lat in -90.0..90.0 && p.lon in -180.0..180.0) { "Bad coordinates" }
    return get("https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,cloud_cover,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility,uv_index" +
        "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max" +
        "&forecast_days=7&timezone=auto&wind_speed_unit=kmh")
}

fun fetchAqi(p: Place): Int = try {
    JSONObject(get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${p.lat}&longitude=${p.lon}&current=us_aqi"))
        .getJSONObject("current").getInt("us_aqi")
} catch (e: Exception) { -1 }

fun parse(p: Place, raw: String, aqiIn: Int): Wx {
    val j = JSONObject(raw)
    val c = j.getJSONObject("current")
    fun n(o: JSONObject, k: String) = if (o.isNull(k)) 0.0 else o.getDouble(k)
    fun JSONArray.d(i: Int) = if (isNull(i)) 0.0 else getDouble(i)
    val cur = Cur(n(c, "temperature_2m"), n(c, "apparent_temperature"), n(c, "relative_humidity_2m").roundToInt(),
        c.optInt("weather_code"), c.optInt("is_day", 1), n(c, "cloud_cover").roundToInt(), n(c, "surface_pressure").roundToInt(),
        n(c, "wind_speed_10m"), n(c, "wind_direction_10m").toFloat(), n(c, "wind_gusts_10m"), n(c, "visibility") / 1000.0, n(c, "uv_index"))
    val now = c.getString("time")
    val h = j.getJSONObject("hourly")
    val ht = h.getJSONArray("time")
    val s = (0 until ht.length()).firstOrNull { ht.getString(it).take(13) >= now.take(13) } ?: 0
    val hours = (s until minOf(s + 24, ht.length())).map { i ->
        Hour(ht.getString(i), h.getJSONArray("temperature_2m").d(i), h.getJSONArray("precipitation_probability").d(i).roundToInt(),
            h.getJSONArray("weather_code").d(i).toInt(), h.getJSONArray("is_day").d(i).toInt())
    }
    val d = j.getJSONObject("daily")
    val dt = d.getJSONArray("time")
    val days = (0 until dt.length()).map { i ->
        Day(dt.getString(i), d.getJSONArray("temperature_2m_max").d(i), d.getJSONArray("temperature_2m_min").d(i),
            d.getJSONArray("weather_code").d(i).toInt(), d.getJSONArray("sunrise").getString(i), d.getJSONArray("sunset").getString(i),
            d.getJSONArray("uv_index_max").d(i), d.getJSONArray("precipitation_sum").d(i),
            d.getJSONArray("precipitation_probability_max").d(i).roundToInt(), d.getJSONArray("wind_speed_10m_max").d(i))
    }
    val aqi = if (aqiIn >= 0) aqiIn else (25 + cur.hum * 0.35 + cur.cloud * 0.15).roundToInt()
    return Wx(p, cur, hours, days, aqi, now)
}

fun search(q: String): List<Place> {
    val a = JSONObject(get("https://photon.komoot.io/api/?limit=10&q=" + URLEncoder.encode(q, "UTF-8"))).getJSONArray("features")
    val seen = HashSet<String>(); val out = ArrayList<Place>()
    for (i in 0 until a.length()) {
        val f = a.getJSONObject(i); val pr = f.getJSONObject("properties")
        val co = f.getJSONObject("geometry").getJSONArray("coordinates")
        val name = pr.optString("name"); if (name.isBlank()) continue
        val lat = co.getDouble(1); val lon = co.getDouble(0)
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
        val sub = listOf(pr.optString("state"), pr.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
        val key = name.lowercase().trim() + "|" + (lat * 10).roundToInt() + "|" + (lon * 10).roundToInt()
        if (seen.add(key)) out.add(Place(name, lat, lon, sub))
    }
    return out
}

fun reverse(lat: Double, lon: Double): Place {
    val f = JSONObject(get("https://photon.komoot.io/reverse?lat=$lat&lon=$lon")).getJSONArray("features")
    val pr = if (f.length() > 0) f.getJSONObject(0).getJSONObject("properties") else JSONObject()
    val name = listOf("city", "name", "county", "state").map { pr.optString(it) }.firstOrNull { it.isNotBlank() } ?: "My location"
    return Place(name, lat, lon, pr.optString("country"))
}

suspend fun Context.saveWx(p: Place, raw: String, aqi: Int) = store.edit { it[K_PLACE] = p.json().toString(); it[K_WX] = raw; it[K_AQI] = aqi }
suspend fun Context.saveList(l: List<Place>) = store.edit { it[K_SAVED] = JSONArray(l.map { p -> p.json() }).toString() }

suspend fun Context.readAll(): Triple<Place?, Wx?, List<Place>> {
    val p = store.data.first()
    val place = p[K_PLACE]?.let { runCatching { JSONObject(it).place() }.getOrNull() }
    val raw = p[K_WX]
    val wx = if (place != null && raw != null) runCatching { parse(place, raw, p[K_AQI] ?: -1) }.getOrNull() else null
    val saved = p[K_SAVED]?.let { s ->
        runCatching { JSONArray(s).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).place() } } }.getOrNull()
    } ?: emptyList()
    return Triple(place, wx, saved)
}

fun desc(c: Int, day: Int): Pair<String, String> {
    val d = day == 1
    return when (c) {
        0 -> (if (d) "☀️" else "🌙") to (if (d) "Clear sky" else "Clear night")
        1 -> (if (d) "🌤️" else "🌙") to "Mainly clear"
        2 -> (if (d) "⛅" else "☁️") to "Partly cloudy"
        3 -> "☁️" to "Overcast"
        45, 48 -> "🌫️" to "Fog"
        51, 53, 55 -> "🌦️" to "Drizzle"
        56, 57 -> "🌧️" to "Freezing drizzle"
        61, 63, 65 -> "🌧️" to "Rain"
        66, 67 -> "🌧️" to "Freezing rain"
        71, 73, 75, 77 -> "❄️" to "Snow"
        80, 81, 82 -> "🌦️" to "Rain showers"
        85, 86 -> "🌨️" to "Snow showers"
        95 -> "⛈️" to "Thunderstorm"
        96, 99 -> "⛈️" to "Thunderstorm, hail"
        else -> "🌡️" to "Unknown"
    }
}

fun compass16(deg: Float): String =
    arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")[(((deg % 360f) / 22.5f) + .5f).toInt() % 16]

fun uvLevel(u: Double) = when { u < 3 -> "Low"; u < 6 -> "Moderate"; u < 8 -> "High"; u < 11 -> "Very High"; else -> "Extreme" }
fun humLabel(h: Int) = when { h < 30 -> "Dry"; h < 60 -> "Comfortable"; h < 80 -> "Humid"; else -> "Very Humid" }
fun mins(t: String): Int { val s = t.takeLast(5); return s.take(2).toInt() * 60 + s.takeLast(2).toInt() }
fun hour12(t: String): String { val h = t.substring(11, 13).toInt(); return "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}" }
fun dayName(date: String, i: Int) = when (i) {
    0 -> "Today"; 1 -> "Tomorrow"
    else -> LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}
