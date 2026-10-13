package com.cursedclash.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

private val RED1 = 0xFFFF2D55.toInt()
private val RED2 = 0xFFB5179E.toInt()
private val BLUE1 = 0xFF3A5BFF.toInt()
private val BLUE2 = 0xFF2A2F6B.toInt()

private fun AppCompatActivity.lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

abstract class FullActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.immersive(window)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Ui.immersive(window)
    }
}

// ------------------------------------------------------------------ MENU
class MenuActivity : FullActivity() {
    private var pulse: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(BackdropView(this), FrameLayout.LayoutParams(-1, -1))

        val pw = Ui.dp(this, 230f).toInt()
        root.addView(
            PortraitView(this, Chars.GOJO, false).also { it.strength = 0.55f },
            FrameLayout.LayoutParams(pw, -1, Gravity.START or Gravity.BOTTOM).also { it.leftMargin = Ui.dp(this, 10f).toInt() }
        )
        root.addView(
            PortraitView(this, Chars.SUKUNA, true).also { it.strength = 0.55f },
            FrameLayout.LayoutParams(pw, -1, Gravity.END or Gravity.BOTTOM).also { it.rightMargin = Ui.dp(this, 10f).toInt() }
        )

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER

        val t1 = Ui.label(this, "CURSED", 46f, Color.WHITE)
        val t2 = Ui.label(this, "CLASH", 58f, 0xFFFF2D55.toInt())
        for (t in listOf(t1, t2)) {
            t.setTypeface(t.typeface, Typeface.ITALIC)
            t.gravity = Gravity.CENTER
            t.includeFontPadding = false
            t.setShadowLayer(26f, 0f, 0f, 0xFFFF2D55.toInt())
        }
        val titleBox = LinearLayout(this)
        titleBox.orientation = LinearLayout.VERTICAL
        titleBox.gravity = Gravity.CENTER
        titleBox.addView(t1)
        titleBox.addView(t2)
        col.addView(titleBox)

        val bw = Ui.dp(this, 250f).toInt()
        val gap = Ui.dp(this, 12f).toInt()
        val buttons = listOf(
            Ui.button(this, "LUTAR", RED1, RED2) { startActivity(Intent(this, SelectActivity::class.java)) },
            Ui.button(this, "CONFIGURAÇÕES", BLUE1, BLUE2) { startActivity(Intent(this, SettingsActivity::class.java)) },
            Ui.button(this, "SAIR", 0xFF4A4560.toInt(), 0xFF26233A.toInt()) { finish() }
        )
        col.addView(View(this), lp(1, Ui.dp(this, 14f).toInt()))
        for ((i, b) in buttons.withIndex()) {
            val l = LinearLayout.LayoutParams(bw, ViewGroup.LayoutParams.WRAP_CONTENT)
            l.topMargin = if (i == 0) 0 else gap
            col.addView(b, l)
            b.alpha = 0f
            b.translationY = 40f
            b.animate().alpha(1f).translationY(0f).setStartDelay(250L + i * 120L).setDuration(380).start()
        }
        titleBox.alpha = 0f
        titleBox.translationY = -50f
        titleBox.animate().alpha(1f).translationY(0f).setDuration(500).start()

        root.addView(col, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        val ver = Ui.label(this, "v" + Updater.versionName(this), 11f, 0x99FFFFFF.toInt())
        root.addView(ver, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also {
            it.bottomMargin = Ui.dp(this, 6f).toInt()
        })
        setContentView(root)

        pulse = ValueAnimator.ofFloat(1f, 1.05f).apply {
            duration = 1300
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val v = it.animatedValue as Float
                titleBox.scaleX = v
                titleBox.scaleY = v
            }
            start()
        }

        Updater.autoCheck(this)
        Novidades.showIfNew(this)
    }

    override fun onDestroy() {
        pulse?.cancel()
        super.onDestroy()
    }
}

