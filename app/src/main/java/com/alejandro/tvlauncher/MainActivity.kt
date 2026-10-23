package com.alejandro.tvlauncher
 
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
 
class MainActivity : Activity() {
    private class Th(val name: String, val top: Int, val bottom: Int, val accent: Int)
    private class Opt(val label: String, val run: () -> Unit)
 
    private val themes = listOf(
        Th("Ámbar", 0xFF2B1B10.toInt(), 0xFF0F0E17.toInt(), 0xFFFFB347.toInt()),
        Th("Océano", 0xFF0B2233.toInt(), 0xFF070C14.toInt(), 0xFF4DD0E1.toInt()),
        Th("Aurora", 0xFF1A1233.toInt(), 0xFF0A0A14.toInt(), 0xFFB388FF.toInt()),
        Th("Grafito", 0xFF1E1E22.toInt(), 0xFF0B0B0D.toInt(), 0xFFE0E0E0.toInt())
    )
    private val sizeNames = arrayOf("Pequeño", "Medio", "Grande")
    private val sizeScale = floatArrayOf(0.85f, 1f, 1.2f)
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private lateinit var prefs: SharedPreferences
    private var sig = ""
 
    private val theme get() = themes[prefs.getInt("theme", 0) % themes.size]
    private val scale get() = sizeScale[prefs.getInt("size", 1) % 3]
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun light() = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private fun getSet(k: String): Set<String> = prefs.getStringSet(k, emptySet())!!.toSet()
    private fun putSet(k: String, s: Set<String>) = prefs.edit().putStringSet(k, s).apply()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
 
    override fun onCreate(b: Bundle?) { super.onCreate(b); prefs = getSharedPreferences("p", 0) }
    override fun onResume() {
        super.onResume()
        val s = apps().joinToString(",") { it.activityInfo.packageName }
        if (s != sig) { sig = s; build() }
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* el inicio no se cierra */ }
 
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
 
    // ---------- piezas de interfaz ----------
    private fun glass(focused: Boolean, radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(if (focused) 0x33FFFFFF else 0x14FFFFFF)
        setStroke(dp(if (focused) 3 else 1), if (focused) theme.accent else 0x26FFFFFF)
    }
 
    private fun focusable(v: View, radius: Int, grow: Float, onClick: () -> Unit, onLong: (() -> Unit)? = null): View {
        v.isFocusable = true; v.isClickable = true
        fun paint(f: Boolean) {
            v.background = glass(f, radius)
            v.scaleX = if (f) grow else 1f; v.scaleY = v.scaleX
            v.elevation = if (f) dp(8).toFloat() else 0f
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
 
    private fun chip(label: String, onClick: () -> Unit): View {
        val t = TextView(this).apply {
            text = label; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(22), dp(11), dp(22), dp(11))
        }
        return focusable(t, 30, 1.06f, onClick)
    }
 
    private fun section(txt: String) = TextView(this).apply {
        text = txt.uppercase(); textSize = 13f; letterSpacing = 0.25f
        setTextColor(theme.accent); setPadding(dp(6), dp(26), 0, dp(6))
    }
 
    private fun hrow(content: View) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false; clipToPadding = false
        setPadding(dp(14), dp(14), dp(14), dp(14)); addView(content)
    }
 
