package com.alejandro.tvlauncher
 
import android.animation.ValueAnimator
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.net.HttpURLConnection
import java.net.URL
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
    private var ramG: GaugeView? = null
    private var diskG: GaugeView? = null
    private var netG: GaugeView? = null
    private var statusRow: LinearLayout? = null
    private var weatherView: TextView? = null
    private var captionView: TextView? = null
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
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) tts?.language = Locale("es", "MX") }
    }
 
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
 
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
 
    private val tick = object : Runnable { override fun run() { refreshStats(); handler.postDelayed(this, 2500) } }
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
        if (!animate) { put(); return }
        layer.animate().alpha(0f).setDuration(250).withEndAction {
            put(); layer.animate().alpha(1f).setDuration(500).start()
        }.start()
    }
 
    private fun updateCaption() {
        val c = captionView ?: return
        val w = curWall
        if (prefs.getInt("wallmode", 0) == 1 && w?.title != null) {
            c.text = "Ver en ${w.prov}: ${w.title}"
            c.visibility = View.VISIBLE
            val p = w.pkg
            captionAction = { if (p != null) goPkg(p) }
        } else c.visibility = View.GONE
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
                val j = JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto")).getJSONObject("current")
                val txt = "${Math.round(j.getDouble("temperature_2m"))}° · ${wxDesc(j.getInt("weather_code"))}"
                runOnUiThread { weatherView?.text = txt }
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
            val sw = w * 0.075f; val r = w / 2 - sw * 1.5f
            track.strokeWidth = sw; prog.strokeWidth = sw
            rect.set(cx - r, cy - r, cx + r, cy + r)
            c.drawArc(rect, 150f, 240f, false, track)
            prog.color = if (warn) 0xFFFF6B6B.toInt() else skin.accent
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
 
    @Suppress("DEPRECATION")
    private fun refreshStats() {
        ramG?.let {
            val mi = ActivityManager.MemoryInfo()
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            val used = (mi.totalMem - mi.availMem).toFloat() / mi.totalMem
            it.set(used, "${(used * 100).toInt()}%", used > 0.85f)
        }
        diskG?.let {
            try {
                val st = StatFs(Environment.getDataDirectory().path)
                val used = (st.totalBytes - st.availableBytes).toFloat() / st.totalBytes
                it.set(used, "${(used * 100).toInt()}%", used > 0.9f)
            } catch (e: Throwable) { }
        }
        netG?.let {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                when {
                    caps == null -> it.set(0f, "OFF", true)
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> it.set(1f, "LAN")
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        val pct = ((wm.connectionInfo.rssi + 100) * 2).coerceIn(0, 100) / 100f
                        it.set(pct, "${(pct * 100).toInt()}%", pct < 0.3f)
                    }
                    else -> it.set(0.5f, "RED")
                }
            } catch (e: Throwable) { }
        }
        statusRow?.let { fillStatus(it) }
    }
 
    // ---------- piezas de interfaz ----------
    private fun dark(r: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat(); setColor(0xB0080810.toInt()); setStroke(dp(1), 0x22FFFFFF)
    }
 
    // cursor: relleno del color del tema + borde grueso; los iconos "saltan" por dentro sin salirse del cuadro
    private fun glass(focused: Boolean, radius: Int): GradientDrawable {
        val cols = if (focused) intArrayOf(withAlpha(skin.accent, 0xB0), withAlpha(skin.accent, 0x50))
                   else intArrayOf(0xD014121C.toInt(), 0xD014121C.toInt())
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, cols).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(if (focused) 3 else 1), if (focused) skin.accent else 0x44FFFFFF)
        }
    }
 
    private fun focusable(v: View, radius: Int, onClick: () -> Unit, onLong: (() -> Unit)? = null, pop: View? = null, popScale: Float = 1.18f): View {
        v.isFocusable = true; v.isClickable = true; v.clipToOutline = true
        fun paint(f: Boolean) {
            v.background = glass(f, radius)
            pop?.animate()?.scaleX(if (f) popScale else 1f)?.scaleY(if (f) popScale else 1f)?.setDuration(180)?.start()
        }
        paint(false)
        v.setOnFocusChangeListener { _, f -> paint(f) }
        v.setOnClickListener { onClick() }
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
    private fun build() {
        val hidden = getSet("hidden"); val favs = getSet("fav")
        val visible = apps().filter { it.activityInfo.packageName !in hidden }
        val favApps = visible.filter { it.activityInfo.packageName in favs }
        val restApps = visible.filter { it.activityInfo.packageName !in favs }
        val s = scale
        val tileW = dp((168 * s).toInt()); val tileH = dp((124 * s).toInt())
        ramG = null; diskG = null; netG = null; statusRow = null; weatherView = null
 
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
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x66000000, 0xBB000000.toInt()))
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        if (wallBmp != null) applyWall(false)
        else { val ws = walls(); if (ws.isNotEmpty()) loadWall(ws[prefs.getInt("wall", 0).coerceIn(0, ws.size - 1)], false) }
 
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(48), dp(22), dp(48), dp(8)) }
 
        // cabecera
        val head = LinearLayout(this).apply { gravity = Gravity.TOP }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(10)); background = dark(26) }
        left.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "h:mm"; textSize = 50f; typeface = light()
            setTextColor(Color.WHITE); setShadowLayer(8f, 0f, 2f, Color.BLACK)
        })
        left.addView(TextClock(this).apply {
            format24Hour = "EEEE d 'de' MMMM"; format12Hour = "EEEE d 'de' MMMM"; textSize = 16f; setTextColor(0xFFE6E0F0.toInt())
        })
        if (flag("wg_weather")) {
            weatherView = TextView(this).apply { textSize = 18f; setTextColor(skin.accent); setPadding(0, dp(2), 0, 0) }
            left.addView(weatherView)
        }
        val cap = TextView(this).apply {
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(16), dp(8), dp(16), dp(8)); visibility = View.GONE
        }
        captionView = cap
        focusable(cap, 24, { captionAction() })
        left.addView(cap, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) })
        val chips = LinearLayout(this)
        fun gap() = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(10) }
        chips.addView(chip("Entrada") { inputPanel() }, gap())
        chips.addView(chip("Compartir") {
            val ok = listOf("com.homwee.multiscreenshare", "com.homwee.mmp.screenshare").firstOrNull { goPkg(it) }
            if (ok == null) toast("No disponible en esta TV")
        }, gap())
        chips.addView(chip("Asistente") { assistantPanel() }, gap())
        chips.addView(chip("Personalizar") { customPanel() }, gap())
        left.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(0, dp(10), 0, dp(4)); addView(chips)
        })
        head.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(14) })
 
        if (flag("wg_gauges") || flag("wg_status")) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); background = dark(26) }
            if (flag("wg_gauges")) {
                val g = LinearLayout(this)
                ramG = gauge("RAM"); diskG = gauge("DISCO"); netG = gauge("RED")
                listOf(ramG!!, diskG!!, netG!!).forEach { g.addView(it, LinearLayout.LayoutParams(dp(96), dp(96))) }
                card.addView(g)
            }
            if (flag("wg_status")) {
                statusRow = LinearLayout(this).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(2), 0, 0) }
                card.addView(statusRow, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            head.addView(card, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        col.addView(head, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) })
 
        // cuerpo: estantes con fondo propio
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (favApps.isEmpty()) {
            body.addView(shelf("Favoritas", TextView(this).apply {
                text = "Mantén pulsado OK sobre una app para fijarla aquí"
                textSize = 15f; setTextColor(0xFFE6E0F0.toInt()); setPadding(dp(8), dp(12), 0, dp(12))
            }))
        } else {
            val r = LinearLayout(this)
            favApps.forEach { r.addView(wideTile(it), LinearLayout.LayoutParams(dp((240 * s).toInt()), dp((135 * s).toInt())).apply { rightMargin = dp(14) }) }
            body.addView(shelf("Favoritas", hrow(r)))
        }
        val grid = GridLayout(this).apply { orientation = GridLayout.VERTICAL; rowCount = 2 }
        restApps.forEach {
            grid.addView(appTile(it), GridLayout.LayoutParams().apply { width = tileW; height = tileH; setMargins(dp(6), dp(6), dp(6), dp(6)) })
        }
        body.addView(shelf("Todas las apps", hrow(grid)))
        body.alpha = 0f; body.translationY = dp(24).toFloat()
        body.animate().alpha(1f).translationY(0f).setDuration(450).start()
 
        col.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false; setPadding(0, 0, 0, dp(30)); addView(body)
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))
 
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        chips.getChildAt(0)?.requestFocus()
        updateCaption()
        refreshStats()
        if (flag("wg_weather")) { lastWx = 0L; fetchWeather() }
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
 
    private fun assistantPanel(note: String? = null) {
        val items = listOf(Opt("Hablar") { listen() }, Opt("Escribir") { typeQuestion() }, Opt("Cerrar") { })
        panel("Asistente", items, note ?: if (claudeKey() == null) "Falta claude.txt con tu clave de API (ver guía)."
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
        val key = claudeKey()
        if (key == null) { assistantPanel(); return }
        toast("Pensando…")
        val appsTxt = apps().joinToString(", ") { it.loadLabel(packageManager).toString() }
        val inTxt = passInputs().joinToString(", ") { labelOf(it) }
        val sys = "Eres el asistente de una TV Android. Responde SOLO con JSON válido: " +
            "{\"say\":\"respuesta breve en español\",\"action\":null} o con una acción: " +
            "{\"say\":\"...\",\"action\":{\"type\":\"open_app\" | \"input\" | \"tv\" | \"next_wall\",\"name\":\"nombre exacto\"}}. " +
            "Apps instaladas: $appsTxt. Entradas: $inTxt."
        Thread {
            val reply: String = try {
                val body = JSONObject().put("model", "claude-haiku-5-5").put("max_tokens", 300).put("system", sys)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", q)))
                val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10000; c.readTimeout = 30000
                c.setRequestProperty("x-api-key", key)
                c.setRequestProperty("anthropic-version", "2023-06-01")
                c.setRequestProperty("content-type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val ok = c.responseCode in 200..299
                val txt = (if (ok) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
                if (ok) JSONObject(txt).getJSONArray("content").getJSONObject(0).getString("text") else "Error ${c.responseCode}: revisa tu clave"
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
 
    private fun customPanel() {
        panel("Personalizar", listOf(
            Opt("Tema: ${skin.name}") { prefs.edit().putInt("theme", prefs.getInt("theme", 0) + 1).apply(); build(); customPanel() },
            Opt("Fondos…") { wallPanel() },
            Opt("Tamaño de iconos: ${sizeNames[prefs.getInt("size", 1) % 3]}") { prefs.edit().putInt("size", prefs.getInt("size", 1) + 1).apply(); build(); customPanel() },
            Opt("Widgets…") { widgetsPanel() },
            toggle("tts", "Voz del asistente"),
            Opt("Mostrar apps ocultas") { putSet("hidden", emptySet()); build() },
            Opt("Ajustes de la TV") { go(Intent(Settings.ACTION_SETTINGS)) }
        ))
    }
}
 
