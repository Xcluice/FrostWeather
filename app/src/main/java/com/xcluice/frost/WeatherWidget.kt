package com.xcluice.frost

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

class WeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                val (place, cached, _) = runBlocking { context.readAll() }
                if (cached != null) ids.forEach { mgr.updateAppWidget(it, build(context, cached)) }
                val p = place ?: Place("Srinagar", 34.0837, 74.7973, "Jammu & Kashmir, India")
                try {
                    val raw = fetchRaw(p); val aqi = fetchAqi(p)
                    val fresh = parse(p, raw, aqi)
                    runBlocking { context.saveWx(p, raw, aqi) }
                    ids.forEach { mgr.updateAppWidget(it, build(context, fresh)) }
                } catch (e: Exception) {
                    if (cached == null) ids.forEach { mgr.updateAppWidget(it, build(context, null)) }
                }
            } finally { pending.finish() }
        }.start()
    }

    companion object {
        fun push(ctx: Context, w: Wx) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWidget::class.java))
            ids.forEach { mgr.updateAppWidget(it, build(ctx, w)) }
        }

        fun build(ctx: Context, w: Wx?): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_weather)
            val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.w_root, pi)
            if (w == null) {
                rv.setTextViewText(R.id.w_city, "Open Frost to set up")
                return rv
            }
            val (emo, txt) = desc(w.cur.code, w.cur.isDay)
            val d = w.days[0]
            val bg = when {
                w.cur.isDay == 0 -> R.drawable.widget_bg_night
                w.cur.code >= 2 -> R.drawable.widget_bg_cloud
                else -> R.drawable.widget_bg_day
            }
            rv.setInt(R.id.w_root, "setBackgroundResource", bg)
            rv.setTextViewText(R.id.w_city, w.place.name)
            rv.setTextViewText(R.id.w_emoji, emo)
            rv.setTextViewText(R.id.w_temp, "${w.cur.temp.roundToInt()}°")
            rv.setTextViewText(R.id.w_cond, txt)
            rv.setTextViewText(R.id.w_hl, "↑${d.max.roundToInt()}°  ↓${d.min.roundToInt()}°  ·  Feels ${w.cur.feels.roundToInt()}°")
            val t = intArrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3)
            val e = intArrayOf(R.id.e0, R.id.e1, R.id.e2, R.id.e3)
            val v = intArrayOf(R.id.v0, R.id.v1, R.id.v2, R.id.v3)
            for (i in 0 until 4) {
                val h = w.hours.getOrNull(i + 1) ?: continue
                rv.setTextViewText(t[i], hour12(h.time))
                rv.setTextViewText(e[i], desc(h.code, h.isDay).first)
                rv.setTextViewText(v[i], "${h.temp.roundToInt()}°")
            }
            return rv
        }
    }
}