    // ---------- pantalla principal ----------
    private fun build() {
        val hidden = getSet("hidden"); val favs = getSet("fav")
        val pm = packageManager
        val visible = apps().filter { it.activityInfo.packageName !in hidden }
        val favApps = visible.filter { it.activityInfo.packageName in favs }
        val restApps = visible.filter { it.activityInfo.packageName !in favs }
        val tileW = dp((168 * scale).toInt()); val tileH = dp((124 * scale).toInt())
 
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(theme.top, theme.bottom))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(56), dp(28), dp(56), dp(12))
        }
 
        // cabecera: hora + botones
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val timeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timeBox.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "h:mm"; textSize = 50f; typeface = light(); setTextColor(Color.WHITE)
        })
        timeBox.addView(TextClock(this).apply {
            format24Hour = "EEEE d 'de' MMMM"; format12Hour = "EEEE d 'de' MMMM"; textSize = 16f; setTextColor(0xFFB9B2C4.toInt())
        })
        head.addView(timeBox, LinearLayout.LayoutParams(0, WRAP, 1f))
        fun gap() = LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(14) }
        head.addView(chip("Entrada") { inputPanel() }, gap())
        head.addView(chip("Compartir pantalla") {
            val ok = listOf("com.homwee.multiscreenshare", "com.homwee.mmp.screenshare").firstOrNull { goPkg(it) }
            if (ok == null) toast("No disponible en esta TV")
        }, gap())
        head.addView(chip("Personalizar") { customPanel() }, gap())
        col.addView(head)
 
        // cuerpo con scroll
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(section("Favoritas"))
        if (favApps.isEmpty()) {
            body.addView(TextView(this).apply {
                text = "Mantén pulsado OK sobre una app para fijarla aquí"
                textSize = 15f; setTextColor(0xFF9A93A8.toInt()); setPadding(dp(8), dp(14), 0, dp(14))
            })
        } else {
            val r = LinearLayout(this)
            favApps.forEach { r.addView(appTile(it), LinearLayout.LayoutParams(tileW, tileH).apply { rightMargin = dp(14) }) }
            body.addView(hrow(r))
        }
        body.addView(section("Todas las apps"))
        val grid = GridLayout(this).apply { orientation = GridLayout.VERTICAL; rowCount = 2 }
        restApps.forEach {
            grid.addView(appTile(it), GridLayout.LayoutParams().apply {
                width = tileW; height = tileH; setMargins(dp(7), dp(7), dp(7), dp(7))
            })
        }
        body.addView(hrow(grid))
 
        col.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false; setPadding(0, 0, 0, dp(30)); addView(body)
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))
 
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        head.getChildAt(1)?.requestFocus()
    }
 
    private fun appTile(ri: ResolveInfo): View {
        val pkg = ri.activityInfo.packageName
        val label = ri.loadLabel(packageManager).toString()
        return tile(label, ri.loadIcon(packageManager), { goPkg(pkg) }, { appMenu(pkg, label) })
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
            orientation = LinearLayout.VERTICAL; setPadding(dp(32), dp(26), dp(32), dp(28))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat(); setColor(0xF0181622.toInt()); setStroke(dp(1), 0x33FFFFFF)
            }
        }
        box.addView(TextView(this).apply {
            text = title; textSize = 26f; typeface = light(); setTextColor(Color.WHITE); setPadding(dp(4), 0, 0, dp(10))
        })
        if (note != null) box.addView(TextView(this).apply {
            text = note; textSize = 14f; setTextColor(0xFF9A93A8.toInt()); setPadding(dp(4), 0, 0, dp(10))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        var first: View? = null
        items.forEach { o ->
            val t = TextView(this).apply {
                text = o.label; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(22), dp(14), dp(22), dp(14))
            }
            val v = focusable(t, 22, 1.03f, { d.dismiss(); o.run() })
            if (first == null) first = v
            list.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
        }
        box.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; addView(list) })
        wrap.addView(box, FrameLayout.LayoutParams(dp(480), WRAP, Gravity.CENTER))
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
            items.add(Opt(labelOf(inp)) {
                go(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inp.id)))
            })
        }
        panel("Entrada", items, if (inputs.isEmpty()) "No se detectaron entradas HDMI/AV. Usa el botón de entrada del control." else null)
    }
 
    private fun customPanel() {
        val items = listOf(
            Opt("Tema: ${theme.name}") { prefs.edit().putInt("theme", prefs.getInt("theme", 0) + 1).apply(); build(); customPanel() },
            Opt("Tamaño de iconos: ${sizeNames[prefs.getInt("size", 1) % 3]}") { prefs.edit().putInt("size", prefs.getInt("size", 1) + 1).apply(); build(); customPanel() },
            Opt("Mostrar apps ocultas") { putSet("hidden", emptySet()); build() },
            Opt("Ajustes de la TV") { go(Intent(Settings.ACTION_SETTINGS)) }
        )
        panel("Personalizar", items, "Mantén pulsado OK sobre una app para fijarla u ocultarla.")
    }
}