// ------------------------------------------------------------------ SELEÇÃO
class SelectActivity : FullActivity() {
    private var selected = 0
    private val cards = ArrayList<LinearLayout>()
    private val infos = ArrayList<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(BackdropView(this), FrameLayout.LayoutParams(-1, -1))

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(this, 12f).toInt()
        main.setPadding(pad, pad / 2, pad, pad)

        val title = Ui.label(this, "ESCOLHA SEU LUTADOR", 20f, Color.WHITE)
        title.gravity = Gravity.CENTER
        title.setShadowLayer(18f, 0f, 0f, 0xFFFF2D55.toInt())
        main.addView(title, lp(-1, -2))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        for ((i, def) in Chars.all.withIndex()) {
            val card = makeCard(i, def)
            val l = LinearLayout.LayoutParams(0, -1, 1f)
            l.setMargins(pad / 2, pad / 2, pad / 2, pad / 2)
            row.addView(card, l)
            if (i == 0) {
                val vs = Ui.label(this, "VS", 26f, 0xFFFFD84D.toInt())
                vs.gravity = Gravity.CENTER
                row.addView(vs, lp(-2, -2))
            }
        }
        main.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER
        val bw = Ui.dp(this, 200f).toInt()
        val back = Ui.button(this, "VOLTAR", BLUE1, BLUE2) { finish() }
        val go = Ui.button(this, "COMBATE!", RED1, RED2) {
            val i = Intent(this, GameActivity::class.java)
            i.putExtra("player", selected)
            startActivity(i)
        }
        val l1 = LinearLayout.LayoutParams(bw, -2)
        l1.rightMargin = pad
        bottom.addView(back, l1)
        bottom.addView(go, LinearLayout.LayoutParams(bw, -2))
        main.addView(bottom, lp(-1, -2))

        root.addView(main, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        refresh()
    }

    private fun makeCard(index: Int, def: CharDef): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        val p = Ui.dp(this, 8f).toInt()
        card.setPadding(p, p, p, p)

        val portrait = PortraitView(this, def, index == 1)
        portrait.strength = 0.6f
        card.addView(portrait, LinearLayout.LayoutParams(-1, 0, 1f))

        val name = Ui.label(this, def.name, 22f, Color.WHITE)
        name.gravity = Gravity.CENTER
        card.addView(name, lp(-1, -2))
        val sub = Ui.label(this, def.title, 12f, def.accent)
        sub.gravity = Gravity.CENTER
        card.addView(sub, lp(-1, -2))
        val info = TextView(this)
        info.textSize = 11f
        info.setTextColor(0xCCFFFFFF.toInt())
        info.gravity = Gravity.CENTER
        info.text = def.desc + "\nTécnica: " + def.techName + "  •  Especial: " + def.specialName
        card.addView(info, lp(-1, -2))
        infos.add(info)

        card.setOnClickListener {
            selected = index
            refresh()
            card.animate().scaleX(1.05f).scaleY(1.05f).setDuration(110).withEndAction {
                card.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }.start()
        }
        cards.add(card)
        return card
    }

    private fun refresh() {
        for ((i, card) in cards.withIndex()) {
            val sel = i == selected
            val bg = GradientDrawable()
            bg.cornerRadius = Ui.dp(this, 18f)
            bg.setColor(if (sel) 0xCC1A1030.toInt() else 0x880A0614.toInt())
            bg.setStroke(Ui.dp(this, if (sel) 3f else 1.5f).toInt(), if (sel) Chars.all[i].accent else 0x44FFFFFF)
            card.background = bg
            card.alpha = if (sel) 1f else 0.78f
        }
    }
}

// ------------------------------------------------------------------ JOGO
class GameActivity : FullActivity() {
    private lateinit var view: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val pid = intent.getIntExtra("player", 0).coerceIn(0, Chars.all.size - 1)
        val player = Chars.all[pid]
        val cpu = Chars.all[(pid + 1) % Chars.all.size]
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        view = GameView(this, player, cpu, prefs.getInt("dif", 1), prefs.getBoolean("vib", true)) { finish() }
        setContentView(view)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                view.pauseGame()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if (::view.isInitialized) view.pauseGame()
    }
}

