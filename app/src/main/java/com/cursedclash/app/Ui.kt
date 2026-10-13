package com.cursedclash.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.sin

object Ui {
    fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun immersive(w: Window) {
        WindowCompat.setDecorFitsSystemWindows(w, false)
        val c = WindowInsetsControllerCompat(w, w.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun button(ctx: Context, text: String, c1: Int, c2: Int, onClick: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        tv.textSize = 17f
        tv.letterSpacing = 0.1f
        tv.gravity = Gravity.CENTER
        val p = dp(ctx, 12f).toInt()
        tv.setPadding(p * 2, p, p * 2, p)
        val bg = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2))
        bg.cornerRadius = dp(ctx, 14f)
        bg.setStroke(dp(ctx, 2f).toInt(), 0x66FFFFFF)
        tv.background = bg
        tv.elevation = dp(ctx, 6f)
        tv.setOnClickListener { onClick() }
        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        return tv
    }

    fun label(ctx: Context, text: String, size: Float, color: Int): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.textSize = size
        tv.setTextColor(color)
        tv.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        return tv
    }
}

object Backdrop {
    fun render(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = java.util.Random(11)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val u = h / 900f
        val gy = h * (W.GROUND / W.H)
        val road = h * 0.81f
        val wf = w.toFloat()

        // céu
        p.shader = LinearGradient(
            0f, 0f, 0f, road,
            intArrayOf(0xFF05061A.toInt(), 0xFF1E1040.toInt(), 0xFF5B2260.toInt(), 0xFFB23A5E.toInt()),
            floatArrayOf(0f, 0.45f, 0.8f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, wf, road, p)
        p.shader = null

        // estrelas
        for (i in 0 until 90) {
            p.color = Color.argb(60 + r.nextInt(150), 255, 255, 255)
            c.drawCircle(r.nextFloat() * wf, r.nextFloat() * road * 0.55f, (0.6f + r.nextFloat() * 1.4f) * u, p)
        }

        // lua
        val mx = wf * 0.78f
        val my = road * 0.27f
        val mr = h * 0.11f
        p.shader = RadialGradient(mx, my, mr * 3.2f, 0x66FFE9C8, 0x00FFE9C8, Shader.TileMode.CLAMP)
        c.drawCircle(mx, my, mr * 3.2f, p)
        p.shader = null
        p.color = 0xFFFFF1D6.toInt()
        c.drawCircle(mx, my, mr, p)
        p.color = 0x22B89A78
        c.drawCircle(mx - mr * 0.3f, my - mr * 0.2f, mr * 0.22f, p)
        c.drawCircle(mx + mr * 0.35f, my + mr * 0.3f, mr * 0.16f, p)
        c.drawCircle(mx + mr * 0.1f, my - mr * 0.5f, mr * 0.1f, p)

        // prédios ao fundo e à frente
        building(c, p, r, wf, road, u, 0.16f, 0.40f, 50f, 110f, 0xFF18123A.toInt(), false)
        building(c, p, r, wf, road, u, 0.22f, 0.55f, 80f, 150f, 0xFF0C0A1D.toInt(), true)

        // rua
        p.shader = LinearGradient(
            0f, road, 0f, h.toFloat(),
            intArrayOf(0xFF2E2A3E.toInt(), 0xFF181521.toInt(), 0xFF0D0B14.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, road, wf, h.toFloat(), p)
        p.shader = null
        p.color = 0xFF7A68A0.toInt()
        c.drawRect(0f, road - 3f * u, wf, road + 3f * u, p)

        // faixa de pedestres em perspectiva
        p.color = 0x44FFFFFF
        val n = 16
        val topW = wf / n * 0.5f
        val botW = wf / n * 1.6f
        val path = Path()
        for (i in 0 until n) {
            val tx = (i - n / 2f) * (wf / n) * 0.62f + wf / 2f
            val bx = (i - n / 2f) * (wf / n) * 1.5f + wf / 2f
            path.reset()
            path.moveTo(tx - topW / 2, road + 14f * u)
            path.lineTo(tx + topW / 2, road + 14f * u)
            path.lineTo(bx + botW / 2, h.toFloat())
            path.lineTo(bx - botW / 2, h.toFloat())
            path.close()
            c.drawPath(path, p)
        }
        // reflexo
        p.shader = LinearGradient(
            0f, road, 0f, gy,
            0x33B23A5E, 0x00B23A5E, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, road, wf, gy, p)
        p.shader = null
        return bmp
    }

    private fun building(
        c: Canvas, p: Paint, r: java.util.Random, wf: Float, road: Float, u: Float,
        minH: Float, maxH: Float, minW: Float, maxW: Float, col: Int, neon: Boolean
    ) {
        var x = -10f * u
        while (x < wf) {
            val bw = (minW + r.nextFloat() * (maxW - minW)) * u
            val bh = road * (minH + r.nextFloat() * (maxH - minH))
            p.shader = null
            p.color = col
            c.drawRect(x, road - bh, x + bw, road, p)
            // janelas
            var wy = road - bh + 12f * u
            while (wy < road - 14f * u) {
                var wx = x + 8f * u
                while (wx < x + bw - 10f * u) {
                    if (r.nextFloat() < 0.28f) {
                        p.color = if (r.nextFloat() < 0.8f) 0x99FFD27A.toInt() else 0x8860E0FF.toInt()
                        c.drawRect(wx, wy, wx + 6f * u, wy + 8f * u, p)
                    }
                    wx += 15f * u
                }
                wy += 20f * u
            }
            // néon
            if (neon && r.nextFloat() < 0.35f) {
                val col2 = if (r.nextBoolean()) 0xFFFF2D8A.toInt() else 0xFF2DE0FF.toInt()
                val ny = road - bh * (0.3f + r.nextFloat() * 0.5f)
                p.maskFilter = BlurMaskFilter(14f * u, BlurMaskFilter.Blur.NORMAL)
                p.color = col2
                c.drawRect(x + bw * 0.15f, ny, x + bw * 0.85f, ny + 12f * u, p)
                p.maskFilter = null
                p.color = 0xFFFFFFFF.toInt()
                c.drawRect(x + bw * 0.2f, ny + 4f * u, x + bw * 0.8f, ny + 8f * u, p)
            }
            x += bw + r.nextFloat() * 8f * u
        }
    }
}

/** Fundo animado dos menus: cenário + partículas de energia amaldiçoada. */
class BackdropView(ctx: Context) : View(ctx) {
    private var bmp: Bitmap? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val px = FloatArray(40)
    private val py = FloatArray(40)
    private val ps = FloatArray(40)
    private val pc = IntArray(40)
    private var t = 0f
    private var last = 0L
    private val rnd = java.util.Random(5)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w <= 0 || h <= 0) return
        bmp = Backdrop.render(w, h)
        for (i in 0 until 40) {
            px[i] = rnd.nextFloat() * w
            py[i] = rnd.nextFloat() * h
            ps[i] = 0.3f + rnd.nextFloat()
            pc[i] = if (i % 2 == 0) 0xFF3AA0FF.toInt() else 0xFFFF2D4A.toInt()
        }
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
        last = now
        t += dt
        bmp?.let { c.drawBitmap(it, 0f, 0f, null) }
        val u = height / 900f
        for (i in 0 until 40) {
            py[i] -= ps[i] * 38f * u * dt * 3f
            px[i] += sin(t * 1.5f + i) * 0.6f
            if (py[i] < -10f) {
                py[i] = height + 10f
                px[i] = rnd.nextFloat() * width
            }
            p.color = (pc[i] and 0x00FFFFFF) or (0x88 shl 24)
            c.drawCircle(px[i], py[i], (2f + ps[i] * 3f) * u, p)
        }
        postInvalidateOnAnimation()
    }
}

/** Retrato animado de um lutador (usado no menu e na seleção). */
class PortraitView(ctx: Context, private val def: CharDef, private val flip: Boolean) : View(ctx) {
    private var t = 0f
    private var last = 0L
    var strength = 0.5f

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
        last = now
        t += dt
        val s = height / 335f
        c.save()
        c.translate(width / 2f, height * 0.97f)
        c.scale(if (flip) -s else s, s)
        Art.drawAura(c, def, strength + 0.12f * sin(t * 2f), t)
        Art.drawFighter(c, def, Art.idlePose(t), t)
        c.restore()
        postInvalidateOnAnimation()
    }
}
