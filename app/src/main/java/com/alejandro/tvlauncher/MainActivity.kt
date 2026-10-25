package com.alejandro.tvlauncher
 
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioAttributes
import android.media.AudioManager
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
import android.view.KeyEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.ViewGroup
import android.view.WindowManager
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
    private val gNames = arrayOf("Aguja", "Anillo", "Cluster", "Segmentos", "Orbital")
    private val fontNames = arrayOf("Moderna", "Tecnológica", "Consola", "Elegante", "Casual")
    private val clkNames = arrayOf("Delgado", "Neón", "Doble línea", "Tecnológico", "Minimal")
    private val wxsNames = arrayOf("Texto", "Icono", "Detalle", "Oculto")
    private val layoutNames = arrayOf("Cine", "Aurora Dock", "Consola")
    private val menuNames = arrayOf("Lista", "Juego")
    private var menuRefresh: (() -> Unit)? = null
    private val patCache = HashMap<Int, Bitmap>()
    private var orbV: OrbitalView? = null
    private var hudV: HudView? = null
    private var volView: VolumeView? = null
    private var wxIcon: WxIcon? = null
    private var mediaProg: ProgView? = null
    private var eqV: EqView? = null
    private var mediaSpin: ObjectAnimator? = null
    private var resumed = false
    private var pendingSplash = false
    private var wakeReceiver: BroadcastReceiver? = null
    private val netHist = FloatArray(60)
    private var sunrise = ""
    private var sunset = ""
    private var uvMax = -1.0
    private var aqi = -1
    private val mstyleNames = arrayOf("Tarjeta", "Vinilo", "Ecualizador", "Pastilla", "Portada")
    private var wxT: Int? = null
    private var wxFeels = 0
    private var wxHum = 0
    private var wxCode = 0
    private var wxDescS = ""
    private val volHide = Runnable { volView?.animate()?.alpha(0f)?.setDuration(300)?.start() }
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
    private val cardNames = arrayOf("Cristal", "Sólido", "Neón", "Carbono", "Puntos", "Diagonal", "Escaneo")
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
    private fun fontIdx(): Int = prefs.getInt("font", 0) % 5
    private fun disp(): Typeface = when (fontIdx()) {
        1 -> Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        2 -> Typeface.create("monospace", Typeface.BOLD)
        3 -> Typeface.create("serif", Typeface.NORMAL)
        4 -> Typeface.create("casual", Typeface.NORMAL)
        else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private fun body(): Typeface = when (fontIdx()) {
        1 -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        2 -> Typeface.create("monospace", Typeface.NORMAL)
        3 -> Typeface.create("serif", Typeface.NORMAL)
        4 -> Typeface.create("casual", Typeface.NORMAL)
        else -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun light(): Typeface = disp()
    private fun getSet(k: String): Set<String> = prefs.getStringSet(k, emptySet())!!.toSet()
    private fun putSet(k: String, s: Set<String>) = prefs.edit().putStringSet(k, s).apply()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun withAlpha(c: Int, a: Int): Int = (a shl 24) or (c and 0xFFFFFF)
 
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("p", 0)
        if (!prefs.contains("fav")) putSet("fav", knownFavs)
        getExternalFilesDir("fondos")
        wakeReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (flag("splash") && flag("splash_wake")) { if (resumed) replaySplash() else pendingSplash = true }
            }
        }
        try { registerReceiver(wakeReceiver, IntentFilter(Intent.ACTION_SCREEN_ON)) } catch (e: Throwable) { }
        loadSounds()
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) tts?.language = Locale("es", "MX") }
    }
 
    override fun onDestroy() {
        try { wakeReceiver?.let { unregisterReceiver(it) } } catch (e: Throwable) { }
        tts?.shutdown(); sp?.release(); super.onDestroy()
    }
 
    override fun onResume() {
        super.onResume()
        val s = apps().joinToString(",") { it.activityInfo.packageName }
        if (s != sig) { sig = s; build() }
        handler.removeCallbacks(tick); handler.post(tick)
        scheduleWall()
        resumed = true
        if (pendingSplash) { pendingSplash = false; replaySplash() }
        if (flag("wg_weather") && System.currentTimeMillis() - lastWx > 30 * 60_000L) fetchWeather()
    }
 
    override fun onPause() {
        super.onPause()
        resumed = false
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
        val key = readLinesRobust(keyFile).firstOrNull() ?: ""
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
                val root = JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,relative_humidity_2m,apparent_temperature,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max&forecast_days=2&timezone=auto"))
                val j = root.getJSONObject("current"); val d = root.getJSONObject("daily")
                val t = Math.round(j.getDouble("temperature_2m"))
                val desc = wxDesc(j.getInt("weather_code"))
                val top = "$t° · $desc"
                val detail = "Sensación ${Math.round(j.optDouble("apparent_temperature"))}° · Humedad ${j.optInt("relative_humidity_2m")}% · Viento ${Math.round(j.optDouble("wind_speed_10m"))} km/h"
                val tom = "Mañana: ${Math.round(d.getJSONArray("temperature_2m_min").getDouble(1))}°–${Math.round(d.getJSONArray("temperature_2m_max").getDouble(1))}° · Lluvia ${d.getJSONArray("precipitation_probability_max").optInt(1)}%"
                sunrise = d.getJSONArray("sunrise").optString(0).substringAfter('T', "")
                sunset = d.getJSONArray("sunset").optString(0).substringAfter('T', "")
                uvMax = d.getJSONArray("uv_index_max").optDouble(0, -1.0)
                try {
                    aqi = JSONObject(httpGet("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi")).getJSONObject("current").getInt("us_aqi")
                } catch (e: Exception) { }
                wxT = t.toInt(); wxDescS = desc; wxCode = j.getInt("weather_code")
                wxFeels = Math.round(j.optDouble("apparent_temperature")).toInt(); wxHum = j.optInt("relative_humidity_2m")
                runOnUiThread { renderWeather(); wxBig?.text = "$t°"; wxDesc2?.text = desc; wxDetail?.text = detail; wxTom?.text = tom }
            } catch (e: Exception) { lastWx = 0L }
        }.start()
    }
 
    private fun renderWeather() {
        val t = wxT ?: return
        weatherView?.text = when (prefs.getInt("wxs", 0) % 4) {
            1 -> "$t°"
            2 -> "$t° · Sens $wxFeels° · $wxHum%"
            else -> "$t° · $wxDescS"
        }
        wxIcon?.let { it.code = wxCode; it.invalidate() }
    }
 
    private inner class WxIcon(ctx: Context) : View(ctx) {
        var code = 0
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        override fun onDraw(c: Canvas) {
            val s = width.toFloat()
            p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = s * 0.07f
            if (code in 0..2) {
                val sx = if (code == 0) s / 2 else s * 0.36f; val r = s * (if (code == 0) 0.2f else 0.15f)
                p.style = Paint.Style.STROKE; p.color = skin.accent; c.drawCircle(sx, sx, r, p)
                for (i in 0 until 8) {
                    val a = Math.PI * i / 4
                    c.drawLine(sx + (r * 1.5f * Math.cos(a)).toFloat(), sx + (r * 1.5f * Math.sin(a)).toFloat(),
                        sx + (r * 2.0f * Math.cos(a)).toFloat(), sx + (r * 2.0f * Math.sin(a)).toFloat(), p)
                }
            }
            if (code >= 1) {
                p.style = Paint.Style.FILL; p.color = 0xFFE6E0F0.toInt()
                c.drawCircle(s * 0.4f, s * 0.62f, s * 0.17f, p); c.drawCircle(s * 0.6f, s * 0.57f, s * 0.21f, p)
                c.drawRect(s * 0.25f, s * 0.62f, s * 0.78f, s * 0.77f, p)
            }
            if (code in 51..67 || code in 80..82) {
                p.style = Paint.Style.STROKE; p.color = 0xFF4DD0E1.toInt()
                for (i in 0..2) c.drawLine(s * (0.38f + 0.14f * i), s * 0.82f, s * (0.34f + 0.14f * i), s * 0.95f, p)
            }
            if (code >= 95) {
                p.style = Paint.Style.FILL; p.color = 0xFFFFD84D.toInt()
                path.reset(); path.moveTo(s * 0.52f, s * 0.78f); path.lineTo(s * 0.42f, s * 0.92f); path.lineTo(s * 0.52f, s * 0.92f); path.lineTo(s * 0.46f, s * 1.0f)
                path.lineTo(s * 0.62f, s * 0.86f); path.lineTo(s * 0.52f, s * 0.86f); path.close(); c.drawPath(path, p)
            }
        }
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
            val ring = prefs.getInt("gstyle", 0) % 5 == 1
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
        System.arraycopy(netHist, 1, netHist, 0, netHist.size - 1); netHist[netHist.size - 1] = stat.mbps
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
        clusterV?.invalidate(); segV?.invalidate(); sportV?.invalidate(); orbV?.invalidate(); hudV?.invalidate()
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
 
    // ---------- widget orbital (tres círculos con anillo de progreso) ----------
    private inner class OrbitalView(ctx: Context, val dock: Boolean) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val rect = RectF()
        private var a = 0f; private var b = 0f; private var m = 0f
 
        private fun ring(c: Canvas, cx: Float, cy: Float, r: Float, v: Float) {
            p.style = Paint.Style.FILL; p.color = 0xF2050508.toInt(); c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = r * 0.07f; p.color = 0xFF22252B.toInt(); c.drawCircle(cx, cy, r * 0.93f, p)
            rect.set(cx - r * 0.93f, cy - r * 0.93f, cx + r * 0.93f, cy + r * 0.93f)
            p.strokeWidth = r * 0.05f; p.color = skin.accent
            c.drawArc(rect, -90f, 360f * v.coerceIn(0.02f, 1f), false, p)
        }
 
        private fun txt(c: Canvas, t: String, x: Float, y: Float, size: Float, col: Int, tf: Typeface) {
            tp.textSize = size; tp.color = col; tp.typeface = tf; c.drawText(t, x, y, tp)
        }
 
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val rs = h * 0.36f; val rc = h * 0.47f
            val left = if (dock) stat.disk else stat.ram
            val right = if (stat.temp >= 0f) stat.temp / 100f else stat.disk
            a = ease(a, left); b = ease(b, right); m = ease(m, stat.ram)
            val now = System.currentTimeMillis()
            ring(c, w * 0.17f, h / 2, rs, a); ring(c, w * 0.83f, h / 2, rs, b)
            ring(c, w / 2, h / 2, rc, if (dock) m else ((now / 1000) % 60) / 60f)
            val dim = 0xFFB0A9BC.toInt()
            txt(c, "${(left * 100).toInt()}%", w * 0.17f, h / 2 + rs * 0.12f, rs * 0.4f, Color.WHITE, Typeface.DEFAULT_BOLD)
            txt(c, if (dock) "DISCO" else "RAM", w * 0.17f, h / 2 + rs * 0.5f, rs * 0.2f, dim, body())
            txt(c, if (stat.temp >= 0f) "${stat.temp.toInt()}°" else "${(stat.disk * 100).toInt()}%", w * 0.83f, h / 2 + rs * 0.12f, rs * 0.4f, Color.WHITE, Typeface.DEFAULT_BOLD)
            txt(c, if (stat.temp >= 0f) "TEMP" else "DISCO", w * 0.83f, h / 2 + rs * 0.5f, rs * 0.2f, dim, body())
            if (dock) {
                txt(c, "${(stat.ram * 100).toInt()}%", w / 2, h / 2 + rc * 0.14f, rc * 0.5f, Color.WHITE, disp())
                txt(c, "RAM", w / 2, h / 2 + rc * 0.55f, rc * 0.18f, dim, body())
            } else {
                val f24 = android.text.format.DateFormat.is24HourFormat(context)
                val time = java.text.SimpleDateFormat(if (f24) "HH:mm" else "h:mm", Locale.getDefault()).format(java.util.Date(now))
                val date = java.text.SimpleDateFormat("EEE d MMM", Locale("es")).format(java.util.Date(now)).uppercase()
                txt(c, time, w / 2, h / 2 + rc * 0.2f, rc * 0.62f, Color.WHITE, disp())
                txt(c, date, w / 2, h / 2 + rc * 0.62f, rc * 0.17f, dim, body())
            }
            val moving = Math.abs(a - left) > 0.004f || Math.abs(b - right) > 0.004f || Math.abs(m - stat.ram) > 0.004f
            if (moving) postInvalidateOnAnimation() else if (!dock) postInvalidateDelayed(1000)
        }
    }
 
    // ---------- etiquetas rápidas tipo HUD: RAM 76% ----------
    private inner class HudView(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        override fun onDraw(c: Canvas) {
            val h = height.toFloat()
            val items = ArrayList<Pair<String, String>>()
            items.add(Pair("RAM", "${(stat.ram * 100).toInt()}%")); items.add(Pair("DISCO", "${(stat.disk * 100).toInt()}%"))
            if (stat.temp >= 0f) items.add(Pair("TEMP", "${stat.temp.toInt()}°"))
            p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
            val labS = dp(13).toFloat(); val valS = dp(22).toFloat(); val gap = dp(26).toFloat(); val mark = dp(16).toFloat()
            var total = 0f
            for (pr in items) {
                p.textSize = labS; val lw = p.measureText(pr.first); p.textSize = valS; val vw = p.measureText(pr.second)
                total += mark + lw + dp(8) + vw + gap
            }
            var x = width - total + gap
            for (pr in items) {
                p.style = Paint.Style.FILL; p.color = skin.accent
                path.reset(); path.moveTo(x + dp(5), h * 0.25f); path.lineTo(x + dp(12), h * 0.25f); path.lineTo(x + dp(7), h * 0.8f); path.lineTo(x, h * 0.8f); path.close()
                c.drawPath(path, p)
                p.textSize = labS; p.color = 0xFFB0A9BC.toInt(); c.drawText(pr.first, x + mark, h * 0.76f, p)
                val lw = p.measureText(pr.first)
                p.textSize = valS; p.color = Color.WHITE; c.drawText(pr.second, x + mark + lw + dp(8), h * 0.8f, p)
                x += mark + lw + dp(8) + p.measureText(pr.second) + gap
            }
        }
    }
 
    // ---------- barra de volumen neón ----------
    private inner class VolumeView(ctx: Context) : View(ctx) {
        var level = 0.5f
        var muted = false
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val rect = RectF()
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val cy = h / 2
            rect.set(0f, 0f, w, h)
            p.style = Paint.Style.FILL; p.color = 0xE60A0A12.toInt(); c.drawRoundRect(rect, h / 2, h / 2, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(1).toFloat(); p.color = withAlpha(skin.accent, 0x66); c.drawRoundRect(rect, h / 2, h / 2, p)
            val x0 = h * 0.9f; val x1 = w - h * 1.0f
            val xl = x0 + (x1 - x0) * (if (muted) 0f else level)
            p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = dp(6).toFloat(); p.color = 0x33FFFFFF; c.drawLine(x0, cy, x1, cy, p)
            if (!muted && level > 0.01f) {
                p.color = withAlpha(skin.accent, 0x22); p.strokeWidth = dp(20).toFloat(); c.drawLine(x0, cy, xl, cy, p)
                p.color = withAlpha(skin.accent, 0x55); p.strokeWidth = dp(12).toFloat(); c.drawLine(x0, cy, xl, cy, p)
                p.color = skin.accent; p.strokeWidth = dp(6).toFloat(); c.drawLine(x0, cy, xl, cy, p)
            }
            p.style = Paint.Style.FILL; p.color = 0xFF0D0C14.toInt(); c.drawCircle(xl, cy, h * 0.36f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(2).toFloat(); p.color = if (muted) 0xFFFF6B6B.toInt() else skin.accent; c.drawCircle(xl, cy, h * 0.36f, p)
            val s = h * 0.17f
            p.style = Paint.Style.FILL; p.color = Color.WHITE
            path.reset(); path.moveTo(xl - s * 0.9f, cy - s * 0.35f); path.lineTo(xl - s * 0.4f, cy - s * 0.35f); path.lineTo(xl + s * 0.1f, cy - s * 0.8f)
            path.lineTo(xl + s * 0.1f, cy + s * 0.8f); path.lineTo(xl - s * 0.4f, cy + s * 0.35f); path.lineTo(xl - s * 0.9f, cy + s * 0.35f); path.close()
            c.drawPath(path, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.22f
            if (muted) { p.color = 0xFFFF6B6B.toInt(); c.drawLine(xl - s, cy + s, xl + s, cy - s, p) }
            else for (i in 1..2) if (level > i / 3f) {
                val rr = s * (0.45f + 0.45f * i)
                rect.set(xl + s * 0.1f - rr, cy - rr, xl + s * 0.1f + rr, cy + rr); c.drawArc(rect, -40f, 80f, false, p)
            }
            p.style = Paint.Style.FILL; p.textAlign = Paint.Align.RIGHT; p.textSize = h * 0.4f; p.color = Color.WHITE
            p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
            c.drawText(if (muted) "MUTE" else "${(level * 100).toInt()}", w - h * 0.35f, cy + h * 0.14f, p)
            p.textAlign = Paint.Align.LEFT
        }
    }
 
    private fun showVolume() {
        val v = volView ?: return
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        v.level = if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0f
        v.muted = am.isStreamMute(AudioManager.STREAM_MUSIC)
        v.visibility = View.VISIBLE; v.animate().cancel(); v.alpha = 1f; v.invalidate()
        handler.removeCallbacks(volHide); handler.postDelayed(volHide, 1900)
    }
 
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (flag("volbar") && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE)) {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val dir = when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
                    KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
                    else -> AudioManager.ADJUST_TOGGLE_MUTE
                }
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
                showVolume()
                return true
            } catch (e: Throwable) { }
        }
        return super.onKeyDown(keyCode, event)
    }
 
    // ---------- widgets nuevos ----------
    private fun opt(k: String): Boolean = prefs.getBoolean(k, false)
 
    private fun toMin(t: String): Int {
        val a = t.split(":")
        return if (a.size == 2) (a[0].toIntOrNull() ?: 0) * 60 + (a[1].toIntOrNull() ?: 0) else -1
    }
 
    private fun aqiLabel(a: Int): String = when {
        a < 0 -> "—"; a <= 50 -> "Bueno"; a <= 100 -> "Moderado"; a <= 150 -> "Dañino sensibles"; else -> "Malo"
    }
 
    private fun wordsTime(h24: Int, m: Int): Pair<String, String> {
        val nums = arrayOf("doce", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once")
        var h = h24 % 12; var mm = ((m + 2) / 5) * 5
        if (mm == 60) { mm = 0; h = (h + 1) % 12 }
        if (mm >= 35) h = (h + 1) % 12
        val suf = when (mm) {
            0 -> "en punto"; 5 -> "y cinco"; 10 -> "y diez"; 15 -> "y cuarto"; 20 -> "y veinte"; 25 -> "y veinticinco"; 30 -> "y media"
            35 -> "menos veinticinco"; 40 -> "menos veinte"; 45 -> "menos cuarto"; 50 -> "menos diez"; else -> "menos cinco"
        }
        return Pair(if (h == 1) "Es la" else "Son las", "${nums[h]} $suf")
    }
 
    private inner class AnalogClock(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(dp(150), dp(150)) }
        private fun hand(c: Canvas, cx: Float, deg: Float, len: Float, w: Float, col: Int) {
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = w; p.color = col
            val a = Math.toRadians(deg - 90.0)
            c.drawLine(cx, cx, cx + (len * Math.cos(a)).toFloat(), cx + (len * Math.sin(a)).toFloat(), p)
        }
        override fun onDraw(c: Canvas) {
            val s = width.toFloat(); val cx = s / 2; val r = s / 2 - dp(6)
            p.style = Paint.Style.FILL; p.color = 0xF2050508.toInt(); c.drawCircle(cx, cx, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(3).toFloat(); p.color = skin.accent; c.drawCircle(cx, cx, r, p)
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0 - 90); val big = i % 5 == 0
                val r1 = r - dp(4); val r2 = r - (if (big) dp(12) else dp(8))
                p.strokeWidth = if (big) dp(2).toFloat() else dp(1).toFloat(); p.color = if (big) Color.WHITE else 0x66FFFFFF
                c.drawLine(cx + (r1 * Math.cos(a)).toFloat(), cx + (r1 * Math.sin(a)).toFloat(), cx + (r2 * Math.cos(a)).toFloat(), cx + (r2 * Math.sin(a)).toFloat(), p)
            }
            val cal = Calendar.getInstance()
            val sec = cal.get(Calendar.SECOND).toFloat(); val min = cal.get(Calendar.MINUTE) + sec / 60f; val hr = (cal.get(Calendar.HOUR) % 12) + min / 60f
            hand(c, cx, hr * 30f, r * 0.5f, dp(5).toFloat(), Color.WHITE)
            hand(c, cx, min * 6f, r * 0.75f, dp(3).toFloat(), Color.WHITE)
            hand(c, cx, sec * 6f, r * 0.85f, dp(2).toFloat(), skin.accent)
            p.style = Paint.Style.FILL; p.color = skin.accent; c.drawCircle(cx, cx, dp(5).toFloat(), p)
            postInvalidateDelayed(1000)
        }
    }
 
    private inner class WordClock(ctx: Context) : View(ctx) {
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
        override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(dp(300), dp(96)) }
        override fun onDraw(c: Canvas) {
            val cal = Calendar.getInstance()
            val pr = wordsTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            tp.typeface = body(); tp.textSize = dp(15).toFloat(); tp.color = 0xFFB0A9BC.toInt(); c.drawText(pr.first, 0f, dp(18).toFloat(), tp)
            tp.typeface = disp(); tp.color = Color.WHITE
            var size = dp(30).toFloat(); tp.textSize = size
            while (tp.measureText(pr.second) > width - dp(8) && size > dp(12)) { size -= 1f; tp.textSize = size }
            c.drawText(pr.second, 0f, dp(62).toFloat(), tp)
            tp.typeface = body(); tp.textSize = dp(12).toFloat(); tp.color = skin.accent
            c.drawText(java.text.SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(cal.time), 0f, dp(88).toFloat(), tp)
            postInvalidateDelayed(15000)
        }
    }
 
    private inner class TimeProgress(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(dp(300), dp(124)) }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val n = Calendar.getInstance()
            val dayF = (n.get(Calendar.HOUR_OF_DAY) * 3600 + n.get(Calendar.MINUTE) * 60 + n.get(Calendar.SECOND)) / 86400f
            val dow = (n.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val vals = floatArrayOf(dayF, (dow + dayF) / 7f,
                (n.get(Calendar.DAY_OF_MONTH) - 1 + dayF) / n.getActualMaximum(Calendar.DAY_OF_MONTH),
                (n.get(Calendar.DAY_OF_YEAR) - 1 + dayF) / n.getActualMaximum(Calendar.DAY_OF_YEAR))
            val names = arrayOf("HOY", "SEMANA", "MES", "AÑO")
            for (i in 0 until 4) {
                val y = dp(4) + i * dp(30).toFloat()
                p.style = Paint.Style.FILL; p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
                p.textSize = dp(12).toFloat(); p.textAlign = Paint.Align.LEFT; p.color = 0xFFD6D0E0.toInt()
                c.drawText(names[i], 0f, y + dp(15), p)
                segs(c, p, path, dp(62).toFloat(), y, w - dp(62 + 56).toFloat(), dp(20).toFloat(), vals[i], 20)
                p.style = Paint.Style.FILL; p.textAlign = Paint.Align.RIGHT; p.textSize = dp(15).toFloat(); p.color = Color.WHITE
                c.drawText("${(vals[i] * 100).toInt()}%", w, y + dp(16), p)
            }
            postInvalidateDelayed(30000)
        }
    }
 
    private inner class MonthView(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(dp(270), dp(196)) }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val cal = Calendar.getInstance(); val today = cal.get(Calendar.DAY_OF_MONTH)
            val title = java.text.SimpleDateFormat("MMMM yyyy", Locale("es")).format(cal.time).replaceFirstChar { it.uppercase() }
            tp.textAlign = Paint.Align.LEFT; tp.textSize = dp(15).toFloat(); tp.color = skin.accent; tp.typeface = Typeface.DEFAULT_BOLD
            c.drawText(title, 0f, dp(15).toFloat(), tp)
            val colW = w / 7; val rowH = (h - dp(24)) / 7
            tp.textAlign = Paint.Align.CENTER; tp.textSize = dp(11).toFloat(); tp.typeface = body(); tp.color = 0xFFB0A9BC.toInt()
            val names = arrayOf("L", "M", "X", "J", "V", "S", "D")
            for (i in 0..6) c.drawText(names[i], colW * i + colW / 2, dp(24) + rowH * 0.72f, tp)
            val first = (Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_WEEK) + 5) % 7
            for (d in 1..cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                val idx = first + d - 1; val col = idx % 7; val row = idx / 7 + 1
                val cx = colW * col + colW / 2; val top = dp(24) + rowH * row
                if (d == today) {
                    p.style = Paint.Style.FILL; p.color = skin.accent; c.drawCircle(cx, top + rowH / 2, minOf(rowH, colW) * 0.46f, p)
                    tp.color = 0xFF05050A.toInt()
                } else tp.color = if (col >= 5) 0xFFB0A9BC.toInt() else Color.WHITE
                c.drawText("$d", cx, top + rowH * 0.72f, tp)
            }
            postInvalidateDelayed(60000)
        }
    }
 
    private inner class SkyView(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val rect = RectF()
        override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(dp(300), dp(132)) }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val cx = w / 2; val cy = h * 0.72f; val r = minOf(w * 0.36f, h * 0.66f)
            rect.set(cx - r, cy - r, cx + r, cy + r)
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = dp(3).toFloat(); p.color = 0x33FFFFFF
            c.drawArc(rect, 180f, 180f, false, p); c.drawLine(cx - r - dp(12), cy, cx + r + dp(12), cy, p)
            val sr = toMin(sunrise); val ss = toMin(sunset)
            val n = Calendar.getInstance(); val nm = n.get(Calendar.HOUR_OF_DAY) * 60 + n.get(Calendar.MINUTE)
            if (sr >= 0 && ss > sr) {
                val f = ((nm - sr).toFloat() / (ss - sr)).coerceIn(0f, 1f)
                p.color = skin.accent; c.drawArc(rect, 180f, 180f * f, false, p)
                val a = Math.toRadians(180.0 + 180.0 * f)
                p.style = Paint.Style.FILL; p.color = if (nm in sr..ss) 0xFFFFD84D.toInt() else 0xFF8896B3.toInt()
                c.drawCircle(cx + (r * Math.cos(a)).toFloat(), cy + (r * Math.sin(a)).toFloat(), dp(8).toFloat(), p)
            }
            tp.typeface = Typeface.DEFAULT_BOLD; tp.textSize = dp(15).toFloat(); tp.color = Color.WHITE
            c.drawText("UV ${if (uvMax >= 0) uvMax.toInt().toString() else "—"}", cx, cy - r * 0.42f, tp)
            tp.typeface = body(); tp.textSize = dp(12).toFloat(); tp.color = 0xFFD6D0E0.toInt()
            c.drawText("Aire ${if (aqi >= 0) aqi.toString() else "—"} ${aqiLabel(aqi)}", cx, cy - r * 0.1f, tp)
            tp.textSize = dp(12).toFloat(); tp.color = skin.accent
            tp.textAlign = Paint.Align.LEFT; c.drawText("Sale ${if (sunrise.isEmpty()) "—" else sunrise}", 0f, h - dp(4), tp)
            tp.textAlign = Paint.Align.RIGHT; c.drawText("Se oculta ${if (sunset.isEmpty()) "—" else sunset}", w, h - dp(4), tp)
            tp.textAlign = Paint.Align.CENTER
            postInvalidateDelayed(30000)
        }
    }
 
    private inner class NetGraph(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(dp(300), dp(112)) }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val top = dp(24).toFloat()
            var mx = 10f; for (v in netHist) if (v > mx) mx = v
            val n = netHist.size
            path.reset()
            for (i in 0 until n) {
                val x = w * i / (n - 1); val y = h - (h - top) * (netHist[i] / mx) - dp(2)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val line = Path(path); path.lineTo(w, h); path.lineTo(0f, h); path.close()
            p.style = Paint.Style.FILL; p.color = withAlpha(skin.accent, 0x33); c.drawPath(path, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(2).toFloat(); p.strokeJoin = Paint.Join.ROUND; p.color = skin.accent; c.drawPath(line, p)
            p.style = Paint.Style.FILL; p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
            p.textAlign = Paint.Align.LEFT; p.textSize = dp(16).toFloat(); p.color = Color.WHITE
            c.drawText("${"%.1f".format(stat.mbps)} Mbps", 0f, dp(16).toFloat(), p)
            p.textAlign = Paint.Align.RIGHT; p.textSize = dp(12).toFloat(); p.color = 0xFFB0A9BC.toInt()
            c.drawText("pico ${mx.toInt()}", w, dp(16).toFloat(), p)
            postInvalidateDelayed(1000)
        }
    }
 
    private fun widgetCard(title: String, v: View): LinearLayout {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(12)); background = dark(24) }
        card.addView(TextView(this).apply {
            text = title.uppercase(); textSize = 12f; letterSpacing = 0.2f; typeface = body(); setTextColor(skin.accent); setPadding(0, 0, 0, dp(6))
        })
        card.addView(v)
        return card
    }
 
    private fun extraWidgets(): List<Triple<String, String, () -> View>> = listOf(
        Triple("wg_aclock", "Reloj analógico", { AnalogClock(this) }),
        Triple("wg_words", "Reloj en palabras", { WordClock(this) }),
        Triple("wg_cal", "Calendario", { MonthView(this) }),
        Triple("wg_sky", "Sol y aire", { SkyView(this) }),
        Triple("wg_netg", "Tráfico de red", { NetGraph(this) }),
        Triple("wg_tprog", "Progreso del tiempo", { TimeProgress(this) })
    )
 
    private fun panelsOverlay() {
        val d = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val wrap = FrameLayout(this).apply { setBackgroundColor(0xEB080810.toInt()) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(44), dp(24), dp(44), dp(14)) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10)) }
        head.addView(TextView(this).apply { text = "PANELES"; textSize = 22f; letterSpacing = 0.2f; typeface = disp(); setTextColor(skin.accent) },
            LinearLayout.LayoutParams(0, WRAP, 1f))
        val close = chip("Cerrar") { d.dismiss() }
        head.addView(close)
        col.addView(head)
        val grid = GridLayout(this).apply { columnCount = 2 }
        for (e in extraWidgets()) grid.addView(widgetCard(e.second, e.third()), GridLayout.LayoutParams().apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        col.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFocusable = true; addView(grid) })
        wrap.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        d.setContentView(wrap); d.window?.setLayout(MATCH, MATCH); d.show(); close.requestFocus()
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
    private fun glassBase(focused: Boolean, radius: Int): GradientDrawable {
        val a = skin.accent
        val fill: IntArray; val sw: Int; val sc: Int
        when (prefs.getInt("cstyle", 0) % 7) {
            1, 3, 4, 5, 6 -> {
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
 
    private fun pattern(kind: Int): Bitmap = patCache.getOrPut(kind) {
        val s = dp(when (kind) { 3 -> 8; 4 -> 10; 5 -> 12; else -> 4 }).coerceAtLeast(4)
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (kind) {
            3 -> { val h = s / 2; for (i in 0..1) for (j in 0..1) { p.color = if ((i + j) % 2 == 0) 0x1AFFFFFF else 0x22000000; c.drawRect((i * h).toFloat(), (j * h).toFloat(), ((i + 1) * h).toFloat(), ((j + 1) * h).toFloat(), p) } }
            4 -> { p.color = 0x33FFFFFF; c.drawCircle(s / 2f, s / 2f, s * 0.09f, p) }
            5 -> { p.color = 0x1FFFFFFF; p.strokeWidth = s * 0.16f; c.drawLine(0f, s.toFloat(), s.toFloat(), 0f, p) }
            else -> { p.color = 0x55000000; c.drawRect(0f, s * 0.5f, s.toFloat(), s.toFloat(), p); p.color = 0x0DFFFFFF; c.drawRect(0f, 0f, s.toFloat(), s * 0.25f, p) }
        }
        b
    }
 
    private fun glass(focused: Boolean, radius: Int): Drawable {
        val cs = prefs.getInt("cstyle", 0) % 7
        val base = glassBase(focused, radius)
        if (cs < 3) return base
        val tile = BitmapDrawable(resources, pattern(cs)).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        return LayerDrawable(arrayOf(base, tile))
    }
 
    private fun focusable(v: View, radius: Int, onClick: () -> Unit, onLong: (() -> Unit)? = null, pop: View? = null, popScale: Float = 1.18f, onFocus: ((Boolean) -> Unit)? = null): View {
        v.isFocusable = true; v.isClickable = true; v.clipToOutline = true
        fun paint(f: Boolean) {
            v.background = glass(f, radius)
            pop?.animate()?.scaleX(if (f) popScale else 1f)?.scaleY(if (f) popScale else 1f)?.setDuration(180)?.start()
        }
        paint(false)
        v.setOnFocusChangeListener { _, f -> paint(f); if (f) sfx(0); onFocus?.invoke(f) }
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
            text = label; textSize = 14f * s; typeface = body(); setTextColor(Color.WHITE); maxLines = 1
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
            typeface = body()
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
            typeface = body(); setTextColor(skin.accent); setShadowLayer(4f, 0f, 1f, Color.BLACK); setPadding(dp(4), 0, 0, 0)
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
    private fun clockView(): View {
        fun tc(f: String, sz: Float, col: Int, tf: Typeface): TextClock = TextClock(this).apply {
            format24Hour = f; format12Hour = f; textSize = sz; setTextColor(col); typeface = tf
        }
        val dim = 0xFFE6E0F0.toInt()
        return when (prefs.getInt("clk", 0) % 5) {
            1 -> tc("HH:mm", 32f, Color.WHITE, Typeface.create(disp(), Typeface.BOLD)).apply { setShadowLayer(22f, 0f, 0f, skin.accent) }
            2 -> LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.END
                addView(tc("HH:mm", 28f, Color.WHITE, disp())); addView(tc("EEE d MMM", 12f, dim, body()))
            }
            3 -> tc("'[ 'HH:mm:ss' ]'", 22f, skin.accent, Typeface.create("monospace", Typeface.BOLD))
            4 -> tc("h:mm a", 20f, dim, body())
            else -> LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(tc("EEE d MMM", 14f, dim, body()), LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(14) })
                addView(tc("HH:mm", 26f, Color.WHITE, disp()).apply { setShadowLayer(6f, 0f, 2f, Color.BLACK) })
            }
        }
    }
 
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
 
    private fun mediaClassic(): LinearLayout {
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
        val pg = ProgView(this); mediaProg = pg
        card.addView(pg, LinearLayout.LayoutParams(MATCH, dp(22)).apply { topMargin = dp(6) })
        val ctl = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }
        fun g() = LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6); rightMargin = dp(6) }
        ctl.addView(chip("|<") { ctrl?.transportControls?.skipToPrevious() }, g())
        val pl = chip("||") { togglePlay() }
        ctl.addView(pl, g())
        ctl.addView(chip(">|") { ctrl?.transportControls?.skipToNext() }, g())
        card.addView(ctl)
        mediaBox = card; mediaCtl = ctl; mediaArt = art; mediaTitle = ti; mediaArtist = ar; mediaPlay = pl as TextView
        return card
    }
 
    private fun fmt(ms: Long): String { val t = (ms / 1000).coerceAtLeast(0); return "${t / 60}:${"%02d".format(t % 60)}" }
 
    private inner class ProgView(ctx: Context) : View(ctx) {
        private var pos = 0L
        private var dur = 0L
        private var playing = false
        private var t0 = 0L
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun set(pos: Long, dur: Long, playing: Boolean) {
            this.pos = pos; this.dur = dur; this.playing = playing; t0 = SystemClock.elapsedRealtime(); invalidate()
        }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val cy = h * 0.35f
            val cur = if (playing) pos + (SystemClock.elapsedRealtime() - t0) else pos
            val f = if (dur > 0) (cur.toFloat() / dur).coerceIn(0f, 1f) else 0f
            p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE; p.strokeWidth = dp(4).toFloat()
            p.color = 0x33FFFFFF; c.drawLine(dp(2).toFloat(), cy, w - dp(2), cy, p)
            p.color = skin.accent; c.drawLine(dp(2).toFloat(), cy, dp(2) + (w - dp(4)) * f, cy, p)
            p.style = Paint.Style.FILL; p.textSize = dp(10).toFloat(); p.color = 0xFFB0A9BC.toInt()
            p.textAlign = Paint.Align.LEFT; c.drawText(fmt(cur), 0f, h - dp(1), p)
            p.textAlign = Paint.Align.RIGHT; c.drawText(if (dur > 0) fmt(dur) else "--:--", w, h - dp(1), p)
            if (playing) postInvalidateDelayed(1000)
        }
    }
 
    private inner class EqView(ctx: Context) : View(ctx) {
        var playing = false
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val n = 16
            val t = SystemClock.elapsedRealtime() / 1000.0
            val gap = dp(3).toFloat(); val bw = (w - gap * (n - 1)) / n
            p.style = Paint.Style.FILL
            for (i in 0 until n) {
                val a = if (playing) 0.2f + 0.8f * Math.abs(Math.sin(t * (2.2 + (i % 5) * 0.7) + i * 1.3)).toFloat() else 0.12f
                val x = i * (bw + gap)
                p.color = if (a > 0.9f) 0xFFFF3B30.toInt() else skin.accent
                rect.set(x, h - h * a, x + bw, h); c.drawRoundRect(rect, bw / 2, bw / 2, p)
            }
            if (playing) postInvalidateDelayed(60)
        }
    }
 
    private fun baseMediaCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
        background = dark(26); minimumWidth = dp(280); visibility = View.GONE
    }
 
    private fun txtCol(titleSize: Float): LinearLayout {
        val ti = TextView(this).apply {
            textSize = titleSize; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = dp(190)
        }
        val ar = TextView(this).apply {
            textSize = 13f; setTextColor(0xFFD6D0E0.toInt()); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = dp(190)
        }
        mediaTitle = ti; mediaArtist = ar
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(ti); addView(ar) }
    }
 
    private fun ctlRow(compact: Boolean): LinearLayout {
        val ctl = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
        fun g() = LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6); rightMargin = dp(6) }
        if (!compact) ctl.addView(chip("|<") { ctrl?.transportControls?.skipToPrevious() }, g())
        val pl = chip("||") { togglePlay() }
        ctl.addView(pl, g()); mediaPlay = pl as TextView
        ctl.addView(chip(">|") { ctrl?.transportControls?.skipToNext() }, g())
        mediaCtl = ctl
        return ctl
    }
 
    private fun oval(color: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
 
    private fun mediaVinyl(): LinearLayout {
        val card = baseMediaCard(); mediaBox = card
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val disc = FrameLayout(this)
        val art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = oval(0xFF222222.toInt()) }
        disc.addView(art, FrameLayout.LayoutParams(MATCH, MATCH))
        disc.addView(View(this).apply { background = oval(0xFF0D0C14.toInt()).apply { setStroke(dp(2), skin.accent) } },
            FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        mediaArt = art
        mediaSpin = ObjectAnimator.ofFloat(disc, "rotation", 0f, 360f).apply {
            duration = 7000; interpolator = LinearInterpolator(); repeatCount = ValueAnimator.INFINITE
        }
        row.addView(disc, LinearLayout.LayoutParams(dp(92), dp(92)))
        val col = txtCol(17f); col.setPadding(dp(14), 0, 0, 0)
        val pg = ProgView(this); mediaProg = pg
        col.addView(pg, LinearLayout.LayoutParams(dp(170), dp(22)).apply { topMargin = dp(6) })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        card.addView(row); card.addView(ctlRow(false))
        return card
    }
 
    private fun mediaEq(): LinearLayout {
        val card = baseMediaCard(); mediaBox = card
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0x33FFFFFF) }
        }
        mediaArt = art
        top.addView(art, LinearLayout.LayoutParams(dp(54), dp(54)))
        val col = txtCol(16f); col.setPadding(dp(12), 0, 0, 0)
        top.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        card.addView(top)
        val eq = EqView(this); eqV = eq
        card.addView(eq, LinearLayout.LayoutParams(MATCH, dp(38)).apply { topMargin = dp(8) })
        card.addView(ctlRow(false))
        return card
    }
 
    private fun mediaPill(): LinearLayout {
        val card = baseMediaCard(); mediaBox = card
        card.orientation = LinearLayout.HORIZONTAL; card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(10), dp(8), dp(10), dp(8)); card.background = dark(34)
        val art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = oval(0xFF222222.toInt()) }
        mediaArt = art
        card.addView(art, LinearLayout.LayoutParams(dp(44), dp(44)))
        val col = txtCol(15f); col.setPadding(dp(10), 0, dp(8), 0)
        card.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        val ctl = ctlRow(true); ctl.setPadding(0, 0, 0, 0)
        card.addView(ctl)
        return card
    }
 
    private fun mediaCover(): LinearLayout {
        val card = baseMediaCard(); mediaBox = card
        card.setPadding(0, 0, 0, dp(8)); card.clipToOutline = true
        val f = FrameLayout(this)
        val art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF1A1826.toInt()) }
        mediaArt = art
        f.addView(art, FrameLayout.LayoutParams(MATCH, MATCH))
        f.addView(View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0xEE000000.toInt())) },
            FrameLayout.LayoutParams(MATCH, MATCH))
        val col = txtCol(22f); col.setPadding(dp(14), 0, dp(14), dp(26))
        mediaTitle?.maxWidth = dp(260); mediaArtist?.maxWidth = dp(260)
        f.addView(col, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START))
        val pg = ProgView(this); mediaProg = pg
        f.addView(pg, FrameLayout.LayoutParams(MATCH, dp(22), Gravity.BOTTOM).apply { setMargins(dp(14), 0, dp(14), dp(2)) })
        card.addView(f, LinearLayout.LayoutParams(MATCH, dp(170)))
        card.addView(ctlRow(false))
        return card
    }
 
    private fun mediaCard(): LinearLayout {
        mediaProg = null; eqV = null; mediaSpin?.cancel(); mediaSpin = null
        return when (prefs.getInt("mstyle", 0) % 5) {
            1 -> mediaVinyl(); 2 -> mediaEq(); 3 -> mediaPill(); 4 -> mediaCover(); else -> mediaClassic()
        }
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
            val stt = c.playbackState; val playingNow = stt?.state == PlaybackState.STATE_PLAYING
            var pos = stt?.position ?: 0L
            if (playingNow && stt != null) pos += ((SystemClock.elapsedRealtime() - stt.lastPositionUpdateTime) * stt.playbackSpeed).toLong()
            mediaProg?.set(pos, md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L, playingNow)
            eqV?.let { it.playing = playingNow; it.invalidate() }
            val sa = mediaSpin
            if (sa != null) {
                if (playingNow) { if (!sa.isStarted) sa.start() else if (sa.isPaused) sa.resume() }
                else if (sa.isRunning) sa.pause()
            }
            mediaCtl?.visibility = View.VISIBLE
            box.visibility = View.VISIBLE
        } catch (e: SecurityException) {
            mediaTitle?.text = "Música: falta permiso"; mediaArtist?.text = "Activa el acceso a notificaciones"
            mediaArt?.setImageDrawable(null); mediaCtl?.visibility = View.GONE; box.visibility = View.VISIBLE
        } catch (e: Throwable) { box.visibility = View.GONE }
    }
 
    private fun build() {
        when (prefs.getInt("layout", 0) % 3) {
            1 -> { buildDock(); return }
            2 -> { buildConsole(); return }
        }
        val hidden = getSet("hidden"); val favs = getSet("fav")
        val visible = apps().filter { it.activityInfo.packageName !in hidden }
        val favApps = visible.filter { it.activityInfo.packageName in favs }
        val restApps = visible.filter { it.activityInfo.packageName !in favs }
        val s = scale
        val tileW = dp((168 * s).toInt()); val tileH = dp((124 * s).toInt())
        ramG = null; diskG = null; netG = null; tempG = null; statusRow = null; weatherView = null
        mediaBox = null; heroBox = null; clusterV = null; segV = null; sportV = null; orbV = null; wxIcon = null
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
        chips.addView(chip("Paneles") { panelsOverlay() }, gap())
        chips.addView(chip("Personalizar") { customPanel() }, gap())
        chips.addView(chip("Optimizar") { optimize() }, gap())
        bar.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(dp(4), dp(6), dp(4), dp(6)); addView(chips)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        val wxs = prefs.getInt("wxs", 0) % 4
        if (flag("wg_weather") && wxs != 3) {
            if (wxs == 1) { wxIcon = WxIcon(this); bar.addView(wxIcon, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(6) }) }
            weatherView = TextView(this).apply {
                textSize = if (wxs == 1) 20f else 17f; typeface = body(); setTextColor(skin.accent); setShadowLayer(4f, 0f, 1f, Color.BLACK)
            }
            bar.addView(weatherView, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(16) })
        }
        bar.addView(clockView(), LinearLayout.LayoutParams(WRAP, WRAP))
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
            textSize = 38f; typeface = disp(); setTextColor(Color.WHITE)
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
                val gs = prefs.getInt("gstyle", 0) % 5
                if (gs == 2) { clusterV = ClusterView(this); card.addView(clusterV, LinearLayout.LayoutParams(dp(300), dp(150))) }
                else if (gs == 4) { orbV = OrbitalView(this, false); card.addView(orbV, LinearLayout.LayoutParams(dp(330), dp(140))) }
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
        addOverlays(root, Gravity.BOTTOM or Gravity.END, true)
        val fx = seasonFx()
        if (fx.isNotEmpty()) root.addView(SeasonView(this, fx), FrameLayout.LayoutParams(MATCH, MATCH))
        if (!splashShown && flag("splash")) { splashShown = true; root.addView(splashView(), FrameLayout.LayoutParams(MATCH, MATCH)) }
        sfxReady = false; handler.postDelayed({ sfxReady = true }, 900)
        setContentView(root)
        chips.getChildAt(0)?.requestFocus()
        updateCaption()
        refreshStats()
        renderWeather()
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
        for (e in extraWidgets()) if (opt(e.first)) r.addView(widgetCard(e.second, e.third()), LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(14) })
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
 
    private fun toggleLite(reopen: Boolean = true) {
        val on = !liteOn(); prefs.edit().putBoolean("lite", on).apply()
        try {
            val sc = if (on) "0.5" else "1.0"
            for (k in listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale"))
                Settings.Global.putString(contentResolver, k, sc)
        } catch (e: SecurityException) { }
        build(); if (reopen) customPanel()
    }
 
    private fun addOverlays(root: FrameLayout, hudGravity: Int, hud: Boolean) {
        if (hud && flag("wg_hud")) {
            val h = HudView(this); hudV = h
            root.addView(h, FrameLayout.LayoutParams(dp(460), dp(36), hudGravity).apply { setMargins(dp(40), dp(8), dp(40), dp(8)) })
        }
        val vv = VolumeView(this); vv.alpha = 0f; vv.visibility = View.INVISIBLE; volView = vv
        root.addView(vv, FrameLayout.LayoutParams(dp(520), dp(60), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(100) })
    }
 
    private fun baseRoot(scrim: IntArray): FrameLayout {
        val root = FrameLayout(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(skin.top, skin.bottom)) }
        wallLayer = FrameLayout(this)
        wallBlur = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        wallView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        wallLayer!!.addView(wallBlur, FrameLayout.LayoutParams(MATCH, MATCH))
        wallLayer!!.addView(wallView, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(wallLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, scrim) }, FrameLayout.LayoutParams(MATCH, MATCH))
        val ws = walls()
        if (wallBmp != null) applyWall(false)
        else if (ws.isNotEmpty()) loadWall(ws[prefs.getInt("wall", 0).coerceIn(0, ws.size - 1)], false)
        return root
    }
 
    private fun indicatorRow(): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        for (k in 0..6) {
            val iv = IndicatorView(this, k); indicators.add(iv)
            row.addView(iv, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(8) })
        }
        return row
    }
 
    private fun makeHero(): LinearLayout {
        val hb = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(44), 0, dp(24), 0); visibility = View.GONE }
        heroBox = hb
        heroProv = TextView(this).apply { textSize = 14f; letterSpacing = 0.25f; setTextColor(skin.accent); setShadowLayer(6f, 0f, 2f, Color.BLACK) }
        heroTitle = TextView(this).apply { textSize = 36f; typeface = disp(); setTextColor(Color.WHITE); maxLines = 2; setShadowLayer(12f, 0f, 3f, Color.BLACK) }
        val btn = chip("Ver") { captionAction() }
        heroBtn = btn as TextView
        hb.addView(heroProv); hb.addView(heroTitle, LinearLayout.LayoutParams(dp(520), WRAP))
        hb.addView(btn, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
        return hb
    }
 
    private inner class GridIcon(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        override fun onDraw(c: Canvas) {
            val s = width.toFloat(); val g = s * 0.08f; val b = (s - g * 4) / 3
            p.color = skin.accent
            for (i in 0..2) for (j in 0..2) {
                rect.set(g + i * (b + g), g + j * (b + g), g + i * (b + g) + b, g + j * (b + g) + b); c.drawRoundRect(rect, b * 0.25f, b * 0.25f, p)
            }
        }
    }
 
    private fun dockTile(ri: ResolveInfo): View {
        val pkg = ri.activityInfo.packageName; val label = ri.loadLabel(packageManager).toString()
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(8), dp(6), dp(6)) }
        val iv = ImageView(this).apply { setImageDrawable(ri.loadIcon(packageManager)) }
        v.addView(iv, LinearLayout.LayoutParams(dp(60), dp(60)))
        v.addView(TextView(this).apply {
            text = label; textSize = 12f; typeface = body(); setTextColor(Color.WHITE); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
        })
        return focusable(v, 26, { goPkg(pkg) }, { appMenu(pkg, label) }, iv, 1.2f)
    }
 
    private fun drawer() {
        val d = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val wrap = FrameLayout(this).apply { setBackgroundColor(0xEB080810.toInt()) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(48), dp(28), dp(48), dp(16)) }
        col.addView(TextView(this).apply {
            text = "TODAS LAS APPS"; textSize = 22f; letterSpacing = 0.2f; typeface = disp(); setTextColor(skin.accent); setPadding(0, 0, 0, dp(12))
        })
        val grid = GridLayout(this).apply { columnCount = 5 }
        var first: View? = null
        val hidden = getSet("hidden")
        apps().filter { it.activityInfo.packageName !in hidden }.forEach { ri ->
            val pkg = ri.activityInfo.packageName; val label = ri.loadLabel(packageManager).toString()
            val t = tile(label, ri.loadIcon(packageManager), { d.dismiss(); goPkg(pkg) }, { appMenu(pkg, label) })
            if (first == null) first = t
            grid.addView(t, GridLayout.LayoutParams().apply { width = dp(150); height = dp(112); setMargins(dp(6), dp(6), dp(6), dp(6)) })
        }
        col.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; addView(grid) })
        wrap.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        d.setContentView(wrap); d.window?.setLayout(MATCH, MATCH); d.show(); first?.requestFocus()
    }
 
    // diseño alterno: reloj grande, widget orbital, dock inferior y cajón de apps
    private fun buildDock() {
        val hidden = getSet("hidden"); val favs = getSet("fav")
        val visible = apps().filter { it.activityInfo.packageName !in hidden }
        val dockApps = visible.filter { it.activityInfo.packageName in favs }.take(7)
        ramG = null; diskG = null; netG = null; tempG = null; statusRow = null; weatherView = null
        mediaBox = null; heroBox = null; clusterV = null; segV = null; sportV = null; orbV = null; wxIcon = null
        sysInfo = null; wxBig = null; wxDesc2 = null; wxDetail = null; wxTom = null; indicators.clear()
        val root = baseRoot(intArrayOf(0x44000000, 0x00000000, 0xCC000000.toInt()))
 
        val tl = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tl.addView(TextView(this).apply { text = greetingText(); textSize = 18f; typeface = body(); setTextColor(skin.accent); setShadowLayer(4f, 0f, 1f, Color.BLACK) })
        tl.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "h:mm"; textSize = 76f; typeface = disp(); setTextColor(Color.WHITE); setShadowLayer(12f, 0f, 3f, Color.BLACK)
        })
        tl.addView(TextClock(this).apply {
            format24Hour = "EEEE d 'de' MMMM"; format12Hour = "EEEE d 'de' MMMM"; textSize = 18f; typeface = body(); setTextColor(0xFFE6E0F0.toInt())
        })
        if (flag("wg_weather")) {
            weatherView = TextView(this).apply { textSize = 18f; typeface = body(); setTextColor(skin.accent); setShadowLayer(4f, 0f, 1f, Color.BLACK); setPadding(0, dp(4), 0, 0) }
            tl.addView(weatherView)
        }
        root.addView(tl, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(dp(44), dp(18), 0, 0) })
 
        val tr = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        tr.addView(indicatorRow(), LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = dp(4) })
        val chips = LinearLayout(this)
        fun gap() = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(10) }
        chips.addView(chip("Entrada") { inputPanel() }, gap())
        chips.addView(chip("Compartir") {
            val ok = listOf("com.homwee.multiscreenshare", "com.homwee.mmp.screenshare").firstOrNull { goPkg(it) }
            if (ok == null) toast("No disponible en esta TV")
        }, gap())
        chips.addView(chip("Asistente") { assistantPanel() }, gap())
        chips.addView(chip("Paneles") { panelsOverlay() }, gap())
        chips.addView(chip("Fondo") { nextWall() }, gap())
        chips.addView(chip("Optimizar") { optimize() }, gap())
        chips.addView(chip("Personalizar") { customPanel() }, gap())
        tr.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(dp(4), dp(6), dp(4), dp(6)); addView(chips)
        }, LinearLayout.LayoutParams(dp(560), WRAP))
        if (flag("wg_gauges")) {
            orbV = OrbitalView(this, true)
            tr.addView(orbV, LinearLayout.LayoutParams(dp(330), dp(140)).apply { topMargin = dp(8) })
        }
        root.addView(tr, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(14), dp(40), 0) })
 
        root.addView(makeHero(), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply { bottomMargin = dp(168) })
        if (flag("wg_media")) root.addView(mediaCard(), FrameLayout.LayoutParams(dp(300), WRAP, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(40), dp(160)) })
 
        val dock = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(38).toFloat(); setColor(0xC0101018.toInt()); setStroke(dp(1), withAlpha(skin.accent, 0x66)) }
        }
        dockApps.forEach { dock.addView(dockTile(it), LinearLayout.LayoutParams(dp(104), dp(108)).apply { rightMargin = dp(8) }) }
        val gi = GridIcon(this)
        val all = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(8), dp(6), dp(6)) }
        all.addView(gi, LinearLayout.LayoutParams(dp(54), dp(54)))
        all.addView(TextView(this).apply { text = "Todas"; textSize = 12f; typeface = body(); setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
        dock.addView(focusable(all, 26, { drawer() }, null, gi, 1.2f), LinearLayout.LayoutParams(dp(104), dp(108)))
        root.addView(dock, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(24) })
 
        addOverlays(root, Gravity.TOP or Gravity.CENTER_HORIZONTAL, false)
        val fx = seasonFx()
        if (fx.isNotEmpty()) root.addView(SeasonView(this, fx), FrameLayout.LayoutParams(MATCH, MATCH))
        if (!splashShown && flag("splash")) { splashShown = true; root.addView(splashView(), FrameLayout.LayoutParams(MATCH, MATCH)) }
        sfxReady = false; handler.postDelayed({ sfxReady = true }, 900)
        setContentView(root)
        dock.getChildAt(0)?.requestFocus()
        updateCaption(); refreshStats(); renderWeather()
        if (flag("wg_weather")) { lastWx = 0L; fetchWeather() }
    }
 
    // ---------- menú de juego (cambios en vivo) ----------
    private class GSetting(val label: String, val kind: Int, val get: () -> String, val act: (Int) -> Unit)
    private class GTab(val name: String, val items: () -> List<GSetting>)
 
    private fun gChoice(label: String, key: String, names: Array<String>, def: Int = 0, extra: () -> Unit = { }): GSetting {
        val n = names.size
        return GSetting(label, 0, { names[((prefs.getInt(key, def) % n) + n) % n] }) { d ->
            val cur = ((prefs.getInt(key, def) % n) + n) % n
            prefs.edit().putInt(key, (cur + (if (d == 0) 1 else d) + n) % n).apply()
            extra(); build()
        }
    }
 
    private fun gSwitch(label: String, key: String, def: Boolean = true): GSetting =
        GSetting(label, 1, { if (prefs.getBoolean(key, def)) "Sí" else "No" }) { _ ->
            prefs.edit().putBoolean(key, !prefs.getBoolean(key, def)).apply(); build()
        }
 
    private fun gAction(label: String, run: () -> Unit): GSetting = GSetting(label, 2, { "" }) { d -> if (d == 0) run() }
 
    private fun gameTabs(): List<GTab> = listOf(
        GTab("DISEÑO") { listOf(
            gChoice("Diseño de inicio", "layout", layoutNames),
            gChoice("Tema de color", "theme", skins.map { it.name }.toTypedArray()),
            gChoice("Tipografía", "font", fontNames),
            gChoice("Tarjetas y texturas", "cstyle", cardNames),
            gChoice("Temporada", "season", seasonNames),
            gChoice("Tamaño de iconos", "size", sizeNames, 1),
            gChoice("Barra superior", "bar", barNames),
            gChoice("Reloj", "clk", clkNames),
            gChoice("Clima en la barra", "wxs", wxsNames)
        ) },
        GTab("WIDGETS") { listOf(
            gChoice("Medidores", "gstyle", gNames),
            gChoice("Reproductor de música", "mstyle", mstyleNames),
            gSwitch("Mostrar medidores", "wg_gauges"),
            gSwitch("Estado de entradas", "wg_status"),
            gSwitch("Clima", "wg_weather"),
            gSwitch("Reproductor", "wg_media"),
            gSwitch("Etiquetas rápidas", "wg_hud"),
            gSwitch("Fila de información", "wg_info"),
            gSwitch("Reloj analógico", "wg_aclock", false),
            gSwitch("Reloj en palabras", "wg_words", false),
            gSwitch("Calendario", "wg_cal", false),
            gSwitch("Sol y aire", "wg_sky", false),
            gSwitch("Tráfico de red", "wg_netg", false),
            gSwitch("Progreso del tiempo", "wg_tprog", false)
        ) },
        GTab("FONDOS") { listOf(
            GSetting("Origen", 0, { if (prefs.getInt("wallmode", 0) == 0) "Mis imágenes" else "Cartelera" }) { _ ->
                val mode = prefs.getInt("wallmode", 0)
                prefs.edit().putInt("wallmode", 1 - mode).putInt("wall", -1).apply()
                wallBmp = null
                if (mode == 0) refreshCartelera() else nextWall()
            },
            gAction("Siguiente fondo") { nextWall() },
            GSetting("Ajuste", 0, { if (prefs.getInt("fit", 0) == 0) "Llenar" else "Ajustar" }) { _ ->
                prefs.edit().putInt("fit", 1 - prefs.getInt("fit", 0)).apply(); applyWall(false)
            },
            gChoice("Cambio automático", "auto", autoNames) { scheduleWall() },
            gAction("Actualizar cartelera") { refreshCartelera() }
        ) },
        GTab("AUDIO") { listOf(
            GSetting("Sonidos de navegación", 0, { sndNames[sndMode()] }) { d ->
                prefs.edit().putInt("snd", (sndMode() + (if (d == 0) 1 else d) + 4) % 4).apply(); loadSounds(); sfxReady = true; sfx(1)
            },
            gChoice("Volumen de sonidos", "sndvol", volNames, 1) { sfx(1) },
            gSwitch("Barra de volumen neón", "volbar"),
            gAction("Probar barra de volumen") { showVolume() },
            gSwitch("Voz del asistente", "tts"),
            GSetting("Motor del asistente", 0, { if (prefs.getInt("ai", 0) == 1) "ChatGPT" else "Claude" }) { _ ->
                prefs.edit().putInt("ai", 1 - prefs.getInt("ai", 0)).apply()
            }
        ) },
        GTab("SISTEMA") { listOf(
            gAction("Optimizar sistema ahora") { optimize() },
            GSetting("Modo ligero", 1, { onOff(liteOn()) }) { _ -> toggleLite(false) },
            GSetting("Bloqueo de anuncios (DNS)", 1, { onOff(dnsOn()) }) { _ -> toggleDns() },
            gSwitch("Pantalla de bienvenida", "splash"),
            gSwitch("Bienvenida al despertar", "splash_wake"),
            gAction("Probar bienvenida") { replaySplash() },
            GSetting("Tu nombre", 2, { userName().ifEmpty { "sin definir" } }) { d -> if (d == 0) askName() },
            gChoice("Estilo del menú", "menu", menuNames),
            gChoice("Posición del menú", "menupos", arrayOf("Derecha", "Izquierda")),
            gAction("Mostrar apps ocultas") { putSet("hidden", emptySet()); build() },
            gAction("Ajustes de la TV") { go(Intent(Settings.ACTION_SETTINGS)) }
        ) }
    )
 
    private fun menuBg(right: Boolean): GradientDrawable {
        val r = dp(26).toFloat()
        return GradientDrawable(if (right) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.RIGHT_LEFT,
            intArrayOf(0xE60B0A14.toInt(), 0xF2161226.toInt())).apply {
            cornerRadii = if (right) floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r) else floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            setStroke(dp(2), withAlpha(skin.accent, 0x77))
        }
    }
 
    private fun gameMenu() {
        val d = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val right = prefs.getInt("menupos", 0) % 2 == 0
        val tabs = gameTabs()
        var cur = 0
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(12)); background = menuBg(right) }
 
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, 0, dp(10)) }
        head.addView(View(this).apply { setBackgroundColor(skin.accent) }, LinearLayout.LayoutParams(dp(5), dp(26)).apply { rightMargin = dp(12) })
        head.addView(TextView(this).apply { text = "AJUSTES"; textSize = 22f; letterSpacing = 0.25f; typeface = disp(); setTextColor(Color.WHITE) },
            LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(TextView(this).apply {
            text = "EN VIVO"; textSize = 11f; letterSpacing = 0.2f; typeface = Typeface.DEFAULT_BOLD; setTextColor(skin.accent)
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(withAlpha(skin.accent, 0x2A)) }
            setPadding(dp(10), dp(3), dp(10), dp(3))
        })
        box.addView(head)
 
        val tabsRow = LinearLayout(this)
        val tabViews = ArrayList<TextView>()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(6), dp(2), dp(6)) }
        val valueViews = ArrayList<Pair<GSetting, TextView>>()
 
        fun valueText(st: GSetting): String = when (st.kind) {
            0 -> "‹  ${st.get()}  ›"
            1 -> st.get()
            else -> if (st.get().isEmpty()) "›" else "${st.get()}  ›"
        }
        fun refresh() {
            for (pr in valueViews) pr.second.text = valueText(pr.first)
            for ((i, tv) in tabViews.withIndex()) tv.setTextColor(if (i == cur) skin.accent else 0xFFB0A9BC.toInt())
        }
        fun render() {
            list.removeAllViews(); valueViews.clear()
            for (st in tabs[cur].items()) {
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(11), dp(16), dp(11)) }
                row.addView(TextView(this).apply { text = st.label; textSize = 17f; typeface = body(); setTextColor(Color.WHITE) },
                    LinearLayout.LayoutParams(0, WRAP, 1f))
                val vt = TextView(this).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(skin.accent) }
                if (st.kind == 1) {
                    vt.setPadding(dp(14), dp(4), dp(14), dp(4))
                    vt.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(withAlpha(skin.accent, 0x30)) }
                }
                row.addView(vt)
                valueViews.add(Pair(st, vt))
                focusable(row, 14, { st.act(0); refresh() })
                row.setOnKeyListener { _, code, ev ->
                    if (st.kind <= 1 && ev.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                        st.act(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1); refresh(); sfx(1); true
                    } else false
                }
                list.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(5) })
            }
            refresh()
        }
        for ((i, t) in tabs.withIndex()) {
            val tv = TextView(this).apply { text = t.name; textSize = 13f; letterSpacing = 0.12f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(14), dp(8), dp(14), dp(8)) }
            tabViews.add(tv)
            tabsRow.addView(focusable(tv, 18, { cur = i; render() }, null, null, 1.1f) { f -> if (f) { cur = i; render() } },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(6) })
        }
        box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(0, dp(4), 0, dp(6)); addView(tabsRow)
        })
        render()
        box.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; addView(list) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        box.addView(TextView(this).apply {
            text = "‹ ›  CAMBIAR      OK  APLICAR      ATRÁS  CERRAR"; textSize = 11f; letterSpacing = 0.1f; typeface = body()
            setTextColor(0xFF8D86A0.toInt()); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
        })
 
        val wrap = FrameLayout(this)
        wrap.addView(box, FrameLayout.LayoutParams(dp(470), MATCH, if (right) Gravity.END else Gravity.START))
        menuRefresh = { refresh() }
        d.setOnDismissListener { menuRefresh = null }
        d.setContentView(wrap)
        d.window?.setLayout(MATCH, MATCH)
        d.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        d.show()
        list.getChildAt(0)?.requestFocus()
    }
 
    // ---------- diseño Consola (inspirado en menús de videojuegos) ----------
    private inner class CircleBtn(ctx: Context, val kind: Int) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val rect = RectF()
        override fun onDraw(c: Canvas) {
            val s = width.toFloat(); val cx = s / 2; val cy = s / 2
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.04f; p.color = withAlpha(skin.accent, 0xAA); c.drawCircle(cx, cy, s * 0.46f, p)
            p.strokeWidth = s * 0.06f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = Color.WHITE
            when (kind) {
                0 -> {
                    rect.set(cx - s * 0.1f, cy - s * 0.2f, cx + s * 0.28f, cy + s * 0.2f); c.drawRoundRect(rect, s * 0.04f, s * 0.04f, p)
                    c.drawLine(cx - s * 0.32f, cy, cx + s * 0.02f, cy, p)
                    c.drawLine(cx - s * 0.12f, cy - s * 0.1f, cx + s * 0.02f, cy, p); c.drawLine(cx - s * 0.12f, cy + s * 0.1f, cx + s * 0.02f, cy, p)
                }
                1 -> {
                    rect.set(cx - s * 0.26f, cy - s * 0.2f, cx + s * 0.26f, cy + s * 0.2f); c.drawRoundRect(rect, s * 0.04f, s * 0.04f, p)
                    for (i in 1..2) {
                        val r = s * 0.12f * i
                        rect.set(cx - s * 0.26f - r, cy + s * 0.2f - r, cx - s * 0.26f + r, cy + s * 0.2f + r); c.drawArc(rect, 270f, 90f, false, p)
                    }
                }
                2 -> {
                    p.style = Paint.Style.FILL
                    path.reset(); path.moveTo(cx, cy - s * 0.3f); path.lineTo(cx + s * 0.07f, cy - s * 0.07f); path.lineTo(cx + s * 0.3f, cy)
                    path.lineTo(cx + s * 0.07f, cy + s * 0.07f); path.lineTo(cx, cy + s * 0.3f); path.lineTo(cx - s * 0.07f, cy + s * 0.07f)
                    path.lineTo(cx - s * 0.3f, cy); path.lineTo(cx - s * 0.07f, cy - s * 0.07f); path.close(); c.drawPath(path, p)
                }
                3 -> {
                    for (i in 0..1) for (j in 0..1) {
                        val x = cx - s * 0.24f + i * s * 0.26f; val y = cy - s * 0.24f + j * s * 0.26f
                        rect.set(x, y, x + s * 0.22f, y + s * 0.22f); c.drawRoundRect(rect, s * 0.04f, s * 0.04f, p)
                    }
                }
                4 -> {
                    p.style = Paint.Style.FILL
                    path.reset(); path.moveTo(cx + s * 0.06f, cy - s * 0.3f); path.lineTo(cx - s * 0.14f, cy + s * 0.04f); path.lineTo(cx - s * 0.01f, cy + s * 0.04f)
                    path.lineTo(cx - s * 0.06f, cy + s * 0.3f); path.lineTo(cx + s * 0.14f, cy - s * 0.06f); path.lineTo(cx + s * 0.01f, cy - s * 0.06f); path.close()
                    c.drawPath(path, p)
                }
                else -> {
                    c.drawCircle(cx, cy, s * 0.11f, p)
                    for (i in 0 until 8) {
                        val a = Math.PI * i / 4
                        c.drawLine(cx + (s * 0.2f * Math.cos(a)).toFloat(), cy + (s * 0.2f * Math.sin(a)).toFloat(),
                            cx + (s * 0.3f * Math.cos(a)).toFloat(), cy + (s * 0.3f * Math.sin(a)).toFloat(), p)
                    }
                }
            }
        }
    }
 
    private fun roundBtn(kind: Int, label: String, onClick: () -> Unit): View {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(10), dp(8), dp(10), dp(6)) }
        val cb = CircleBtn(this, kind)
        v.addView(cb, LinearLayout.LayoutParams(dp(48), dp(48)))
        v.addView(TextView(this).apply { text = label; textSize = 11f; typeface = body(); setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
        return focusable(v, 30, onClick, null, cb, 1.15f)
    }
 
    private fun consoleTile(ri: ResolveInfo, info: TextView): View {
        val pkg = ri.activityInfo.packageName; val label = ri.loadLabel(packageManager).toString()
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val iv = ImageView(this).apply { setImageDrawable(ri.loadIcon(packageManager)) }
        v.addView(iv, LinearLayout.LayoutParams(dp(76), dp(76)))
        return focusable(v, 26, { goPkg(pkg) }, { appMenu(pkg, label) }, iv, 1.2f) { f -> if (f) info.text = label }
    }
 
    private fun buildConsole() {
        val hidden = getSet("hidden"); val favs = getSet("fav")
        val visible = apps().filter { it.activityInfo.packageName !in hidden }
        val favApps = visible.filter { it.activityInfo.packageName in favs }
        val restApps = visible.filter { it.activityInfo.packageName !in favs }
        ramG = null; diskG = null; netG = null; tempG = null; statusRow = null; weatherView = null
        mediaBox = null; heroBox = null; clusterV = null; segV = null; sportV = null; orbV = null; wxIcon = null
        sysInfo = null; wxBig = null; wxDesc2 = null; wxDetail = null; wxTom = null; indicators.clear()
        val root = baseRoot(intArrayOf(0x55000000, 0x22000000, 0xDD000000.toInt()))
 
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(40), dp(12), dp(40), dp(8)) }
        val nm = userName()
        top.addView(TextView(this).apply {
            text = if (nm.isEmpty()) "•" else nm.substring(0, 1).uppercase(); textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF05050A.toInt()); gravity = Gravity.CENTER; background = oval(skin.accent)
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        top.addView(TextView(this).apply {
            text = greetingText(); textSize = 16f; typeface = body(); setTextColor(Color.WHITE); setShadowLayer(6f, 0f, 2f, Color.BLACK); setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(indicatorRow(), LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(18) })
        if (flag("wg_weather")) {
            weatherView = TextView(this).apply { textSize = 16f; typeface = body(); setTextColor(skin.accent); setShadowLayer(4f, 0f, 1f, Color.BLACK) }
            top.addView(weatherView, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(16) })
        }
        top.addView(clockView())
        root.addView(top, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
 
        val info = TextView(this).apply {
            textSize = 30f; typeface = disp(); setTextColor(Color.WHITE); setShadowLayer(8f, 0f, 2f, Color.BLACK); maxLines = 1; setPadding(dp(8), 0, 0, 0)
            text = (favApps.firstOrNull() ?: restApps.firstOrNull())?.loadLabel(packageManager)?.toString() ?: ""
        }
        val tiles = LinearLayout(this)
        (favApps + restApps.take(10)).forEach { tiles.addView(consoleTile(it, info), LinearLayout.LayoutParams(dp(132), dp(132)).apply { rightMargin = dp(12) }) }
        val gi = GridIcon(this)
        val all = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        all.addView(gi, LinearLayout.LayoutParams(dp(60), dp(60)))
        tiles.addView(focusable(all, 26, { drawer() }, null, gi, 1.2f) { f -> if (f) info.text = "Todas las apps" }, LinearLayout.LayoutParams(dp(132), dp(132)))
        val area = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        area.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(dp(8), dp(12), dp(8), dp(12)); addView(tiles)
        })
        area.addView(info)
        area.addView(TextView(this).apply { text = "Mantén OK sobre una app para fijarla u ocultarla"; textSize = 12f; typeface = body(); setTextColor(0xFFD6D0E0.toInt()); setPadding(dp(8), dp(2), 0, 0) })
        val aw = resources.displayMetrics.widthPixels - dp(40 + 40 + 330)
        root.addView(area, FrameLayout.LayoutParams(aw, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(dp(32), dp(62), 0, 0) })
 
        val rc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (flag("wg_gauges")) {
            val card = LinearLayout(this).apply { setPadding(dp(14), dp(8), dp(14), dp(8)); background = dark(26) }
            segV = SegView(this); card.addView(segV, LinearLayout.LayoutParams(dp(300), dp(124)))
            rc.addView(card)
        }
        if (flag("wg_media")) rc.addView(mediaCard(), LinearLayout.LayoutParams(dp(328), WRAP).apply { topMargin = dp(10) })
        root.addView(rc, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(62), dp(36), 0) })
 
        root.addView(makeHero(), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply { bottomMargin = dp(124) })
 
        val btns = LinearLayout(this).apply { gravity = Gravity.CENTER; background = dark(38); setPadding(dp(14), dp(4), dp(14), dp(4)) }
        fun bg() = LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(4); rightMargin = dp(4) }
        btns.addView(roundBtn(0, "Entrada") { inputPanel() }, bg())
        btns.addView(roundBtn(1, "Compartir") {
            val ok = listOf("com.homwee.multiscreenshare", "com.homwee.mmp.screenshare").firstOrNull { goPkg(it) }
            if (ok == null) toast("No disponible en esta TV")
        }, bg())
        btns.addView(roundBtn(2, "Asistente") { assistantPanel() }, bg())
        btns.addView(roundBtn(3, "Paneles") { panelsOverlay() }, bg())
        btns.addView(roundBtn(4, "Optimizar") { optimize() }, bg())
        btns.addView(roundBtn(5, "Ajustes") { customPanel() }, bg())
        root.addView(btns, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(14) })
 
        addOverlays(root, Gravity.TOP or Gravity.CENTER_HORIZONTAL, false)
        val fx = seasonFx()
        if (fx.isNotEmpty()) root.addView(SeasonView(this, fx), FrameLayout.LayoutParams(MATCH, MATCH))
        if (!splashShown && flag("splash")) { splashShown = true; root.addView(splashView(), FrameLayout.LayoutParams(MATCH, MATCH)) }
        sfxReady = false; handler.postDelayed({ sfxReady = true }, 900)
        setContentView(root)
        tiles.getChildAt(0)?.requestFocus()
        updateCaption(); refreshStats(); renderWeather()
        if (flag("wg_weather")) { lastWx = 0L; fetchWeather() }
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
        val wrap = FrameLayout(this).apply { setBackgroundColor(0xB8000000.toInt()) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(34), dp(24), dp(34), dp(26))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xF2221E34.toInt(), 0xF20D0B16.toInt())).apply {
                cornerRadius = dp(30).toFloat(); setStroke(dp(2), withAlpha(skin.accent, 0x88))
            }
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(8)) }
        head.addView(View(this).apply { setBackgroundColor(skin.accent) }, LinearLayout.LayoutParams(dp(5), dp(30)).apply { rightMargin = dp(14) })
        head.addView(TextView(this).apply {
            text = title.uppercase(); textSize = 24f; letterSpacing = 0.18f; typeface = disp(); setTextColor(Color.WHITE)
        })
        box.addView(head)
        if (note != null) box.addView(TextView(this).apply {
            text = note; textSize = 13f; typeface = body(); setTextColor(0xFFB0A9BC.toInt()); setPadding(dp(4), 0, dp(4), dp(8))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        var first: View? = null
        items.forEach { o ->
            val idx = o.label.indexOf(": ")
            val name = if (idx > 0) o.label.substring(0, idx) else o.label
            val value: String? = if (idx > 0) o.label.substring(idx + 2) else null
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(22), dp(12), dp(22), dp(12)) }
            row.addView(TextView(this).apply { text = name; textSize = 18f; typeface = body(); setTextColor(Color.WHITE) },
                LinearLayout.LayoutParams(0, WRAP, 1f))
            if (value != null) row.addView(TextView(this).apply {
                text = value; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(skin.accent)
                background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(withAlpha(skin.accent, 0x26)) }
                setPadding(dp(14), dp(5), dp(14), dp(5))
            })
            else if (name.endsWith("…")) row.addView(TextView(this).apply { text = "›"; textSize = 22f; setTextColor(skin.accent) })
            val v = focusable(row, 22, { d.dismiss(); o.run() })
            if (first == null) first = v
            list.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8) })
        }
        box.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; addView(list) })
        wrap.addView(box, FrameLayout.LayoutParams(dp(620), WRAP, Gravity.CENTER))
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
    private fun claudeKey(): String? {
        val f = File(getExternalFilesDir(null), "claude.txt")
        return if (f.exists()) readLinesRobust(f).firstOrNull() else null
    }
 
    private fun gptConf(): Pair<String, String>? {
        val f = File(getExternalFilesDir(null), "openai.txt")
        if (!f.exists()) return null
        val lines = readLinesRobust(f)
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
 
    private fun toggleOff(key: String, label: String): Opt =
        Opt("$label: ${onOff(opt(key))}") { prefs.edit().putBoolean(key, !opt(key)).apply(); build(); widgetsPanel() }
 
    private fun toggle(key: String, label: String): Opt =
        Opt("$label: ${onOff(flag(key))}") { prefs.edit().putBoolean(key, !flag(key)).apply(); build(); widgetsPanel() }
 
    private fun widgetsPanel() {
        panel("Widgets", listOf(
            toggle("wg_gauges", "Medidores (RAM, disco, red)"),
            toggle("wg_status", "Estado de entradas y Bluetooth"),
            toggle("wg_weather", "Clima"),
            toggle("wg_media", "Reproductor de música"),
            toggle("wg_hud", "Etiquetas rápidas (RAM 76%)"),
            toggle("wg_info", "Fila de información (clima y sistema)"),
            toggleOff("wg_aclock", "  Reloj analógico"),
            toggleOff("wg_words", "  Reloj en palabras"),
            toggleOff("wg_cal", "  Calendario"),
            toggleOff("wg_sky", "  Sol y aire"),
            toggleOff("wg_netg", "  Tráfico de red"),
            toggleOff("wg_tprog", "  Progreso del tiempo"),
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
 
    // ---------- archivos de texto y nombre ----------
    private fun readTextRobust(f: File): String {
        val b = f.readBytes()
        val cs = when {
            b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(b, cs).replace("\uFEFF", "").replace("\u0000", "")
    }
 
    private fun readLinesRobust(f: File): List<String> = try {
        readTextRobust(f).lines().map { it.trim() }.filter { it.isNotEmpty() }
    } catch (e: Throwable) { emptyList() }
 
    private fun userName(): String {
        val saved = prefs.getString("uname", "") ?: ""
        if (saved.isNotBlank()) return saved.trim()
        val f = File(getExternalFilesDir(null), "nombre.txt")
        return if (f.exists()) (readLinesRobust(f).firstOrNull() ?: "") else ""
    }
 
    private fun greetingText(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hi = if (h < 12) "Buenos días" else if (h < 19) "Buenas tardes" else "Buenas noches"
        val nm = userName()
        return if (nm.isEmpty()) hi else "$hi, $nm"
    }
 
    private fun askName() {
        val et = EditText(this).apply { setText(userName()); hint = "Tu nombre"; isSingleLine = true }
        AlertDialog.Builder(this).setTitle("¿Cómo te llamas?").setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit().putString("uname", et.text.toString().trim()).apply(); build()
                val mr = menuRefresh; if (mr != null) mr() else customPanel()
            }
            .setNegativeButton("Cancelar", null).show()
    }
 
    private fun replaySplash() {
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        content.addView(splashView(), FrameLayout.LayoutParams(MATCH, MATCH))
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
                LinearLayout.LayoutParams(dp(520), dp(320)))
        } else {
            val ring = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0); setStroke(dp(3), skin.accent) }
                scaleX = 0.5f; scaleY = 0.5f
            }
            col.addView(ring, LinearLayout.LayoutParams(dp(150), dp(150)))
            ring.animate().scaleX(1f).scaleY(1f).setDuration(2200).start()
        }
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hi = if (h < 12) "Buenos días" else if (h < 19) "Buenas tardes" else "Buenas noches"
        val nm = userName()
        col.addView(TextView(this).apply {
            text = if (nm.isEmpty()) hi else "$hi, $nm"; textSize = 58f; typeface = light()
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(22), 0, dp(6))
        })
        col.addView(TextView(this).apply { text = "MI LAUNCHER"; textSize = 16f; letterSpacing = 0.5f; setTextColor(skin.accent) })
        f.addView(col, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        col.alpha = 0f
        col.animate().alpha(1f).setDuration(900).start()
        handler.postDelayed({ sfx(2, true) }, 400)
        handler.postDelayed({
            f.animate().alpha(0f).setDuration(700).withEndAction { (f.parent as? ViewGroup)?.removeView(f) }.start()
        }, 5200)
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
            Opt("Diseño: ${layoutNames[prefs.getInt("layout", 0) % 3]}") { prefs.edit().putInt("layout", prefs.getInt("layout", 0) + 1).apply(); build(); stylePanel() },
            Opt("Tema de color: ${skin.name}") { prefs.edit().putInt("theme", prefs.getInt("theme", 0) + 1).apply(); build(); stylePanel() },
            Opt("Tipografía: ${fontNames[fontIdx()]}") { prefs.edit().putInt("font", prefs.getInt("font", 0) + 1).apply(); build(); stylePanel() },
            Opt("Tarjetas: ${cardNames[prefs.getInt("cstyle", 0) % 7]}") { prefs.edit().putInt("cstyle", prefs.getInt("cstyle", 0) + 1).apply(); build(); stylePanel() },
            Opt("Temporada: ${seasonNames[prefs.getInt("season", 0) % 6]}") { prefs.edit().putInt("season", prefs.getInt("season", 0) + 1).apply(); build(); stylePanel() },
            Opt("Tamaño de iconos: ${sizeNames[prefs.getInt("size", 1) % 3]}") { prefs.edit().putInt("size", prefs.getInt("size", 1) + 1).apply(); build(); stylePanel() },
            Opt("Barra y widgets…") { barPanel() },
            Opt("Aplicar diseño recomendado") {
                prefs.edit().putInt("layout", 1).putInt("theme", 2).putInt("font", 0).putInt("cstyle", 0).putInt("gstyle", 4).putInt("wxs", 1).apply()
                build(); stylePanel()
            },
            Opt("Volver") { customPanel() }
        ), "Recomendado: Aurora Dock con tema Aurora, tipografía Moderna y medidor Orbital.")
    }
 
    private fun barPanel() {
        panel("Barra y widgets", listOf(
            Opt("Barra superior: ${barNames[prefs.getInt("bar", 0) % 3]}") { prefs.edit().putInt("bar", prefs.getInt("bar", 0) + 1).apply(); build(); barPanel() },
            Opt("Reloj: ${clkNames[prefs.getInt("clk", 0) % 5]}") { prefs.edit().putInt("clk", prefs.getInt("clk", 0) + 1).apply(); build(); barPanel() },
            Opt("Clima en la barra: ${wxsNames[prefs.getInt("wxs", 0) % 4]}") { prefs.edit().putInt("wxs", prefs.getInt("wxs", 0) + 1).apply(); build(); barPanel() },
            Opt("Medidores: ${gNames[prefs.getInt("gstyle", 0) % 5]}") { prefs.edit().putInt("gstyle", prefs.getInt("gstyle", 0) + 1).apply(); build(); barPanel() },
            Opt("Reproductor de música: ${mstyleNames[prefs.getInt("mstyle", 0) % 5]}") { prefs.edit().putInt("mstyle", prefs.getInt("mstyle", 0) + 1).apply(); build(); barPanel() },
            Opt("Widgets…") { widgetsPanel() },
            Opt("Volver") { stylePanel() }
        ))
    }
 
    private fun customPanel() { if (prefs.getInt("menu", 0) % 2 == 1) gameMenu() else customList() }
 
    private fun customList() {
        panel("Personalizar", listOf(
            Opt("Estilo del menú: ${menuNames[prefs.getInt("menu", 0) % 2]}") { prefs.edit().putInt("menu", prefs.getInt("menu", 0) + 1).apply(); customPanel() },
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
            Opt("Tu nombre: ${userName().ifEmpty { "sin definir" }}") { askName() },
            toggle("splash", "Pantalla de bienvenida"),
            toggle("splash_wake", "Bienvenida al despertar la TV"),
            Opt("Probar bienvenida") { replaySplash() },
            toggle("volbar", "Barra de volumen neón"),
            Opt("Probar barra de volumen") { showVolume() },
            toggle("tts", "Voz del asistente"),
            Opt("Bloqueo de anuncios por DNS: ${onOff(dnsOn())}") { toggleDns() },
            Opt("Mostrar apps ocultas") { putSet("hidden", emptySet()); build() },
            Opt("Ajustes de la TV") { go(Intent(Settings.ACTION_SETTINGS)) }
        ))
    }
}
 
class MediaListener : android.service.notification.NotificationListenerService()
 
