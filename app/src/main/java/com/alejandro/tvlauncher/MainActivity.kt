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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
 
class MainActivity : Activity() {
    private class Skin(val name: String, val top: Int, val bottom: Int, val accent: Int)
    private class Opt(val label: String, val run: () -> Unit)
 
    private val skins = listOf(
        Skin("Ámbar", 0xFF2B1B10.toInt(), 0xFF0F0E17.toInt(), 0xFFFFB347.toInt()),
        Skin("Océano", 0xFF0B2233.toInt(), 0xFF070C14.toInt(), 0xFF4DD0E1.toInt()),
        Skin("Aurora", 0xFF1A1233.toInt(), 0xFF0A0A14.toInt(), 0xFFB388FF.toInt()),
        Skin("Sakura", 0xFF2E1424.toInt(), 0xFF0E0A12.toInt(), 0xFFFF7EB6.toInt())
    )
    private val sizeNames = arrayOf("Pequeño", "Medio", "Grande")
    private val sizeScale = floatArrayOf(0.85f, 1f, 1.2f)
    private val autoMins = intArrayOf(0, 5, 15, 30)
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val knownFavs = setOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
        "com.wbd.stream", "com.spotify.tv.android", "com.univision.prendetv")
 
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var sig = ""
    private var wallBmp: Bitmap? = null
    private var wallView: ImageView? = null
    private var ramG: GaugeView? = null
    private var diskG: GaugeView? = null
    private var netG: GaugeView? = null
    private var statusRow: LinearLayout? = null
    private var weatherView: TextView? = null
    private var lastWx = 0L
 
    private val skin get() = skins[prefs.getInt("theme", 0) % skins.size]
    private val scale get() = sizeScale[prefs.getInt("size", 1) % 3]
    private fun flag(k: String) = prefs.getBoolean(k, true)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun light() = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private fun getSet(k: String): Set<String> = prefs.getStringSet(k, emptySet())!!.toSet()
    private fun putSet(k: String, s: Set<String>) = prefs.edit().putStringSet(k, s).apply()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
 
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("p", 0)
        if (!prefs.contains("fav")) putSet("fav", knownFavs)
        getExternalFilesDir("fondos")
    }
 
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
 
    private val tick = object : Runnable { override fun run() { refreshStats(); handler.postDelayed(this, 2500) } }
    private val wallTask = object : Runnable { override fun run() { nextWall(); scheduleWall() } }
 
    private fun go(i: Intent, quiet: Boolean = false): Boolean = try {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { if (!quiet) toast("No se pudo abrir"); false }
 
    private fun goPkg(p: String): Boolean {
        val i = packageManager.getLeanbackLaunchIntentForPackage(p) ?: packageManager.getLaunchIntentForPackage(p) ?: return false
        return go(i)
    }
 
    private fun apps(): List<ResolveInfo> {
        val pm = packageManager
        return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }
 
    // ---------- fondos ----------
    private fun wallFiles(): List<File> =
        getExternalFilesDir("fondos")?.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            ?.sortedBy { it.name } ?: emptyList()
 
    private fun decode(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 1280 && o.outHeight / (s * 2) >= 720) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Throwable) { null }
 
    private fun loadWall(f: File, animate: Boolean) {
        Thread {
            val b = decode(f)
            runOnUiThread { if (b != null) { wallBmp = b; applyWall(animate) } }
        }.start()
    }
 
    private fun applyWall(animate: Boolean) {
        val v = wallView ?: return
        if (!animate) { v.setImageBitmap(wallBmp); return }
        v.animate().alpha(0f).setDuration(250).withEndAction {
            v.setImageBitmap(wallBmp); v.animate().alpha(1f).setDuration(400).start()
        }.start()
    }
 
    private fun nextWall() {
        val files = wallFiles(); if (files.isEmpty()) return
        val idx = (prefs.getInt("wall", -1) + 1) % files.size
        prefs.edit().putInt("wall", idx).apply()
        loadWall(files[idx], true)
    }
 
    private fun scheduleWall() {
        handler.removeCallbacks(wallTask)
        val m = autoMins[prefs.getInt("auto", 0) % autoMins.size]
        if (m > 0) handler.postDelayed(wallTask, m * 60_000L)
    }
 
    // ---------- clima ----------
    private fun wxDesc(c: Int) = when (c) {
        0 -> "Despejado"; 1, 2 -> "Parcialmente nublado"; 3 -> "Nublado"; 45, 48 -> "Niebla"
        in 51..57 -> "Llovizna"; in 61..67, in 80..82 -> "Lluvia"; in 71..77, 85, 86 -> "Nieve"
        in 95..99 -> "Tormenta"; else -> ""
    }
 
    private fun fetchWeather() {
        lastWx = System.currentTimeMillis()
        Thread {
            try {
                val lat = prefs.getFloat("lat", 19.93f); val lon = prefs.getFloat("lon", -96.85f)
                val c = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
                val t = Math.round(j.getDouble("temperature_2m"))
                val txt = "$t° · ${wxDesc(j.getInt("weather_code"))}"
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
        private val tickP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF; strokeWidth = 2f }
        private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND }
        private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        private val lab = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB9B2C4.toInt(); textAlign = Paint.Align.CENTER }
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
        row.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(if (on) Color.WHITE else 0xFF9A93A8.toInt()) })
        row.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(14) }
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
    private fun glass(focused: Boolean, radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(if (focused) 0x44FFFFFF else 0x26000000)
        setStroke(dp(if (focused) 3 else 1), if (focused) skin.accent else 0x33FFFFFF)
    }
 
    private fun focusable(v: View, radius: Int, grow: Float, onClick: () -> Unit, onLong: (() -> Unit)? = null): View {
        v.isFocusable = true; v.isClickable = true; v.clipToOutline = true
        fun paint(f: Boolean) {
            v.background = glass(f, radius)
            v.scaleX = if (f) grow else 1f; v.scaleY = v.scaleX
            v.elevation = if (f) dp(10).toFloat() else 0f
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
        if (icon != null) v.addView(ImageView(this).apply { setImageDrawable(icon) },
            LinearLayout.LayoutParams(dp((56 * s).toInt()), dp((56 * s).toInt())).apply { bottomMargin = dp(8) })
        v.addView(TextView(this).apply {
            text = label; textSize = 14f * s; setTextColor(Color.WHITE); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        })
        return focusable(v, 22, 1.08f, onClick, onLong)
    }
 
    private fun wideTile(ri: ResolveInfo): View {
        val pm = packageManager; val pkg = ri.activityInfo.packageName; val label = ri.loadLabel(pm).toString()
        val banner = try { ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm) } catch (e: Throwable) { null }
        if (banner == null) return tile(label, ri.loadIcon(pm), { goPkg(pkg) }, { appMenu(pkg, label) })
        val f = FrameLayout(this).apply { setPadding(dp(3), dp(3), dp(3), dp(3)) }
        f.addView(ImageView(this).apply { setImageDrawable(banner); scaleType = ImageView.ScaleType.CENTER_CROP },
            FrameLayout.LayoutParams(MATCH, MATCH))
        return focusable(f, 22, 1.08f, { goPkg(pkg) }, { appMenu(pkg, label) })
    }
 
    private fun appTile(ri: ResolveInfo): View {
        val pkg = ri.activityInfo.packageName; val label = ri.loadLabel(packageManager).toString()
        return tile(label, ri.loadIcon(packageManager), { goPkg(pkg) }, { appMenu(pkg, label) })
    }
 
    private fun chip(label: String, onClick: () -> Unit): View {
        val t = TextView(this).apply {
            text = label; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        return focusable(t, 30, 1.06f, onClick)
    }
 
    private fun section(txt: String) = TextView(this).apply {
        text = txt.uppercase(); textSize = 13f; letterSpacing = 0.25f
        setTextColor(skin.accent); setPadding(dp(6), dp(22), 0, dp(4))
    }
 
    private fun hrow(content: View) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false; clipToPadding = false
        setPadding(dp(14), dp(14), dp(14), dp(14)); addView(content)
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
        wallView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; if (wallBmp != null) setImageBitmap(wallBmp) }
        root.addView(wallView, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x55000000, 0xAA000000.toInt()))
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        if (wallBmp == null) {
            val files = wallFiles()
            if (files.isNotEmpty()) loadWall(files[prefs.getInt("wall", 0).coerceIn(0, files.size - 1)], false)
        }
 
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(56), dp(26), dp(56), dp(10)) }
 
        // cabecera: izquierda reloj + clima + botones, derecha medidores
        val head = LinearLayout(this).apply { gravity = Gravity.TOP }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "h:mm"; textSize = 54f; typeface = light(); setTextColor(Color.WHITE)
        })
        left.addView(TextClock(this).apply {
            format24Hour = "EEEE d 'de' MMMM"; format12Hour = "EEEE d 'de' MMMM"; textSize = 16f; setTextColor(0xFFD6D0E0.toInt())
        })
        if (flag("wg_weather")) {
            weatherView = TextView(this).apply { textSize = 18f; setTextColor(skin.accent); setPadding(0, dp(2), 0, 0) }
            left.addView(weatherView)
        }
        val chips = LinearLayout(this).apply { setPadding(0, dp(12), 0, 0) }
        fun gap() = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(12) }
        chips.addView(chip("Entrada") { inputPanel() }, gap())
        chips.addView(chip("Compartir") {
            val ok = listOf("com.homwee.multiscreenshare", "com.homwee.mmp.screenshare").firstOrNull { goPkg(it) }
            if (ok == null) toast("No disponible en esta TV")
        }, gap())
        chips.addView(chip("Personalizar") { customPanel() }, gap())
        left.addView(chips)
        head.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
 
        if (flag("wg_gauges") || flag("wg_status")) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10))
                background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(0x40000000); setStroke(dp(1), 0x33FFFFFF) }
            }
            if (flag("wg_gauges")) {
                val g = LinearLayout(this)
                ramG = gauge("RAM"); diskG = gauge("DISCO"); netG = gauge("RED")
                listOf(ramG!!, diskG!!, netG!!).forEach { g.addView(it, LinearLayout.LayoutParams(dp(104), dp(104))) }
                card.addView(g)
            }
            if (flag("wg_status")) {
                statusRow = LinearLayout(this).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(4), 0, 0) }
                card.addView(statusRow, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            head.addView(card, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        col.addView(head)
 
        // cuerpo
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(section("Favoritas"))
        if (favApps.isEmpty()) {
            body.addView(TextView(this).apply {
                text = "Mantén pulsado OK sobre una app para fijarla aquí"
                textSize = 15f; setTextColor(0xFFD6D0E0.toInt()); setPadding(dp(8), dp(14), 0, dp(14))
            })
        } else {
            val r = LinearLayout(this)
            favApps.forEach { r.addView(wideTile(it), LinearLayout.LayoutParams(dp((240 * s).toInt()), dp((135 * s).toInt())).apply { rightMargin = dp(16) }) }
            body.addView(hrow(r))
        }
        body.addView(section("Todas las apps"))
        val grid = GridLayout(this).apply { orientation = GridLayout.VERTICAL; rowCount = 2 }
        restApps.forEach {
            grid.addView(appTile(it), GridLayout.LayoutParams().apply { width = tileW; height = tileH; setMargins(dp(7), dp(7), dp(7), dp(7)) })
        }
        body.addView(hrow(grid))
 
        col.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false; setPadding(0, 0, 0, dp(30)); addView(body)
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))
 
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        chips.getChildAt(0)?.requestFocus()
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
            text = note; textSize = 12f; setTextColor(0xFF9A93A8.toInt()); setPadding(dp(4), 0, 0, dp(8))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        var first: View? = null
        items.forEach { o ->
            val t = TextView(this).apply {
                text = o.label; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(22), dp(13), dp(22), dp(13))
            }
            val v = focusable(t, 22, 1.03f, { d.dismiss(); o.run() })
            if (first == null) first = v
            list.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(9) })
        }
        box.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; addView(list) })
        wrap.addView(box, FrameLayout.LayoutParams(dp(520), WRAP, Gravity.CENTER))
        d.setContentView(wrap)
        d.window?.setLayout(MATCH, MATCH)
        d.show()
        first?.requestFocus()
    }
 
    private fun labelOf(i: TvInputInfo): String =
        i.loadCustomLabel(this)?.toString()?.takeIf { it.isNotBlank() } ?: i.loadLabel(this)?.toString() ?: "Entrada"
 
    private fun inputPanel() {
        val inputs = try {
            (getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager).tvInputList.filter { it.isPassthroughInput }
        } catch (e: Exception) { emptyList() }
        val items = mutableListOf<Opt>()
        items.add(Opt("TV en vivo") {
            if (!go(Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI), true)) {
                if (!goPkg("com.mediatek.wwtv.tvcenter")) toast("No se pudo abrir la TV")
            }
        })
        inputs.sortedBy { labelOf(it) }.forEach { inp ->
            items.add(Opt(labelOf(inp)) { go(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inp.id))) })
        }
        panel("Entrada", items, if (inputs.isEmpty()) "No se detectaron entradas HDMI/AV. Usa el botón de entrada del control." else null)
    }
 
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
 
    private fun customPanel() {
        val n = wallFiles().size
        val items = listOf(
            Opt("Tema: ${skin.name}") { prefs.edit().putInt("theme", prefs.getInt("theme", 0) + 1).apply(); build(); customPanel() },
            Opt(if (n == 0) "Fondo: sin imágenes (ver nota)" else "Fondo: siguiente ($n disponibles)") { nextWall(); customPanel() },
            Opt("Cambio automático: ${autoMins[prefs.getInt("auto", 0) % 4].let { if (it == 0) "No" else "cada $it min" }}") {
                prefs.edit().putInt("auto", prefs.getInt("auto", 0) + 1).apply(); scheduleWall(); customPanel()
            },
            Opt("Tamaño de iconos: ${sizeNames[prefs.getInt("size", 1) % 3]}") { prefs.edit().putInt("size", prefs.getInt("size", 1) + 1).apply(); build(); customPanel() },
            Opt("Widgets…") { widgetsPanel() },
            Opt("Mostrar apps ocultas") { putSet("hidden", emptySet()); build() },
            Opt("Ajustes de la TV") { go(Intent(Settings.ACTION_SETTINGS)) }
        )
        panel("Personalizar", items,
            "Fondos: adb push imagen.jpg /sdcard/Android/data/com.alejandro.tvlauncher/files/fondos/")
    }
}
 
