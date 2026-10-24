package com.alejandro.tvlauncher
 
import android.animation.ValueAnimator
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.SoundPool
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
 
class MainActivity : Activity() {
    private class Skin(val name: String, val top: Int, val bottom: Int, val accent: Int)
    private class Opt(val label: String, val run: () -> Unit)
    private class Wall(val file: File, val title: String?, val pkg: String?, val prov: String?)
 
    private val skins = listOf(
        Skin("Ámbar", 0xFF2B1B10.toInt(), 0xFF0F0E17.toInt(), 0xFFFFB347.toInt()),
        Skin("Océano", 0xFF0B2233.toInt(), 0xFF070C14.toInt(), 0xFF4DD0E1.toInt()),
        Skin("Aurora", 0xFF1A1233.toInt(), 0xFF0A0A14.toInt(), 0xFFB388FF.toInt()),
        Skin("Sakura", 0xFF2E1424.toInt(), 0xFF0E0A12.toInt(), 0xFFFF7EB6.toInt())
    )
    private val sizeNames = arrayOf("Pequeño", "Medio", "Grande")
    private val sizeScale = floatArrayOf(0.85f, 1f, 1.2f)
    private val autoSecs = intArrayOf(0, 10, 30, 60, 300, 900, 1800)
    private val autoNames = arrayOf("No", "cada 10 s", "cada 30 s", "cada 1 min", "cada 5 min", "cada 15 min", "cada 30 min")
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val knownFavs = setOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
        "com.wbd.stream", "com.spotify.tv.android", "com.univision.prendetv")
 
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var sig = ""
    private var wallBmp: Bitmap? = null
    private var blurBmp: Bitmap? = null
    private var curWall: Wall? = null
    private var wallLayer: FrameLayout? = null
    private var wallView: ImageView? = null
    private var wallBlur: ImageView? = null
    private class Stats {
        var ram = 0f; var disk = 0f; var temp = -1f; var mbps = 0f; var sig = 0f
        var wifi = false; var lan = false; var inet = false
        var availMb = 0L; var freeGb = 0f; var ip = "—"
    }
    private val stat = Stats()
    private var lastRx = -1L
    private var lastRxT = 0L
    private var hdmiOn = false
    private var musicOn = false
    private var tempG: GaugeView? = null
    private var clusterV: ClusterView? = null
    private var segV: SegView? = null
    private var sportV: SportBar? = null
    private val indicators = mutableListOf<IndicatorView>()
    private var sysInfo: TextView? = null
    private var wxBig: TextView? = null
    private var wxDesc2: TextView? = null
    private var wxDetail: TextView? = null
    private var wxTom: TextView? = null
    private val gNames = arrayOf("Aguja", "Anillo", "Cluster", "Segmentos")
    private val barNames = arrayOf("Clásica", "Tablero", "Deportiva")
    private var ramG: GaugeView? = null
    private var diskG: GaugeView? = null
    private var netG: GaugeView? = null
    private var statusRow: LinearLayout? = null
    private var weatherView: TextView? = null
    private var heroBox: LinearLayout? = null
    private var heroProv: TextView? = null
    private var heroTitle: TextView? = null
    private var heroBtn: TextView? = null
    private var mediaBox: LinearLayout? = null
    private var mediaCtl: LinearLayout? = null
    private var mediaArt: ImageView? = null
    private var mediaTitle: TextView? = null
    private var mediaArtist: TextView? = null
    private var mediaPlay: TextView? = null
    private var ctrl: MediaController? = null
    private val seasonNames = arrayOf("Automática", "Apagada", "Halloween", "Día de Muertos", "Navidad", "San Valentín")
    private val cardNames = arrayOf("Cristal", "Sólido", "Neón")
    private val sndNames = arrayOf("Apagados", "Suave", "Tecno", "Retro")
    private val volNames = arrayOf("Bajo", "Medio", "Alto")
    private var sp: SoundPool? = null
    private val sndIds = mutableListOf<Int>()
    private var sfxReady = false
    private var lastSfx = 0L
    private var splashShown = false
    private var captionAction: () -> Unit = { }
    private var lastWx = 0L
 
    private val skin get() = skins[prefs.getInt("theme", 0) % skins.size]
    private val scale get() = sizeScale[prefs.getInt("size", 1) % 3]
    private fun flag(k: String): Boolean = prefs.getBoolean(k, true)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun light(): Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private fun getSet(k: String): Set<String> = prefs.getStringSet(k, emptySet())!!.toSet()
    private fun putSet(k: String, s: Set<String>) = prefs.edit().putStringSet(k, s).apply()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun withAlpha(c: Int, a: Int): Int = (a shl 24) or (c and 0xFFFFFF)
 
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("p", 0)
        if (!prefs.contains("fav")) putSet("fav", knownFavs)
        getExternalFilesDir("fondos")
        loadSounds()
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) tts?.language = Locale("es", "MX") }
    }
 
    override fun onDestroy() { tts?.shutdown(); sp?.release(); super.onDestroy() }
 
    override fun onResume() {
        super.onResume()
        val s = apps().joinToString(",") { it.activityInfo.packageName }
        if (s != sig) { sig = s; build() }
        handler.removeCallbacks(tick); handler.post(tick)
        scheduleWall()
        if (flag("wg_weather") && System.currentTimeMillis() - lastWx > 30 * 60_000L) fetchWeather()
    }
 
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick); handler.removeCallbacks(wallTask)
    }
 
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* el inicio no se cierra */ }
 
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (rc == 77 && res == RESULT_OK) {
            val q = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (q != null) ask(q)
        }
    }
 
    private val tick = object : Runnable { override fun run() { refreshStats(); handler.postDelayed(this, if (liteOn()) 6000L else 2500L) } }
    private val wallTask = object : Runnable { override fun run() { nextWall(); scheduleWall() } }
 
    private fun go(i: Intent, quiet: Boolean = false): Boolean = try {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { if (!quiet) toast("No se pudo abrir"); false }
 
    private fun goPkg(p: String): Boolean {
        val i = packageManager.getLeanbackLaunchIntentForPackage(p) ?: packageManager.getLaunchIntentForPackage(p) ?: return false
        return go(i)
    }
 
    private fun isInstalled(p: String): Boolean = try { packageManager.getPackageInfo(p, 0); true } catch (e: Exception) { false }
 
    private fun apps(): List<ResolveInfo> {
        val pm = packageManager
        return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }
 
    private fun httpGet(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        return c.inputStream.bufferedReader().use { it.readText() }
    }
 
    // ---------- fondos ----------
    private fun userFiles(): List<File> =
        getExternalFilesDir("fondos")?.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            ?.sortedBy { it.name } ?: emptyList()
 
    private fun walls(): List<Wall> {
        if (prefs.getInt("wallmode", 0) == 1) {
            return try {
                val arr = JSONArray(File(cacheDir, "cartelera/index.json").readText())
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    Wall(File(cacheDir, "cartelera/" + o.getString("f")), o.getString("t"), o.getString("p"), o.getString("v"))
                }.filter { it.file.exists() }
            } catch (e: Throwable) { emptyList() }
        }
        return userFiles().map { Wall(it, null, null, null) }
    }
 
    private fun decode(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 1920 && o.outHeight / (s * 2) >= 1080) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Throwable) { null }
 
    private fun loadWall(w: Wall, animate: Boolean) {
        Thread {
            val b = decode(w.file)
            val bl = if (b != null) Bitmap.createScaledBitmap(b, maxOf(1, b.width / 28), maxOf(1, b.height / 28), true) else null
            runOnUiThread { if (b != null) { wallBmp = b; blurBmp = bl; curWall = w; applyWall(animate); updateCaption() } }
        }.start()
    }
 
    private fun applyWall(animate: Boolean) {
        val layer = wallLayer ?: return
        val fit = prefs.getInt("fit", 0) == 1
        fun put() {
            wallView?.scaleType = if (fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            wallView?.setImageBitmap(wallBmp)
            wallBlur?.setImageBitmap(blurBmp)
            wallBlur?.visibility = if (fit) View.VISIBLE else View.GONE
        }
        if (!animate || liteOn()) { put(); return }
        layer.animate().alpha(0f).setDuration(250).withEndAction {
            put(); layer.animate().alpha(1f).setDuration(500).start()
        }.start()
    }
 
    private fun updateCaption() {
        val box = heroBox ?: return
        val w = curWall
        val t = w?.title
        if (prefs.getInt("wallmode", 0) == 1 && w != null && t != null) {
            heroProv?.text = (w.prov ?: "").uppercase()
            heroTitle?.text = t
            heroBtn?.text = "Ver en ${w.prov}"
            val p = w.pkg
            captionAction = { if (p != null) goPkg(p) }
            box.visibility = View.VISIBLE
        } else box.visibility = View.GONE
    }
 
    private fun nextWall() {
        val ws = walls(); if (ws.isEmpty()) return
        val idx = (prefs.getInt("wall", -1) + 1) % ws.size
        prefs.edit().putInt("wall", idx).apply()
        loadWall(ws[idx], true)
    }
 
    private fun scheduleWall() {
        handler.removeCallbacks(wallTask)
        val s = autoSecs[prefs.getInt("auto", 0) % autoSecs.size]
        if (s > 0) handler.postDelayed(wallTask, s * 1000L)
    }
 
    private fun refreshCartelera() {
        val keyFile = File(getExternalFilesDir(null), "tmdb.txt")
        if (!keyFile.exists()) { toast("Falta tmdb.txt con tu clave (ver guía)"); return }
        val key = keyFile.readText().trim()
        toast("Descargando cartelera…")
        Thread {
            try {
                val dir = File(cacheDir, "cartelera"); dir.mkdirs(); dir.listFiles()?.forEach { it.delete() }
                val provs = listOf(
                    Triple("com.netflix.ninja", 8, "Netflix"), Triple("com.amazon.amazonvideo.livingroom", 119, "Prime Video"),
                    Triple("com.disney.disneyplus", 337, "Disney+"), Triple("com.wbd.stream", 1899, "Max"),
                    Triple("com.apple.atve.androidtv.appletv", 350, "Apple TV+"))
                var sel = provs.filter { isInstalled(it.first) }
                if (sel.isEmpty()) sel = provs.take(1)
                val out = JSONArray(); var n = 0
                for (p in sel) {
                    val j = JSONObject(httpGet("https://api.themoviedb.org/3/discover/movie?api_key=$key&language=es-MX&watch_region=MX&with_watch_providers=${p.second}&with_watch_monetization_types=flatrate&sort_by=popularity.desc"))
                    val res = j.getJSONArray("results"); var c = 0
                    for (i in 0 until res.length()) {
                        if (c >= 4) break
                        val m = res.getJSONObject(i); val bp = m.optString("backdrop_path", "")
                        if (bp.isEmpty() || bp == "null") continue
                        val name = "b${n++}.jpg"
                        URL("https://image.tmdb.org/t/p/w1280$bp").openStream().use { ins -> File(dir, name).outputStream().use { o -> ins.copyTo(o) } }
                        out.put(JSONObject().put("f", name).put("t", m.optString("title")).put("p", p.first).put("v", p.third))
                        c++
                    }
                }
                File(dir, "index.json").writeText(out.toString())
                runOnUiThread { toast("Cartelera lista"); nextWall() }
            } catch (e: Exception) { runOnUiThread { toast("Error de cartelera: revisa tu clave de TMDB") } }
        }.start()
    }
 
    // ---------- clima ----------
    private fun wxDesc(c: Int): String = when (c) {
        0 -> "Despejado"; 1, 2 -> "Parcialmente nublado"; 3 -> "Nublado"; 45, 48 -> "Niebla"
        in 51..57 -> "Llovizna"; in 61..67, in 80..82 -> "Lluvia"; in 71..77, 85, 86 -> "Nieve"
        in 95..99 -> "Tormenta"; else -> ""
    }
 
    private fun fetchWeather() {
        lastWx = System.currentTimeMillis()
        Thread {
            try {
                val lat = prefs.getFloat("lat", 19.93f); val lon = prefs.getFloat("lon", -96.85f)
                val root = JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,relative_humidity_2m,apparent_temperature,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=2&timezone=auto"))
                val j = root.getJSONObject("current"); val d = root.getJSONObject("daily")
                val t = Math.round(j.getDouble("temperature_2m"))
                val desc = wxDesc(j.getInt("weather_code"))
                val top = "$t° · $desc"
                val detail = "Sensación ${Math.round(j.optDouble("apparent_temperature"))}° · Humedad ${j.optInt("relative_humidity_2m")}% · Viento ${Math.round(j.optDouble("wind_speed_10m"))} km/h"
                val tom = "Mañana: ${Math.round(d.getJSONArray("temperature_2m_min").getDouble(1))}°–${Math.round(d.getJSONArray("temperature_2m_max").getDouble(1))}° · Lluvia ${d.getJSONArray("precipitation_probability_max").optInt(1)}%"
                runOnUiThread { weatherView?.text = top; wxBig?.text = "$t°"; wxDesc2?.text = desc; wxDetail?.text = detail; wxTom?.text = tom }
            } catch (e: Exception) { lastWx = 0L }
        }.start()
    }
 
    // ---------- medidores ----------
    private inner class GaugeView(ctx: Context, val title: String) : View(ctx) {
        private var shown = 0f
        private var text = ""
        private var warn = false
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0x33FFFFFF }
        private val prog = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val tickP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x77FFFFFF; strokeWidth = 2f }
        private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND }
        private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        private val lab = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD6D0E0.toInt(); textAlign = Paint.Align.CENTER }
        private val rect = RectF()
 
        fun set(v: Float, t: String, w: Boolean = false) {
            text = t; warn = w
            ValueAnimator.ofFloat(shown, v.coerceIn(0f, 1f)).apply {
                duration = 700
                addUpdateListener { shown = it.animatedValue as Float; invalidate() }
                start()
            }
        }
 
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val cx = w / 2; val cy = height / 2f
            val ring = prefs.getInt("gstyle", 0) % 4 == 1
            val sw = w * (if (ring) 0.11f else 0.075f); val r = w / 2 - sw * 1.5f
            track.strokeWidth = sw; prog.strokeWidth = sw
            rect.set(cx - r, cy - r, cx + r, cy + r)
            prog.color = if (warn) 0xFFFF6B6B.toInt() else skin.accent
            if (ring) {
                c.drawArc(rect, -90f, 360f, false, track)
                if (shown > 0.001f) c.drawArc(rect, -90f, 360f * shown, false, prog)
                num.textSize = r * 0.5f; lab.textSize = r * 0.26f
                c.drawText(text, cx, cy + r * 0.16f, num)
                c.drawText(title, cx, cy + r * 0.55f, lab)
                return
            }
            c.drawArc(rect, 150f, 240f, false, track)
            if (shown > 0.001f) c.drawArc(rect, 150f, 240f * shown, false, prog)
            for (i in 0..12) {
                val a = Math.toRadians(150.0 + 240.0 * i / 12)
                val r1 = r + sw * 0.85f; val r2 = r + sw * 1.25f
                c.drawLine(cx + (r1 * Math.cos(a)).toFloat(), cy + (r1 * Math.sin(a)).toFloat(),
                    cx + (r2 * Math.cos(a)).toFloat(), cy + (r2 * Math.sin(a)).toFloat(), tickP)
            }
            val na = Math.toRadians(150.0 + 240.0 * shown)
            c.drawLine(cx, cy, cx + (r * 0.62f * Math.cos(na)).toFloat(), cy + (r * 0.62f * Math.sin(na)).toFloat(), needle)
            c.drawCircle(cx, cy, sw * 0.55f, needle)
            lab.textSize = r * 0.24f; num.textSize = r * 0.38f
            c.drawText(title, cx, cy + r * 0.45f, lab)
            c.drawText(text, cx, cy + r * 0.93f, num)
        }
    }
 
    private fun gauge(title: String): GaugeView = GaugeView(this, title)
    private fun btOn(): Boolean = try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true } catch (e: Throwable) { false }
 
    private fun pill(label: String, on: Boolean): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (on) 0xFF7BE08F.toInt() else 0xFF5A5566.toInt()) }
        }, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(6) })
        row.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(if (on) Color.WHITE else 0xFFB0A9BC.toInt()) })
        row.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(12) }
        return row
    }
 
    private fun fillStatus(row: LinearLayout) {
        row.removeAllViews()
        try {
            val tim = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
            tim.tvInputList.filter { it.isPassthroughInput }.sortedBy { labelOf(it) }.take(4).forEach {
                row.addView(pill(labelOf(it), tim.getInputState(it.id) == TvInputManager.INPUT_STATE_CONNECTED))
            }
        } catch (e: Throwable) { }
        row.addView(pill("Bluetooth", btOn()))
    }
 
    private fun readTemp(): Float {
        var best = -1f
        try {
            File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }?.forEach {
                try {
                    val v = File(it, "temp").readText().trim().toFloat()
                    val c = if (v > 1000f) v / 1000f else v
                    if (c in 10f..120f && c > best) best = c
                } catch (e: Throwable) { }
            }
        } catch (e: Throwable) { }
        return best
    }
 
    private fun ease(cur: Float, target: Float): Float = cur + (target - cur) * 0.18f
 
    private fun segs(c: Canvas, p: Paint, path: Path, x: Float, y: Float, w: Float, h: Float, v: Float, n: Int) {
        val gap = dp(3).toFloat(); val sw = (w - gap * (n - 1)) / n; val sl = h * 0.35f
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            val x0 = x + i * (sw + gap)
            val on = (i + 1f) / n <= v + 0.001f
            val red = i >= n * 0.8f
            p.color = if (on) (if (red) 0xFFFF3B30.toInt() else skin.accent) else (if (red) 0x55FF3B30 else 0x33FFFFFF)
            path.reset(); path.moveTo(x0 + sl, y); path.lineTo(x0 + sw + sl, y); path.lineTo(x0 + sw, y + h); path.lineTo(x0, y + h); path.close()
            c.drawPath(path, p)
        }
    }
 
    @Suppress("DEPRECATION")
    private fun refreshStats() {
        try {
            val mi = ActivityManager.MemoryInfo()
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            stat.ram = (mi.totalMem - mi.availMem).toFloat() / mi.totalMem
            stat.availMb = mi.availMem / 1048576L
        } catch (e: Throwable) { }
        try {
            val d = StatFs(Environment.getDataDirectory().path)
            stat.disk = (d.totalBytes - d.availableBytes).toFloat() / d.totalBytes
            stat.freeGb = d.availableBytes / 1.0e9f
        } catch (e: Throwable) { }
        stat.temp = readTemp()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            stat.lan = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            stat.wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            stat.inet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            stat.sig = if (stat.lan) 1f else if (stat.wifi) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                ((wm.connectionInfo.rssi + 100) * 2).coerceIn(0, 100) / 100f
            } else 0f
            stat.ip = cm.getLinkProperties(cm.activeNetwork)?.linkAddresses?.map { it.address }
                ?.firstOrNull { it is Inet4Address }?.hostAddress ?: "—"
        } catch (e: Throwable) { }
        val rx = TrafficStats.getTotalRxBytes(); val t = SystemClock.elapsedRealtime()
        if (lastRx >= 0 && t > lastRxT && rx >= lastRx) {
            val m = (rx - lastRx) * 8f / 1e6f / ((t - lastRxT) / 1000f)
            stat.mbps = stat.mbps * 0.5f + m * 0.5f
        }
        lastRx = rx; lastRxT = t
        try {
            val l = (getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).getActiveSessions(ComponentName(this, MediaListener::class.java))
            musicOn = l.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        } catch (e: Throwable) { musicOn = false }
        try {
            val tim = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
            hdmiOn = tim.tvInputList.any { it.isPassthroughInput && tim.getInputState(it.id) == TvInputManager.INPUT_STATE_CONNECTED }
        } catch (e: Throwable) { hdmiOn = false }
 
        ramG?.set(stat.ram, "${(stat.ram * 100).toInt()}%", stat.ram > 0.85f)
        diskG?.set(stat.disk, "${(stat.disk * 100).toInt()}%", stat.disk > 0.9f)
        tempG?.let { if (stat.temp < 0f) it.set(0f, "N/D") else it.set(stat.temp / 100f, "${stat.temp.toInt()}°", stat.temp > 75f) }
        netG?.let {
            if (!stat.inet && !stat.lan && !stat.wifi) it.set(0f, "OFF", true)
            else if (stat.lan) it.set(1f, "LAN") else it.set(stat.sig, "${(stat.sig * 100).toInt()}%", stat.sig < 0.3f)
        }
        clusterV?.invalidate(); segV?.invalidate(); sportV?.invalidate()
        indicators.forEach { ind ->
            ind.lit = when (ind.kind) { 0 -> stat.wifi; 1 -> stat.lan; 2 -> btOn(); 3 -> hdmiOn; 4 -> musicOn; 5 -> dnsOn(); else -> stat.inet }
            ind.invalidate()
        }
        statusRow?.let { fillStatus(it) }
        val up = SystemClock.elapsedRealtime() / 60000
        sysInfo?.text = "Memoria libre: ${stat.availMb} MB\nDisco libre: ${"%.1f".format(stat.freeGb)} GB\nEncendida: ${up / 60} h ${up % 60} min\nIP: ${stat.ip}\nDescarga: ${"%.1f".format(stat.mbps)} Mbps"
        refreshMedia()
    }
 
    // ---------- indicadores tipo tablero ----------
    private inner class IndicatorView(ctx: Context, val kind: Int) : View(ctx) {
        var lit = false
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val rect = RectF()
        override fun onDraw(c: Canvas) {
            val s = width.toFloat(); val cx = s / 2; val cy = s / 2
            p.color = if (lit) (if (kind == 5) 0xFF7BE08F.toInt() else skin.accent) else 0x33FFFFFF
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.075f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
            when (kind) {
                0 -> {
                    for (i in 1..3) { val r = s * 0.2f * i; rect.set(cx - r, cy + s * 0.3f - r, cx + r, cy + s * 0.3f + r); c.drawArc(rect, 225f, 90f, false, p) }
                    p.style = Paint.Style.FILL; c.drawCircle(cx, cy + s * 0.3f, s * 0.06f, p)
                }
                1 -> {
                    rect.set(cx - s * 0.32f, cy - s * 0.22f, cx + s * 0.32f, cy + s * 0.18f); c.drawRoundRect(rect, s * 0.05f, s * 0.05f, p)
                    c.drawLine(cx - s * 0.16f, cy + s * 0.18f, cx - s * 0.16f, cy + s * 0.34f, p)
                    c.drawLine(cx + s * 0.16f, cy + s * 0.18f, cx + s * 0.16f, cy + s * 0.34f, p)
                    c.drawLine(cx - s * 0.3f, cy + s * 0.34f, cx + s * 0.3f, cy + s * 0.34f, p)
                }
                2 -> {
                    path.reset(); path.moveTo(cx - s * 0.25f, cy - s * 0.2f); path.lineTo(cx + s * 0.25f, cy + s * 0.2f); path.lineTo(cx, cy + s * 0.4f)
                    path.lineTo(cx, cy - s * 0.4f); path.lineTo(cx + s * 0.25f, cy - s * 0.2f); path.lineTo(cx - s * 0.25f, cy + s * 0.2f); c.drawPath(path, p)
                }
                3 -> {
                    path.reset(); path.moveTo(cx - s * 0.4f, cy - s * 0.25f); path.lineTo(cx + s * 0.4f, cy - s * 0.25f); path.lineTo(cx + s * 0.4f, cy + s * 0.05f)
                    path.lineTo(cx + s * 0.22f, cy + s * 0.32f); path.lineTo(cx - s * 0.22f, cy + s * 0.32f); path.lineTo(cx - s * 0.4f, cy + s * 0.05f); path.close(); c.drawPath(path, p)
                }
                4 -> {
                    p.style = Paint.Style.FILL; c.drawCircle(cx - s * 0.14f, cy + s * 0.28f, s * 0.15f, p)
                    p.style = Paint.Style.STROKE; c.drawLine(cx + s * 0.01f, cy + s * 0.28f, cx + s * 0.01f, cy - s * 0.38f, p)
                    c.drawLine(cx + s * 0.01f, cy - s * 0.38f, cx + s * 0.3f, cy - s * 0.2f, p)
                }
                5 -> {
                    path.reset(); path.moveTo(cx, cy - s * 0.42f); path.lineTo(cx + s * 0.34f, cy - s * 0.25f); path.lineTo(cx + s * 0.3f, cy + s * 0.1f)
                    path.lineTo(cx, cy + s * 0.42f); path.lineTo(cx - s * 0.3f, cy + s * 0.1f); path.lineTo(cx - s * 0.34f, cy - s * 0.25f); path.close(); c.drawPath(path, p)
                }
                else -> {
                    c.drawCircle(cx, cy, s * 0.36f, p)
                    rect.set(cx - s * 0.16f, cy - s * 0.36f, cx + s * 0.16f, cy + s * 0.36f); c.drawOval(rect, p)
                    c.drawLine(cx - s * 0.36f, cy, cx + s * 0.36f, cy, p)
                }
            }
        }
    }
 
    // ---------- medidor tipo cluster de auto ----------
    private inner class ClusterView(ctx: Context) : View(ctx) {
        private var a = 0f; private var b = 0f; private var d = 0f
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
        private val rect = RectF()
 
        private fun dial(c: Canvas, cx: Float, cy: Float, r: Float, v: Float, nums: Int, mx: Float) {
            p.style = Paint.Style.FILL; p.color = 0xCC081428.toInt(); c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.06f; p.color = withAlpha(skin.accent, 0xDD)
            rect.set(cx - r * 0.96f, cy - r * 0.96f, cx + r * 0.96f, cy + r * 0.96f); c.drawArc(rect, 0f, 360f, false, p)
            p.color = 0xFFFF3B30.toInt(); p.strokeWidth = r * 0.07f
            rect.set(cx - r * 0.84f, cy - r * 0.84f, cx + r * 0.84f, cy + r * 0.84f); c.drawArc(rect, 150f + 240f * 0.85f, 240f * 0.15f, false, p)
            p.color = 0xCCFFFFFF.toInt(); p.strokeWidth = maxOf(2f, r * 0.02f)
            for (i in 0..20) {
                val ang = Math.toRadians(150.0 + 240.0 * i / 20); val len = if (i % 2 == 0) 0.16f else 0.09f
                c.drawLine(cx + (r * 0.76f * Math.cos(ang)).toFloat(), cy + (r * 0.76f * Math.sin(ang)).toFloat(),
                    cx + (r * (0.76f - len) * Math.cos(ang)).toFloat(), cy + (r * (0.76f - len) * Math.sin(ang)).toFloat(), p)
            }
            if (nums > 0) {
                tp.textSize = r * 0.15f
                for (i in 0..nums) {
                    val ang = Math.toRadians(150.0 + 240.0 * i / nums)
                    c.drawText("${(mx * i / nums).toInt()}", cx + (r * 0.5f * Math.cos(ang)).toFloat(), cy + (r * 0.5f * Math.sin(ang)).toFloat() + r * 0.05f, tp)
                }
            }
            val na = Math.toRadians(150.0 + 240.0 * v)
            p.color = 0xFFFF5A36.toInt(); p.strokeWidth = r * 0.05f; p.strokeCap = Paint.Cap.ROUND
            c.drawLine(cx, cy, cx + (r * 0.8f * Math.cos(na)).toFloat(), cy + (r * 0.8f * Math.sin(na)).toFloat(), p)
            p.style = Paint.Style.FILL; p.color = 0xFF05080F.toInt(); c.drawCircle(cx, cy, r * 0.13f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.03f; p.color = skin.accent; c.drawCircle(cx, cy, r * 0.13f, p)
        }
 
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val mx = if (stat.mbps > 100f) 500f else 100f
            val side = if (stat.temp >= 0f) stat.temp / 100f else stat.disk
            a = ease(a, stat.ram); b = ease(b, (stat.mbps / mx).coerceIn(0f, 1f)); d = ease(d, side)
            dial(c, w * 0.17f, h * 0.44f, h * 0.27f, a, 0, 100f)
            dial(c, w * 0.83f, h * 0.44f, h * 0.27f, d, 0, 100f)
            dial(c, w / 2, h * 0.42f, h * 0.40f, b, 5, mx)
            tp.textSize = dp(13).toFloat(); c.drawText("RAM ${(stat.ram * 100).toInt()}%", w * 0.17f, h * 0.93f, tp)
            c.drawText(if (stat.temp >= 0f) "${stat.temp.toInt()}°C" else "DISCO ${(stat.disk * 100).toInt()}%", w * 0.83f, h * 0.93f, tp)
            tp.textSize = dp(17).toFloat(); c.drawText("${"%.1f".format(stat.mbps)} Mbps", w / 2, h * 0.98f, tp)
            if (Math.abs(a - stat.ram) > 0.004f || Math.abs(d - side) > 0.004f) postInvalidateOnAnimation()
        }
    }
 
    // ---------- medidor de barras segmentadas (estilo deportivo) ----------
    private inner class SegView(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val vals = FloatArray(4)
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val t = floatArrayOf(stat.ram, stat.disk, if (stat.temp >= 0f) stat.temp / 100f else 0f, (stat.mbps / 100f).coerceIn(0f, 1f))
            val names = arrayOf("RAM", "DISCO", "TEMP", "RED")
            val txt = arrayOf("${(stat.ram * 100).toInt()}%", "${(stat.disk * 100).toInt()}%",
                if (stat.temp >= 0f) "${stat.temp.toInt()}°" else "N/D", "${stat.mbps.toInt()} Mb")
            var moving = false
            for (i in 0 until 4) {
                vals[i] = ease(vals[i], t[i]); if (Math.abs(vals[i] - t[i]) > 0.004f) moving = true
                val y = dp(4) + i * dp(30).toFloat()
                p.style = Paint.Style.FILL; p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
                p.textSize = dp(12).toFloat(); p.textAlign = Paint.Align.LEFT; p.color = 0xFFD6D0E0.toInt()
                c.drawText(names[i], 0f, y + dp(15), p)
                segs(c, p, path, dp(52).toFloat(), y, w - dp(52 + 58).toFloat(), dp(20).toFloat(), vals[i], 20)
                p.style = Paint.Style.FILL; p.textAlign = Paint.Align.RIGHT; p.textSize = dp(15).toFloat(); p.color = Color.WHITE
                c.drawText(txt[i], w, y + dp(16), p)
            }
            if (moving) postInvalidateOnAnimation()
        }
    }
 
    // ---------- barra larga deportiva ----------
    private inner class SportBar(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private var v = 0f
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            v = ease(v, stat.ram)
            val x0 = dp(92).toFloat(); val x1 = w - dp(36 + 330)
            p.style = Paint.Style.FILL; p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
            p.textAlign = Paint.Align.CENTER; p.textSize = dp(11).toFloat()
            for (i in 0..10) { p.color = if (i >= 9) 0xFFFF3B30.toInt() else 0xFFD6D0E0.toInt(); c.drawText("$i", x0 + (x1 - x0) * i / 10f, h * 0.36f, p) }
            segs(c, p, path, x0, h * 0.44f, x1 - x0, h * 0.34f, v, 20)
            p.style = Paint.Style.FILL; p.textAlign = Paint.Align.LEFT; p.textSize = dp(14).toFloat(); p.color = skin.accent
            c.drawText("RAM", dp(36).toFloat(), h * 0.76f, p)
            p.textSize = dp(26).toFloat(); p.color = Color.WHITE; c.drawText("${(stat.ram * 100).toInt()}%", x1 + dp(18), h * 0.8f, p)
            p.textSize = dp(12).toFloat(); p.color = 0xFFD6D0E0.toInt()
            val tt = if (stat.temp >= 0f) "${stat.temp.toInt()}°C" else "N/D"
            c.drawText("DISCO ${(stat.disk * 100).toInt()}%   TEMP $tt   ↓ ${"%.1f".format(stat.mbps)} Mbps", x1 + dp(100), h * 0.72f, p)
            if (Math.abs(v - stat.ram) > 0.004f) postInvalidateOnAnimation()
        }
    }
 
    // ---------- piezas de interfaz ----------
    private fun dark(r: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat(); setColor(0xB0080810.toInt()); setStroke(dp(1), 0x22FFFFFF)
    }
 
    // estilos de tarjeta: 0 cristal, 1 sólido, 2 neón
    private fun glass(focused: Boolean, radius: Int): GradientDrawable {
        val a = skin.accent
        val fill: IntArray; val sw: Int; val sc: Int
        when (prefs.getInt("cstyle", 0) % 3) {
            1 -> {
                fill = if (focused) intArrayOf(0xFF2E2B45.toInt(), 0xFF2E2B45.toInt()) else intArrayOf(0xFF1A1826.toInt(), 0xFF1A1826.toInt())
                sw = if (focused) 4 else 1; sc = if (focused) a else 0x44FFFFFF
            }
            2 -> {
                fill = if (focused) intArrayOf(withAlpha(a, 0x55), withAlpha(a, 0x22)) else intArrayOf(0x55000000, 0x55000000)
                sw = if (focused) 4 else 2; sc = if (focused) a else withAlpha(a, 0x99)
            }
            else -> {
                fill = if (focused) intArrayOf(withAlpha(a, 0xB0), withAlpha(a, 0x50)) else intArrayOf(0xD014121C.toInt(), 0xD014121C.toInt())
                sw = if (focused) 3 else 1; sc = if (focused) a else 0x44FFFFFF
            }
        }
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, fill).apply {
            cornerRadius = dp(radius).toFloat(); setStroke(dp(sw), sc)
        }
    }
 
    private fun focusable(v: View, radius: Int, onClick: () -> Unit, onLong: (() -> Unit)? = null, pop: View? = null, popScale: Float = 1.18f): View {
        v.isFocusable = true; v.isClickable = true; v.clipToOutline = true
        fun paint(f: Boolean) {
            v.background = glass(f, radius)
            pop?.animate()?.scaleX(if (f) popScale else 1f)?.scaleY(if (f) popScale else 1f)?.setDuration(180)?.start()
        }
        paint(false)
        v.setOnFocusChangeListener { _, f -> paint(f); if (f) sfx(0) }
        v.setOnClickListener { sfx(1); onClick() }
        if (onLong != null) v.setOnLongClickListener { onLong(); true }
        return v
    }
 
    private fun tile(label: String, icon: Drawable?, onClick: () -> Unit, onLong: () -> Unit): View {
        val s = scale
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        var iv: ImageView? = null
        if (icon != null) {
            iv = ImageView(this).apply { setImageDrawable(icon) }
            v.addView(iv, LinearLayout.LayoutParams(dp((54 * s).toInt()), dp((54 * s).toInt())).apply { bottomMargin = dp(8) })
        }
        v.addView(TextView(this).apply {
            text = label; textSize = 14f * s; setTextColor(Color.WHITE); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        })
        return focusable(v, 22, onClick, onLong, iv, 1.15f)
    }
 
    private fun wideTile(ri: ResolveInfo): View {
        val pm = packageManager; val pkg = ri.activityInfo.packageName; val label = ri.loadLabel(pm).toString()
        val banner = try { ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm) } catch (e: Throwable) { null }
        if (banner == null) return tile(label, ri.loadIcon(pm), { goPkg(pkg) }, { appMenu(pkg, label) })
        val f = FrameLayout(this).apply { setPadding(dp(3), dp(3), dp(3), dp(3)) }
        val img = ImageView(this).apply { setImageDrawable(banner); scaleType = ImageView.ScaleType.CENTER_CROP }
        f.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
        return focusable(f, 22, { goPkg(pkg) }, { appMenu(pkg, label) }, img, 1.08f)
    }
 
    private fun appTile(ri: ResolveInfo): View {
        val pkg = ri.activityInfo.packageName; val label = ri.loadLabel(packageManager).toString()
        return tile(label, ri.loadIcon(packageManager), { goPkg(pkg) }, { appMenu(pkg, label) })
    }
 
    private fun chip(label: String, onClick: () -> Unit): View {
        val t = TextView(this).apply {
            text = label; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(16), dp(9), dp(16), dp(9))
        }
        return focusable(t, 30, onClick)
    }
 
    private fun shelf(title: String, content: View): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(8)); background = dark(26)
        }
        box.addView(TextView(this).apply {
            text = title.uppercase(); textSize = 13f; letterSpacing = 0.25f
            setTextColor(skin.accent); setShadowLayer(4f, 0f, 1f, Color.BLACK); setPadding(dp(4), 0, 0, 0)
        })
        box.addView(content)
        box.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(14) }
        return box
    }
 
    private fun hrow(content: View): HorizontalScrollView = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false; clipToPadding = false
        setPadding(dp(4), dp(8), dp(4), dp(8)); addView(content)
    }
 
    // ---------- pantalla principal ----------
    private fun decodeThumb(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 480) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Throwable) { null }
 
    private fun thumbRow(ws: List<Wall>): View {
        val r = LinearLayout(this)
        ws.take(14).forEachIndexed { i, w ->
            val f = FrameLayout(this).apply { setPadding(dp(3), dp(3), dp(3), dp(3)) }
            val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            f.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
            val t = w.title
            if (t != null) f.addView(TextView(this).apply {
                text = t; textSize = 11f; setTextColor(Color.WHITE); maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(8), dp(3), dp(8), dp(3)); setBackgroundColor(0xAA000000.toInt())
            }, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
            Thread { val b = decodeThumb(w.file); runOnUiThread { if (b != null) img.setImageBitmap(b) } }.start()
            r.addView(focusable(f, 18, { prefs.edit().putInt("wall", i).apply(); loadWall(w, true) }, null, img, 1.08f),
                LinearLayout.LayoutParams(dp(200), dp(112)).apply { rightMargin = dp(12) })
        }
        return r
    }
 
    private fun mediaCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
            background = dark(26); minimumWidth = dp(280); visibility = View.GONE
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0x33FFFFFF) }
        }
        top.addView(art, LinearLayout.LayoutParams(dp(54), dp(54)))
        val txt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        val ti = TextView(this).apply {
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = dp(190)
        }
        val ar = TextView(this).apply {
            textSize = 13f; setTextColor(0xFFD6D0E0.toInt()); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = dp(190)
        }
        txt.addView(ti); txt.addView(ar)
        top.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f))
        card.addView(top)
        val ctl = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }
        fun g() = LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6); rightMargin = dp(6) }
        ctl.addView(chip("|<") { ctrl?.transportControls?.skipToPrevious() }, g())
        val pl = chip("||") { togglePlay() }
        ctl.addView(pl, g())
        ctl.addView(chip(">|") { ctrl?.transportControls?.skipToNext() }, g())
        card.addView(ctl)
        mediaBox = card; mediaCtl = ctl; mediaArt = art; mediaTitle = ti; mediaArtist = ar; mediaPlay = pl as TextView
        return card
    }
 
    private fun togglePlay() {
        val c = ctrl ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }
 
    private fun refreshMedia() {
        val box = mediaBox ?: return
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = msm.getActiveSessions(ComponentName(this, MediaListener::class.java))
            val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
            ctrl = c
            if (c == null) { box.visibility = View.GONE; return }
            val md = c.metadata
            val appLabel = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(c.packageName, 0)).toString() } catch (e: Exception) { c.packageName }
            mediaTitle?.text = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: appLabel
            mediaArtist?.text = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: appLabel
            val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            if (art != null) mediaArt?.setImageBitmap(art)
            else try { mediaArt?.setImageDrawable(packageManager.getApplicationIcon(c.packageName)) } catch (e: Exception) { }
            mediaPlay?.text = if (c.playbackState?.state == PlaybackState.STATE_PLAYING) "||" else ">"
            mediaCtl?.visibility = View.VISIBLE
            box.visibility = View.VISIBLE
        } catch (e: SecurityException) {
            mediaTitle?.text = "Música: falta permiso"; mediaArtist?.text = "Activa el acceso a notificaciones"
            mediaArt?.setImageDrawable(null); mediaCtl?.visibility = View.GONE; box.visibility = View.VISIBLE
        } catch (e: Throwable) { box.visibility = View.GONE }
    }
 
    private fun build() {
        val hidden = getSet("hidden"); val favs = getSet("fav")
        val visible = apps().filter { it.activityInfo.packageName !in hidden }
        val favApps = visible.filter { it.activityInfo.packageName in favs }
        val restApps = visible.filter { it.activityInfo.packageName !in favs }
        val s = scale
        val tileW = dp((168 * s).toInt()); val tileH = dp((124 * s).toInt())
        ramG = null; diskG = null; netG = null; tempG = null; statusRow = null; weatherView = null
        mediaBox = null; heroBox = null; clusterV = null; segV = null; sportV = null
        sysInfo = null; wxBig = null; wxDesc2 = null; wxDetail = null; wxTom = null; indicators.clear()
 
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(skin.top, skin.bottom))
        }
        wallLayer = FrameLayout(this)
        wallBlur = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        wallView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        wallLayer!!.addView(wallBlur, FrameLayout.LayoutParams(MATCH, MATCH))
        wallLayer!!.addView(wallView, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(wallLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x55000000, 0x11000000, 0xE6000000.toInt()))
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        val ws = walls()
        if (wallBmp != null) applyWall(false)
        else if (ws.isNotEmpty()) loadWall(ws[prefs.getInt("wall", 0).coerceIn(0, ws.size - 1)], false)
 
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
 
        // barra superior: botones a la izquierda, fecha, clima y hora a la derecha
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(36), dp(6), dp(36), dp(6)); setBackgroundColor(0x77000000)
        }
        val chips = LinearLayout(this)
        fun gap() = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(10) }
        chips.addView(chip("Entrada") { inputPanel() }, gap())
        chips.addView(chip("Compartir") {
            val ok = listOf("com.homwee.multiscreenshare", "com.homwee.mmp.screenshare").firstOrNull { goPkg(it) }
            if (ok == null) toast("No disponible en esta TV")
        }, gap())
        chips.addView(chip("Asistente") { assistantPanel() }, gap())
        chips.addView(chip("Personalizar") { customPanel() }, gap())
        chips.addView(chip("Optimizar") { optimize() }, gap())
        bar.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(dp(4), dp(6), dp(4), dp(6)); addView(chips)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (flag("wg_weather")) {
            weatherView = TextView(this).apply { textSize = 17f; setTextColor(skin.accent); setShadowLayer(4f, 0f, 1f, Color.BLACK) }
            bar.addView(weatherView, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(16) })
        }
        bar.addView(TextClock(this).apply {
            format24Hour = "EEE d MMM"; format12Hour = "EEE d MMM"; textSize = 14f; setTextColor(0xFFE6E0F0.toInt())
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(14) })
        bar.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "h:mm a"; textSize = 26f; typeface = light()
            setTextColor(Color.WHITE); setShadowLayer(6f, 0f, 2f, Color.BLACK)
        })
        val bs = prefs.getInt("bar", 0) % 3
        if (bs >= 1) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            for (k in 0..6) {
                val iv = IndicatorView(this, k); indicators.add(iv)
                row.addView(iv, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(8) })
            }
            bar.addView(row, 1, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(10); rightMargin = dp(14) })
        }
        if (bs == 1) bar.addView(TextClock(this).apply {
            format24Hour = "ss"; format12Hour = "ss"; textSize = 15f; setTextColor(skin.accent)
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(4) })
        col.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        if (bs == 2) {
            sportV = SportBar(this)
            col.addView(sportV, LinearLayout.LayoutParams(MATCH, dp(48)).apply { })
            sportV!!.setBackgroundColor(0x77000000)
        }
 
        // portada: título de cartelera a la izquierda, widgets a la derecha
        val hero = FrameLayout(this).apply { minimumHeight = (resources.displayMetrics.heightPixels * 0.40f).toInt() }
        val hb = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(40), 0, dp(24), dp(14)); visibility = View.GONE
        }
        heroBox = hb
        heroProv = TextView(this).apply {
            textSize = 14f; letterSpacing = 0.25f; setTextColor(skin.accent); setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        heroTitle = TextView(this).apply {
            textSize = 38f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(Color.WHITE)
            maxLines = 2; setShadowLayer(12f, 0f, 3f, Color.BLACK)
        }
        val btn = chip("Ver") { captionAction() }
        heroBtn = btn as TextView
        hb.addView(heroProv); hb.addView(heroTitle, LinearLayout.LayoutParams(dp(460), WRAP))
        hb.addView(btn, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
        hero.addView(hb, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START))
 
        val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), dp(36), dp(10)) }
        if (flag("wg_gauges") || flag("wg_status")) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); background = dark(26) }
            if (flag("wg_gauges")) {
                val gs = prefs.getInt("gstyle", 0) % 4
                if (gs == 2) { clusterV = ClusterView(this); card.addView(clusterV, LinearLayout.LayoutParams(dp(300), dp(150))) }
                else if (gs == 3) { segV = SegView(this); card.addView(segV, LinearLayout.LayoutParams(dp(300), dp(124))) }
                else {
                    val g = LinearLayout(this)
                    ramG = gauge("RAM"); diskG = gauge("DISCO"); tempG = gauge("TEMP"); netG = gauge("RED")
                    listOf(ramG!!, diskG!!, tempG!!, netG!!).forEach { g.addView(it, LinearLayout.LayoutParams(dp(78), dp(78))) }
                    card.addView(g)
                }
            }
            if (flag("wg_status")) {
                statusRow = LinearLayout(this).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(2), 0, 0) }
                card.addView(statusRow, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            side.addView(card, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
        }
        if (flag("wg_media")) side.addView(mediaCard(), LinearLayout.LayoutParams(MATCH, WRAP))
        if (side.childCount > 0) hero.addView(side, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL))
 
        // estantes
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(hero, LinearLayout.LayoutParams(MATCH, WRAP))
        val shelves = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(36), 0, dp(36), 0) }
        if (ws.isNotEmpty()) shelves.addView(shelf(if (prefs.getInt("wallmode", 0) == 1) "Cartelera" else "Mis fondos", hrow(thumbRow(ws))))
        if (flag("wg_info")) shelves.addView(shelf("Información", hrow(infoRow())))
        if (favApps.isEmpty()) {
            shelves.addView(shelf("Favoritas", TextView(this).apply {
                text = "Mantén pulsado OK sobre una app para fijarla aquí"
                textSize = 15f; setTextColor(0xFFE6E0F0.toInt()); setPadding(dp(8), dp(12), 0, dp(12))
            }))
        } else {
            val r = LinearLayout(this)
            favApps.forEach { r.addView(wideTile(it), LinearLayout.LayoutParams(dp((240 * s).toInt()), dp((135 * s).toInt())).apply { rightMargin = dp(14) }) }
            shelves.addView(shelf("Favoritas", hrow(r)))
        }
        val grid = GridLayout(this).apply { orientation = GridLayout.VERTICAL; rowCount = 2 }
        restApps.forEach {
            grid.addView(appTile(it), GridLayout.LayoutParams().apply { width = tileW; height = tileH; setMargins(dp(6), dp(6), dp(6), dp(6)) })
        }
        shelves.addView(shelf("Todas las apps", hrow(grid)))
        content.addView(shelves, LinearLayout.LayoutParams(MATCH, WRAP))
        if (!liteOn()) {
            content.alpha = 0f; content.translationY = dp(24).toFloat()
            content.animate().alpha(1f).translationY(0f).setDuration(450).start()
        }
 
        col.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false; setPadding(0, 0, 0, dp(56)); addView(content)
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))
 
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        val fx = seasonFx()
        if (fx.isNotEmpty()) root.addView(SeasonView(this, fx), FrameLayout.LayoutParams(MATCH, MATCH))
        if (!splashShown && flag("splash")) { splashShown = true; root.addView(splashView(), FrameLayout.LayoutParams(MATCH, MATCH)) }
        sfxReady = false; handler.postDelayed({ sfxReady = true }, 900)
        setContentView(root)
        chips.getChildAt(0)?.requestFocus()
        updateCaption()
        refreshStats()
        if (flag("wg_weather")) { lastWx = 0L; fetchWeather() }
    }
 
    private fun infoRow(): View {
        val r = LinearLayout(this)
        fun card(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)); background = dark(24) }
        fun tv(sz: Float, col: Int): TextView = TextView(this).apply { textSize = sz; setTextColor(col); text = "—" }
        val w = card()
        w.addView(tv(12f, skin.accent).apply { text = "CLIMA AFUERA"; letterSpacing = 0.2f })
        wxBig = tv(44f, Color.WHITE).apply { typeface = light() }
        wxDesc2 = tv(16f, Color.WHITE); wxDetail = tv(13f, 0xFFD6D0E0.toInt()); wxTom = tv(13f, 0xFFD6D0E0.toInt())
        w.addView(wxBig); w.addView(wxDesc2); w.addView(wxDetail); w.addView(wxTom)
        r.addView(w, LinearLayout.LayoutParams(dp(330), WRAP).apply { rightMargin = dp(14) })
        val sy = card()
        sy.addView(tv(12f, skin.accent).apply { text = "SISTEMA DE LA TV"; letterSpacing = 0.2f })
        sysInfo = tv(14f, Color.WHITE).apply { setPadding(0, dp(6), 0, dp(8)) }
        sy.addView(sysInfo)
        sy.addView(chip("Optimizar ahora") { optimize() })
        r.addView(sy, LinearLayout.LayoutParams(dp(300), WRAP).apply { rightMargin = dp(14) })
        return r
    }
 
    // libera RAM de apps en segundo plano sin tocar la música ni los servicios del sistema
    private fun optimize() {
        toast("Optimizando…")
        Thread {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            fun avail(): Long { val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi); return mi.availMem }
            val before = avail()
            val keep = mutableSetOf(packageName, "android", "com.google.android.gms", "com.google.android.gsf",
                "com.google.android.tvlauncher", "com.google.android.katniss", "com.google.android.apps.mediashell",
                "com.android.systemui", "com.android.vending", "com.google.android.tts", "com.google.android.inputmethod.latin")
            try {
                (getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager)
                    .getActiveSessions(ComponentName(this, MediaListener::class.java)).forEach { keep.add(it.packageName) }
            } catch (e: Throwable) { }
            var n = 0
            for (ai in packageManager.getInstalledApplications(0)) {
                val pkg = ai.packageName
                if (pkg in keep || pkg.startsWith("com.mediatek") || pkg.startsWith("com.homwee") || pkg.startsWith("com.android.providers")
                    || pkg.startsWith("com.android.bluetooth") || pkg.startsWith("com.android.settings") || pkg.startsWith("com.android.tv")) continue
                try { am.killBackgroundProcesses(pkg); n++ } catch (e: Throwable) { }
            }
            Thread.sleep(1000)
            val freed = ((avail() - before) / 1048576L).coerceAtLeast(0L)
            runOnUiThread { toast("Listo: $n apps revisadas, ~$freed MB liberados. La música sigue sonando."); refreshStats() }
        }.start()
    }
 
    private fun liteOn(): Boolean = prefs.getBoolean("lite", false)
 
    private fun toggleLite() {
        val on = !liteOn(); prefs.edit().putBoolean("lite", on).apply()
        try {
            val sc = if (on) "0.5" else "1.0"
            for (k in listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale"))
                Settings.Global.putString(contentResolver, k, sc)
        } catch (e: SecurityException) { }
        build(); customPanel()
    }
 
    // ---------- temporadas ----------
    private fun seasonFx(): Set<String> {
        if (liteOn()) return emptySet()
        return when (prefs.getInt("season", 0) % 6) {
            1 -> emptySet()
            2 -> setOf("cobweb", "pumpkin")
            3 -> setOf("petals")
            4 -> setOf("snow", "lights")
            5 -> setOf("hearts")
            else -> {
                val c = Calendar.getInstance(); val m = c.get(Calendar.MONTH) + 1; val d = c.get(Calendar.DAY_OF_MONTH)
                val out = mutableSetOf<String>()
                if ((m == 10 && d >= 15) || (m == 11 && d <= 1)) { out.add("cobweb"); out.add("pumpkin") }
                if ((m == 10 && d >= 28) || (m == 11 && d <= 2)) out.add("petals")
                if (m == 12 || (m == 1 && d <= 6)) { out.add("snow"); out.add("lights") }
                if (m == 2 && d in 7..14) out.add("hearts")
                out
            }
        }
    }
 
    private inner class SeasonView(ctx: Context, val fx: Set<String>) : View(ctx) {
        private val n = 26
        private val px = FloatArray(n); private val py = FloatArray(n); private val pv = FloatArray(n)
        private val ps = FloatArray(n); private val pp = FloatArray(n)
        private var last = 0L
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val oval = RectF()
 
        init {
            val rnd = java.util.Random(7)
            for (i in 0 until n) {
                px[i] = rnd.nextFloat(); py[i] = rnd.nextFloat(); pv[i] = 0.05f + rnd.nextFloat() * 0.07f
                ps[i] = 0.6f + rnd.nextFloat() * 0.8f; pp[i] = rnd.nextFloat() * 6.28f
            }
        }
 
        private fun heart(c: Canvas, x: Float, y: Float, s: Float) {
            paint.color = 0xAAFF5C8A.toInt()
            path.reset(); path.moveTo(x, y + s * 0.35f)
            path.cubicTo(x - s, y - s * 0.3f, x - s * 0.5f, y - s, x, y - s * 0.35f)
            path.cubicTo(x + s * 0.5f, y - s, x + s, y - s * 0.3f, x, y + s * 0.35f)
            c.drawPath(path, paint)
        }
 
        private fun web(c: Canvas, size: Float) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1).toFloat(); paint.color = 0x88FFFFFF.toInt()
            val steps = 6
            for (i in 0..steps) {
                val a = Math.toRadians(90.0 * i / steps)
                c.drawLine(0f, 0f, (size * Math.cos(a)).toFloat(), (size * Math.sin(a)).toFloat(), paint)
            }
            for (r in 1..4) {
                val rr = size * r / 4.5f
                path.reset()
                for (i in 0..steps) {
                    val a = Math.toRadians(90.0 * i / steps)
                    val x = (rr * Math.cos(a)).toFloat(); val y = (rr * Math.sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else {
                        val am = Math.toRadians(90.0 * (i - 0.5) / steps)
                        path.quadTo((rr * 0.86f * Math.cos(am)).toFloat(), (rr * 0.86f * Math.sin(am)).toFloat(), x, y)
                    }
                }
                c.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
        }
 
        private fun pumpkin(c: Canvas, cx: Float, cy: Float, s: Float) {
            paint.color = 0xFFFF7A1A.toInt(); oval.set(cx - s, cy - s * 0.8f, cx + s, cy + s * 0.8f); c.drawOval(oval, paint)
            paint.color = 0xFF3B7D23.toInt(); c.drawRect(cx - s * 0.12f, cy - s * 1.0f, cx + s * 0.12f, cy - s * 0.72f, paint)
            paint.color = 0xFF2A1405.toInt()
            path.reset(); path.moveTo(cx - s * 0.5f, cy - s * 0.1f); path.lineTo(cx - s * 0.2f, cy - s * 0.1f); path.lineTo(cx - s * 0.35f, cy - s * 0.4f); path.close(); c.drawPath(path, paint)
            path.reset(); path.moveTo(cx + s * 0.5f, cy - s * 0.1f); path.lineTo(cx + s * 0.2f, cy - s * 0.1f); path.lineTo(cx + s * 0.35f, cy - s * 0.4f); path.close(); c.drawPath(path, paint)
            path.reset(); path.moveTo(cx - s * 0.5f, cy + s * 0.2f); path.lineTo(cx - s * 0.25f, cy + s * 0.5f)
            path.lineTo(cx - s * 0.1f, cy + s * 0.25f); path.lineTo(cx + s * 0.1f, cy + s * 0.5f); path.lineTo(cx + s * 0.25f, cy + s * 0.25f)
            path.lineTo(cx + s * 0.5f, cy + s * 0.2f); path.lineTo(cx + s * 0.25f, cy + s * 0.65f); path.lineTo(cx - s * 0.25f, cy + s * 0.65f); path.close()
            c.drawPath(path, paint)
        }
 
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val now = System.nanoTime()
            val dt = if (last == 0L) 0.04f else ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
            val t = now / 1e9
            val up = "hearts" in fx
            if ("snow" in fx || "petals" in fx || up) {
                for (i in 0 until n) {
                    py[i] += (if (up) -pv[i] else pv[i]) * dt
                    if (py[i] > 1.05f) py[i] = -0.05f
                    if (py[i] < -0.05f) py[i] = 1.05f
                    val x = px[i] * w + Math.sin(t * 0.8 + pp[i]).toFloat() * dp(14)
                    val y = py[i] * h
                    val sz = dp(6) * ps[i]
                    if ("snow" in fx) { paint.color = 0xCCFFFFFF.toInt(); c.drawCircle(x, y, sz * 0.6f, paint) }
                    else if ("petals" in fx) {
                        paint.color = if (i % 2 == 0) 0xDDFF9F1C.toInt() else 0xDDFFC107.toInt()
                        c.save(); c.rotate((t * 40 + pp[i] * 50).toFloat(), x, y)
                        oval.set(x - sz, y - sz * 0.5f, x + sz, y + sz * 0.5f); c.drawOval(oval, paint); c.restore()
                    } else heart(c, x, y, sz * 1.6f)
                }
            }
            if ("cobweb" in fx) {
                web(c, dp(130).toFloat())
                c.save(); c.scale(-1f, 1f); c.translate(-w, 0f); web(c, dp(130).toFloat()); c.restore()
                val sx = dp(170).toFloat(); val sy = dp(54) + Math.sin(t).toFloat() * dp(12)
                paint.color = 0xAAFFFFFF.toInt(); paint.strokeWidth = dp(1).toFloat(); paint.style = Paint.Style.STROKE
                c.drawLine(sx, 0f, sx, sy, paint)
                for (k in -2..2) { if (k != 0) { c.drawLine(sx, sy, sx + k * dp(7), sy - dp(5) + Math.abs(k) * dp(3), paint); c.drawLine(sx, sy, sx + k * dp(7), sy + dp(6), paint) } }
                paint.style = Paint.Style.FILL; paint.color = 0xFF15121C.toInt(); c.drawCircle(sx, sy, dp(6).toFloat(), paint)
            }
            if ("pumpkin" in fx) {
                pumpkin(c, dp(46).toFloat(), h - dp(26), dp(22).toFloat())
                pumpkin(c, w - dp(46), h - dp(26), dp(22).toFloat())
            }
            if ("lights" in fx) {
                val cols = intArrayOf(0xFFFF4D4D.toInt(), 0xFF4DFF88.toInt(), 0xFFFFD84D.toInt(), 0xFF4DA6FF.toInt())
                val seg = dp(120).toFloat(); var x0 = 0f; var i = 0
                while (x0 < w) {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2).toFloat(); paint.color = 0xFF1B3B1B.toInt()
                    path.reset(); path.moveTo(x0, dp(2).toFloat()); path.quadTo(x0 + seg / 2, dp(26).toFloat(), x0 + seg, dp(2).toFloat())
                    c.drawPath(path, paint)
                    paint.style = Paint.Style.FILL
                    paint.color = cols[(i + (t * 1.5).toInt()) % 4]
                    c.drawCircle(x0 + seg / 2, dp(19).toFloat(), dp(5).toFloat(), paint)
                    x0 += seg; i++
                }
            }
            postInvalidateDelayed(40)
        }
    }
 
    private fun appMenu(pkg: String, label: String) {
        val isFav = pkg in getSet("fav")
        val opts = arrayOf(if (isFav) "Quitar de favoritas" else "Fijar en favoritas", "Ocultar app", "Cancelar")
        AlertDialog.Builder(this).setTitle(label).setItems(opts) { _, w ->
            when (w) {
                0 -> { putSet("fav", if (isFav) getSet("fav") - pkg else getSet("fav") + pkg); build() }
                1 -> { putSet("hidden", getSet("hidden") + pkg); build() }
            }
        }.show()
    }
 
    // ---------- paneles ----------
    private fun panel(title: String, items: List<Opt>, note: String? = null) {
        val d = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val wrap = FrameLayout(this).apply { setBackgroundColor(0xCC000000.toInt()) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(32), dp(24), dp(32), dp(26))
            background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(0xF0181622.toInt()); setStroke(dp(1), 0x33FFFFFF) }
        }
        box.addView(TextView(this).apply {
            text = title; textSize = 26f; typeface = light(); setTextColor(Color.WHITE); setPadding(dp(4), 0, 0, dp(8))
        })
        if (note != null) box.addView(TextView(this).apply {
            text = note; textSize = 14f; setTextColor(0xFFB0A9BC.toInt()); setPadding(dp(4), 0, dp(4), dp(8))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        var first: View? = null
        items.forEach { o ->
            val t = TextView(this).apply {
                text = o.label; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(22), dp(13), dp(22), dp(13))
            }
            val v = focusable(t, 22, { d.dismiss(); o.run() })
            if (first == null) first = v
            list.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(9) })
        }
        box.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; addView(list) })
        wrap.addView(box, FrameLayout.LayoutParams(dp(540), WRAP, Gravity.CENTER))
        d.setContentView(wrap)
        d.window?.setLayout(MATCH, MATCH)
        d.show()
        first?.requestFocus()
    }
 
    private fun labelOf(i: TvInputInfo): String =
        i.loadCustomLabel(this)?.toString()?.takeIf { it.isNotBlank() } ?: i.loadLabel(this)?.toString() ?: "Entrada"
 
    private fun passInputs(): List<TvInputInfo> = try {
        (getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager).tvInputList.filter { it.isPassthroughInput }.sortedBy { labelOf(it) }
    } catch (e: Exception) { emptyList() }
 
    private fun openTv() {
        if (!go(Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI), true)) {
            if (!goPkg("com.mediatek.wwtv.tvcenter")) toast("No se pudo abrir la TV")
        }
    }
 
    private fun openInput(i: TvInputInfo) { go(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(i.id))) }
 
    private fun inputPanel() {
        val inputs = passInputs()
        val items = mutableListOf<Opt>()
        items.add(Opt("TV en vivo") { openTv() })
        inputs.forEach { inp -> items.add(Opt(labelOf(inp)) { openInput(inp) }) }
        panel("Entrada", items, if (inputs.isEmpty()) "No se detectaron entradas HDMI/AV. Usa el botón de entrada del control." else null)
    }
 
    // ---------- asistente (Claude) ----------
    private fun claudeKey(): String? =
        File(getExternalFilesDir(null), "claude.txt").takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
 
    private fun gptConf(): Pair<String, String>? {
        val f = File(getExternalFilesDir(null), "openai.txt")
        if (!f.exists()) return null
        val lines = f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        return Pair(lines[0], if (lines.size > 1) lines[1] else "gpt-4o-mini")
    }
 
    private fun assistantPanel(note: String? = null) {
        val gpt = prefs.getInt("ai", 0) == 1
        val ready = if (gpt) gptConf() != null else claudeKey() != null
        val items = listOf(
            Opt("Hablar") { listen() },
            Opt("Escribir") { typeQuestion() },
            Opt("Motor: ${if (gpt) "ChatGPT" else "Claude"}") { prefs.edit().putInt("ai", if (gpt) 0 else 1).apply(); assistantPanel() },
            Opt("Cerrar") { }
        )
        panel("Asistente", items, note ?: if (!ready) "Falta ${if (gpt) "openai.txt" else "claude.txt"} con tu clave (ver guía)."
            else "Pídeme abrir una app, cambiar de entrada o pregúntame lo que quieras.")
    }
 
    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora")
        try { startActivityForResult(i, 77) } catch (e: Exception) { toast("Sin reconocimiento de voz; usa Escribir") }
    }
 
    private fun typeQuestion() {
        val et = EditText(this).apply { hint = "¿Qué quieres hacer?"; isSingleLine = true }
        AlertDialog.Builder(this).setTitle("Asistente").setView(et)
            .setPositiveButton("Enviar") { _, _ -> val q = et.text.toString().trim(); if (q.isNotEmpty()) ask(q) }
            .setNegativeButton("Cancelar", null).show()
    }
 
    private fun ask(q: String) {
        val gpt = prefs.getInt("ai", 0) == 1
        val key: String? = if (gpt) gptConf()?.first else claudeKey()
        if (key == null) { assistantPanel(); return }
        val model = gptConf()?.second ?: "gpt-4o-mini"
        toast("Pensando…")
        val appsTxt = apps().joinToString(", ") { it.loadLabel(packageManager).toString() }
        val inTxt = passInputs().joinToString(", ") { labelOf(it) }
        val sys = "Eres el asistente de una TV Android. Responde SOLO con JSON válido: " +
            "{\"say\":\"respuesta breve en español\",\"action\":null} o con una acción: " +
            "{\"say\":\"...\",\"action\":{\"type\":\"open_app\" | \"input\" | \"tv\" | \"next_wall\",\"name\":\"nombre exacto\"}}. " +
            "Apps instaladas: $appsTxt. Entradas: $inTxt."
        Thread {
            val reply: String = try {
                val c = URL(if (gpt) "https://api.openai.com/v1/chat/completions" else "https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10000; c.readTimeout = 30000
                c.setRequestProperty("content-type", "application/json")
                val body: JSONObject
                if (gpt) {
                    c.setRequestProperty("Authorization", "Bearer $key")
                    body = JSONObject().put("model", model).put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", sys))
                        .put(JSONObject().put("role", "user").put("content", q)))
                } else {
                    c.setRequestProperty("x-api-key", key)
                    c.setRequestProperty("anthropic-version", "2023-06-01")
                    body = JSONObject().put("model", "claude-haiku-5-5").put("max_tokens", 300).put("system", sys)
                        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", q)))
                }
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val ok = c.responseCode in 200..299
                val txt = (if (ok) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
                if (!ok) "Error ${c.responseCode}: revisa tu clave, saldo o modelo"
                else if (gpt) JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                else JSONObject(txt).getJSONArray("content").getJSONObject(0).getString("text")
            } catch (e: Exception) { "Sin conexión con el asistente" }
            runOnUiThread { handleReply(reply) }
        }.start()
    }
 
    private fun handleReply(raw: String) {
        var say = raw; var type = ""; var name = ""
        try {
            val s = raw.indexOf('{'); val e = raw.lastIndexOf('}')
            if (s >= 0 && e > s) {
                val j = JSONObject(raw.substring(s, e + 1))
                say = j.optString("say", raw)
                val a = j.optJSONObject("action")
                if (a != null) { type = a.optString("type"); name = a.optString("name") }
            }
        } catch (ex: Exception) { }
        if (flag("tts")) tts?.speak(say, TextToSpeech.QUEUE_FLUSH, null, "asistente")
        if (type.isEmpty() || type == "null") { assistantPanel(say); return }
        toast(say)
        when (type) {
            "open_app" -> {
                val pm = packageManager
                val m = apps().firstOrNull { val l = it.loadLabel(pm).toString(); l.equals(name, true) || l.contains(name, true) }
                if (m != null) goPkg(m.activityInfo.packageName) else toast("No encontré $name")
            }
            "input" -> {
                val m = passInputs().firstOrNull { labelOf(it).contains(name, true) }
                if (m != null) openInput(m) else toast("No encontré $name")
            }
            "tv" -> openTv()
            "next_wall" -> nextWall()
        }
    }
 
    // ---------- personalización ----------
    private fun onOff(b: Boolean): String = if (b) "Sí" else "No"
 
    private fun toggle(key: String, label: String): Opt =
        Opt("$label: ${onOff(flag(key))}") { prefs.edit().putBoolean(key, !flag(key)).apply(); build(); widgetsPanel() }
 
    private fun widgetsPanel() {
        panel("Widgets", listOf(
            toggle("wg_gauges", "Medidores (RAM, disco, red)"),
            toggle("wg_status", "Estado de entradas y Bluetooth"),
            toggle("wg_weather", "Clima"),
            toggle("wg_media", "Reproductor de música"),
            toggle("wg_info", "Información (clima y sistema)"),
            Opt("Volver") { customPanel() }
        ))
    }
 
    private fun wallPanel() {
        val mode = prefs.getInt("wallmode", 0)
        val fit = prefs.getInt("fit", 0)
        panel("Fondos", listOf(
            Opt("Origen: ${if (mode == 0) "Mis imágenes" else "Cartelera de películas"}") {
                prefs.edit().putInt("wallmode", 1 - mode).putInt("wall", -1).apply()
                wallBmp = null
                if (mode == 0) refreshCartelera() else nextWall()
                wallPanel()
            },
            Opt("Siguiente fondo") { nextWall(); wallPanel() },
            Opt("Ajuste: ${if (fit == 0) "Llenar pantalla" else "Ajustar completa"}") {
                prefs.edit().putInt("fit", 1 - fit).apply(); applyWall(false); wallPanel()
            },
            Opt("Cambio automático: ${autoNames[prefs.getInt("auto", 0) % autoSecs.size]}") {
                prefs.edit().putInt("auto", prefs.getInt("auto", 0) + 1).apply(); scheduleWall(); wallPanel()
            },
            Opt("Actualizar cartelera") { refreshCartelera() },
            Opt("Volver") { customPanel() }
        ), "Mis imágenes: ${userFiles().size} en la carpeta. Cartelera: datos de TMDB (no certificado por TMDB).")
    }
 
    // ---------- sonidos ----------
    private fun sndMode(): Int = prefs.getInt("snd", 1) % 4
 
    private fun synth(f1: Double, f2: Double, ms: Int, wave: Int): ShortArray {
        val rate = 22050; val n = rate * ms / 1000
        val out = ShortArray(n); var ph = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            ph += 2 * Math.PI * (f1 + (f2 - f1) * t) / rate
            var v = Math.sin(ph)
            if (wave == 2) v = if (v >= 0) 0.6 else -0.6
            else if (wave == 1) v = (v + 0.4 * Math.sin(ph * 2)) / 1.4
            val env = Math.pow(1.0 - t, 1.6) * minOf(1.0, i / 80.0)
            out[i] = (v * env * 0.6 * 32767).toInt().toShort()
        }
        return out
    }
 
    private fun writeWav(f: File, pcm: ShortArray, rate: Int) {
        val bb = java.nio.ByteBuffer.allocate(44 + pcm.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + pcm.size * 2); bb.put("WAVE".toByteArray()); bb.put("fmt ".toByteArray())
        bb.putInt(16); bb.putShort(1.toShort()); bb.putShort(1.toShort()); bb.putInt(rate); bb.putInt(rate * 2)
        bb.putShort(2.toShort()); bb.putShort(16.toShort()); bb.put("data".toByteArray()); bb.putInt(pcm.size * 2)
        for (x in pcm) bb.putShort(x)
        f.writeBytes(bb.array())
    }
 
    private fun loadSounds() {
        try {
            sp?.release()
            val s2 = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
            sp = s2; sndIds.clear()
            val wave = (sndMode() - 1).coerceAtLeast(0)
            val files = listOf(
                Pair("move", synth(900.0, 1150.0, 35, wave)),
                Pair("select", synth(650.0, 1300.0, 90, wave)),
                Pair("intro", synth(523.0, 523.0, 130, wave) + synth(659.0, 659.0, 130, wave) + synth(784.0, 784.0, 380, wave))
            )
            for (pr in files) {
                val f = File(cacheDir, "sfx${wave}_${pr.first}.wav")
                if (!f.exists()) writeWav(f, pr.second, 22050)
                sndIds.add(s2.load(f.path, 1))
            }
        } catch (e: Throwable) { }
    }
 
    // 0 mover, 1 seleccionar, 2 intro
    private fun sfx(kind: Int, force: Boolean = false) {
        if ((!sfxReady && !force) || sndMode() == 0) return
        val now = System.currentTimeMillis()
        if (kind == 0 && now - lastSfx < 45) return
        lastSfx = now
        val id = sndIds.getOrNull(kind) ?: return
        val v = floatArrayOf(0.35f, 0.65f, 1f)[prefs.getInt("sndvol", 1) % 3]
        sp?.play(id, v, v, 1, 0, 1f)
    }
 
    // ---------- bienvenida al encender ----------
    private fun splashView(): View {
        val f = FrameLayout(this).apply { setBackgroundColor(0xFF05050A.toInt()) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val logo = File(getExternalFilesDir(null), "splash.png")
        val bmp = if (logo.exists()) decodeThumb(logo) else null
        if (bmp != null) {
            col.addView(ImageView(this).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER },
                LinearLayout.LayoutParams(dp(260), dp(160)))
        } else {
            val ring = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0); setStroke(dp(3), skin.accent) }
                scaleX = 0.5f; scaleY = 0.5f
            }
            col.addView(ring, LinearLayout.LayoutParams(dp(84), dp(84)))
            ring.animate().scaleX(1f).scaleY(1f).setDuration(1400).start()
        }
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hi = if (h < 12) "Buenos días" else if (h < 19) "Buenas tardes" else "Buenas noches"
        val nm = File(getExternalFilesDir(null), "nombre.txt").takeIf { it.exists() }?.readText()?.trim().orEmpty()
        col.addView(TextView(this).apply {
            text = if (nm.isEmpty()) hi else "$hi, $nm"; textSize = 40f; typeface = light()
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(22), 0, dp(6))
        })
        col.addView(TextView(this).apply { text = "MI LAUNCHER"; textSize = 13f; letterSpacing = 0.5f; setTextColor(skin.accent) })
        f.addView(col, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        col.alpha = 0f
        col.animate().alpha(1f).setDuration(900).start()
        handler.postDelayed({ sfx(2, true) }, 400)
        handler.postDelayed({
            f.animate().alpha(0f).setDuration(700).withEndAction { (f.parent as? ViewGroup)?.removeView(f) }.start()
        }, 2600)
        return f
    }
 
    private fun dnsOn(): Boolean = try { Settings.Global.getString(contentResolver, "private_dns_mode") == "hostname" } catch (e: Throwable) { false }
 
    private fun toggleDns() {
        try {
            if (dnsOn()) {
                Settings.Global.putString(contentResolver, "private_dns_mode", "off")
                toast("DNS con bloqueo: apagado")
            } else {
                Settings.Global.putString(contentResolver, "private_dns_specifier", "dns.adguard-dns.com")
                Settings.Global.putString(contentResolver, "private_dns_mode", "hostname")
                toast("DNS con bloqueo: encendido")
            }
        } catch (e: SecurityException) {
            panel("Falta un permiso", listOf(Opt("Cerrar") { }),
                "Ejecuta una vez en tu PC:  adb shell pm grant com.alejandro.tvlauncher android.permission.WRITE_SECURE_SETTINGS")
        }
    }
 
    private fun stylePanel() {
        panel("Estilo", listOf(
            Opt("Tema de color: ${skin.name}") { prefs.edit().putInt("theme", prefs.getInt("theme", 0) + 1).apply(); build(); stylePanel() },
            Opt("Tarjetas: ${cardNames[prefs.getInt("cstyle", 0) % 3]}") { prefs.edit().putInt("cstyle", prefs.getInt("cstyle", 0) + 1).apply(); build(); stylePanel() },
            Opt("Medidores: ${gNames[prefs.getInt("gstyle", 0) % 4]}") { prefs.edit().putInt("gstyle", prefs.getInt("gstyle", 0) + 1).apply(); build(); stylePanel() },
            Opt("Barra superior: ${barNames[prefs.getInt("bar", 0) % 3]}") { prefs.edit().putInt("bar", prefs.getInt("bar", 0) + 1).apply(); build(); stylePanel() },
            Opt("Temporada: ${seasonNames[prefs.getInt("season", 0) % 6]}") { prefs.edit().putInt("season", prefs.getInt("season", 0) + 1).apply(); build(); stylePanel() },
            Opt("Tamaño de iconos: ${sizeNames[prefs.getInt("size", 1) % 3]}") { prefs.edit().putInt("size", prefs.getInt("size", 1) + 1).apply(); build(); stylePanel() },
            Opt("Volver") { customPanel() }
        ))
    }
 
    private fun customPanel() {
        panel("Personalizar", listOf(
            Opt("Estilo…") { stylePanel() },
            Opt("Fondos…") { wallPanel() },
            Opt("Widgets…") { widgetsPanel() },
            Opt("Optimizar sistema ahora") { optimize() },
            Opt("Modo ligero: ${onOff(liteOn())}") { toggleLite() },
            Opt("Sonidos de navegación: ${sndNames[sndMode()]}") {
                prefs.edit().putInt("snd", (sndMode() + 1) % 4).apply(); loadSounds(); sfxReady = true; sfx(1); customPanel()
            },
            Opt("Volumen de sonidos: ${volNames[prefs.getInt("sndvol", 1) % 3]}") {
                prefs.edit().putInt("sndvol", prefs.getInt("sndvol", 1) + 1).apply(); sfx(1); customPanel()
            },
            toggle("splash", "Pantalla de bienvenida"),
            toggle("tts", "Voz del asistente"),
            Opt("Bloqueo de anuncios por DNS: ${onOff(dnsOn())}") { toggleDns() },
            Opt("Mostrar apps ocultas") { putSet("hidden", emptySet()); build() },
            Opt("Ajustes de la TV") { go(Intent(Settings.ACTION_SETTINGS)) }
        ))
    }
}
 
class MediaListener : android.service.notification.NotificationListenerService()
 
