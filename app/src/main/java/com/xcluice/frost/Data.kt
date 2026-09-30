package com.xcluice.frost

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
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

private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(20, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
private val httpFast = http.newBuilder().callTimeout(7, TimeUnit.SECONDS).build()

fun get(url: String, fast: Boolean = false): String {
    val r = Request.Builder().url(url).header("User-Agent", "FrostWeather/3.2").build()
    (if (fast) httpFast else http).newCall(r).execute().use {
        if (!it.isSuccessful) error("HTTP ${it.code}")
        return it.body!!.string()
    }
}

fun fetchRaw(p: Place): String {
    require(p.lat in -90.0..90.0 && p.lon in -180.0..180.0) { "Bad coordinates" }
    return get("https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,cloud_cover,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility,uv_index" +
        "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max" +
        "&forecast_days=15&timezone=auto&wind_speed_unit=kmh")
}

fun fetchAqi(p: Place): Int = try {
    JSONObject(get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${p.lat}&longitude=${p.lon}&current=us_aqi", true))
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

fun cond(c: Int, day: Int): String = when (c) {
    0 -> if (day == 1) "Clear" else "Clear night"
    1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
    45, 48 -> "Fog"; 51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"; 66, 67 -> "Freezing rain"; 71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Rain showers"; 85, 86 -> "Snow showers"; 95 -> "Thunderstorm"; 96, 99 -> "Thunderstorm, hail"
    else -> "Cloudy"
}

fun iconFor(c: Int, day: Int): Int {
    val d = day == 1
    return when (c) {
        0 -> if (d) R.drawable.ic_w_sun else R.drawable.ic_w_moon
        1, 2 -> if (d) R.drawable.ic_w_partly_day else R.drawable.ic_w_partly_night
        3 -> R.drawable.ic_w_cloud
        45, 48 -> R.drawable.ic_w_fog
        in 51..67, in 80..82 -> R.drawable.ic_w_rain
        in 71..77, 85, 86 -> R.drawable.ic_w_snow
        in 95..99 -> R.drawable.ic_w_storm
        else -> R.drawable.ic_w_cloud
    }
}

fun compass16(deg: Float): String =
    arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")[(((deg % 360f) / 22.5f) + .5f).toInt() % 16]

fun uvLevel(u: Double) = when { u < 3 -> "Very weak"; u < 5 -> "Weak"; u < 7 -> "Moderate"; u < 10 -> "Strong"; else -> "Very strong" }
fun humLabel(h: Int) = when { h < 30 -> "Dry"; h < 60 -> "Comfortable"; h < 80 -> "Humid"; else -> "Very Humid" }
fun mins(t: String): Int { val s = t.takeLast(5); return s.take(2).toInt() * 60 + s.takeLast(2).toInt() }
fun hour12(t: String): String { val h = t.substring(11, 13).toInt(); return "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}" }
fun dayName(date: String, i: Int) = when (i) {
    0 -> "Today"; 1 -> "Tomorrow"
    else -> LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

fun t12(s: String): String {
    val x = s.takeLast(5); val h = x.take(2).toInt()
    return "${if (h % 12 == 0) 12 else h % 12}:${x.takeLast(2)}${if (h < 12) "am" else "pm"}"
}
fun md(d: String) = "${d.substring(5, 7).toInt()}/${d.substring(8, 10).toInt()}"