// ------------------------------------------------------------------ CONFIGURAÇÕES
class SettingsActivity : FullActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val root = FrameLayout(this)
        root.addView(BackdropView(this), FrameLayout.LayoutParams(-1, -1))

        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        val pad = Ui.dp(this, 18f).toInt()
        col.setPadding(pad, pad, pad, pad)
        val panel = GradientDrawable()
        panel.cornerRadius = Ui.dp(this, 20f)
        panel.setColor(0xCC0A0614.toInt())
        panel.setStroke(Ui.dp(this, 1.5f).toInt(), 0x55FFFFFF)
        col.background = panel

        col.addView(Ui.label(this, "CONFIGURAÇÕES", 22f, Color.WHITE), lp(-2, -2))

        col.addView(Ui.label(this, "DIFICULDADE", 13f, 0xFFFF5A70.toInt()).also { it.setPadding(0, pad, 0, pad / 3) }, lp(-2, -2))
        val difRow = LinearLayout(this)
        difRow.orientation = LinearLayout.HORIZONTAL
        val names = listOf("FÁCIL", "NORMAL", "DIFÍCIL")
        val difBtns = ArrayList<TextView>()
        fun paintDif() {
            val cur = prefs.getInt("dif", 1)
            for ((i, b) in difBtns.withIndex()) {
                b.alpha = if (i == cur) 1f else 0.45f
            }
        }
        for ((i, n) in names.withIndex()) {
            val b = Ui.button(this, n, RED1, RED2) {
                prefs.edit().putInt("dif", i).apply()
                paintDif()
            }
            b.textSize = 13f
            val l = LinearLayout.LayoutParams(Ui.dp(this, 110f).toInt(), -2)
            l.setMargins(4, 0, 4, 0)
            difRow.addView(b, l)
            difBtns.add(b)
        }
        paintDif()
        col.addView(difRow, lp(-2, -2))

        val vib = Ui.button(this, "", BLUE1, BLUE2) {}
        fun paintVib() {
            vib.text = if (prefs.getBoolean("vib", true)) "VIBRAÇÃO: LIGADA" else "VIBRAÇÃO: DESLIGADA"
        }
        vib.setOnClickListener {
            prefs.edit().putBoolean("vib", !prefs.getBoolean("vib", true)).apply()
            paintVib()
        }
        paintVib()
        val bw = Ui.dp(this, 260f).toInt()
        val l2 = LinearLayout.LayoutParams(bw, -2)
        l2.topMargin = pad
        col.addView(vib, l2)

        status = TextView(this)
        status.setTextColor(0xCCFFFFFF.toInt())
        status.textSize = 12f
        status.gravity = Gravity.CENTER
        status.text = "Versão instalada: " + Updater.versionName(this)

        val upd = Ui.button(this, "VERIFICAR ATUALIZAÇÃO", RED1, RED2) {
            Updater.manualCheck(this) { msg -> status.text = msg }
        }
        val l3 = LinearLayout.LayoutParams(bw, -2)
        l3.topMargin = pad / 2
        col.addView(upd, l3)
        col.addView(status, lp(-2, -2).also { it.topMargin = pad / 3 })

        val nov = Ui.button(this, "O QUE MUDOU", BLUE1, BLUE2) { Novidades.showLatest(this) }
        val l4 = LinearLayout.LayoutParams(bw, -2)
        l4.topMargin = pad / 2
        col.addView(nov, l4)

        val back = Ui.button(this, "VOLTAR", 0xFF4A4560.toInt(), 0xFF26233A.toInt()) { finish() }
        val l5 = LinearLayout.LayoutParams(bw, -2)
        l5.topMargin = pad / 2
        col.addView(back, l5)

        scroll.addView(col, FrameLayout.LayoutParams(Ui.dp(this, 420f).toInt(), -2, Gravity.CENTER_HORIZONTAL))
        val sl = FrameLayout.LayoutParams(-1, -1)
        root.addView(scroll, sl)
        setContentView(root)
    }
}
