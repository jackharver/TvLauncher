package com.alejandro.tvlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private val accents = intArrayOf(0xFFFFB347.toInt(), 0xFF7BE08F.toInt(), 0xFF79A8FF.toInt(), 0xFFC9A6FF.toInt(), 0xFFFF6B6B.toInt())
    private val accent get() = accents[prefs.getInt("accent", 0) % accents.size]
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) { super.onCreate(b); prefs = getSharedPreferences("p", 0) }
    override fun onResume() { super.onResume(); build() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* el inicio no se cierra */ }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun go(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast("No se pudo abrir") }
    }
    private fun goPkg(p: String): Boolean {
        val i = packageManager.getLeanbackLaunchIntentForPackage(p) ?: packageManager.getLaunchIntentForPackage(p) ?: return false
        go(i); return true
    }

    private fun card(label: String, icon: Drawable?, onClick: () -> Unit, onLong: (() -> Unit)? = null): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            isFocusable = true; isClickable = true
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        if (icon != null) v.addView(ImageView(this).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        v.addView(TextView(this).apply {
            text = label; textSize = 14f; setTextColor(Color.WHITE); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        })
        fun paint(f: Boolean) {
            v.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (f) 0x40FFFFFF else 0x1AFFFFFF)
                setStroke(dp(3), if (f) accent else 0)
            }
            v.scaleX = if (f) 1.07f else 1f; v.scaleY = v.scaleX
        }
        paint(false)
        v.setOnFocusChangeListener { _, f -> paint(f) }
        v.setOnClickListener { onClick() }
        if (onLong != null) v.setOnLongClickListener { onLong(); true }
        return v
    }

    private fun build() {
        val hidden = prefs.getStringSet("hidden", emptySet())!!.toSet()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF2A1F14.toInt(), 0xFF14121F.toInt()))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text = "Inicio"; textSize = 34f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextClock(this).apply { format24Hour = "HH:mm"; format12Hour = "h:mm a"; textSize = 26f; setTextColor(0xFFB9B2C4.toInt()) })
        root.addView(top)

        // Fila rápida: TV, HDMI, compartir pantalla, ajustes, personalizar
        val quick = LinearLayout(this).apply { setPadding(0, dp(14), 0, dp(10)) }
        fun q(label: String, click: () -> Unit, long: (() -> Unit)? = null) {
            quick.addView(card(label, null, click, long), LinearLayout.LayoutParams(dp(150), dp(56)).apply { rightMargin = dp(10) })
        }
        q("TV en vivo", {
            go(Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI))
        })
        val inputs = try {
            (getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager).tvInputList.filter { it.isPassthroughInput }
        } catch (e: Exception) { emptyList() }
        inputs.forEach { inp ->
            val name = inp.loadCustomLabel(this)?.toString() ?: inp.loadLabel(this)?.toString() ?: "HDMI"
            q(name, { go(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inp.id))) })
        }
        q("Compartir pantalla", {
            val ok = listOf("com.homwee.multiscreenshare", "com.homwee.mmp.screenshare").firstOrNull { goPkg(it) }
            if (ok == null) toast("No disponible en esta TV")
        })
        q("Ajustes", { go(Intent(Settings.ACTION_SETTINGS)) })
        q("Color", { prefs.edit().putInt("accent", prefs.getInt("accent", 0) + 1).apply(); build() })
        q("Mostrar ocultas", { prefs.edit().remove("hidden").apply(); build() })
        root.addView(quick)

        // Apps (mantén pulsado OK sobre una app para ocultarla)
        val pm = packageManager
        val apps = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName && it.activityInfo.packageName !in hidden }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val grid = GridLayout(this).apply { columnCount = 6 }
        apps.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            val c = card(ri.loadLabel(pm).toString(), ri.loadIcon(pm), { goPkg(pkg) }, {
                prefs.edit().putStringSet("hidden", hidden + pkg).apply()
                toast("Oculta. Usa \"Mostrar ocultas\" para recuperarla"); build()
            })
            grid.addView(c, GridLayout.LayoutParams().apply { width = dp(150); height = dp(104); setMargins(dp(6), dp(6), dp(6), dp(6)) })
        }
        root.addView(ScrollView(this).apply { isFillViewport = false; addView(grid) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        quick.getChildAt(0)?.requestFocus()
    }
}
