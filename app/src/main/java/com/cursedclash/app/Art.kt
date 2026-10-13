package com.cursedclash.app

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
internal const val PIF = 3.14159265f

internal fun withAlpha(col: Int, a: Int): Int = (col and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

class Pose {
    var bob = 0f
    var lean = 0f
    var tilt = 0f
    var crouch = 0f
    var glow = 0f
    var eyes = false
    var lf1 = 0.35f; var lf2 = 0.15f; var lb1 = -0.35f; var lb2 = -0.1f
    var af1 = 0.95f; var af2 = 2.2f; var ab1 = 0.5f; var ab2 = 1.9f
    var xf1 = 0.3f; var xf2 = 1.4f; var xb1 = 0.1f; var xb2 = 1.2f
}

/**
 * Arte 100% original, desenhada por código (formas + gradientes + textura de ruído).
 * Origem do desenho: pés no (0,0), personagem olhando para +x, altura ~300 unidades.
 */
object Art {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val np = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var inited = false
    var flashOn = false
    private val flashF = PorterDuffColorFilter(0xCCFFFFFF.toInt(), PorterDuff.Mode.SRC_ATOP)
    private var ex = 0f
    private var ey = 0f

    private fun init() {
        if (inited) return
        inited = true
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val r = java.util.Random(3)
        for (y in 0 until 64) for (x in 0 until 64) {
            val g = 80 + r.nextInt(150)
            val a = 14 + r.nextInt(40)
            bmp.setPixel(x, y, Color.argb(a, g, g, g))
        }
        np.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    // ---------- helpers ----------
    private fun fill(col: Int) {
        p.style = Paint.Style.FILL; p.shader = null; p.maskFilter = null; p.color = col
        p.colorFilter = if (flashOn) flashF else null
    }

    private fun stroke(col: Int, w: Float) {
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.shader = null; p.maskFilter = null; p.color = col; p.strokeWidth = w
        p.colorFilter = if (flashOn) flashF else null
    }

    private fun poly(c: Canvas, col: Int, pts: FloatArray, tex: Boolean) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1]); i += 2
        }
        path.close()
        fill(col); c.drawPath(path, p)
        if (tex) {
            np.style = Paint.Style.FILL; c.drawPath(path, np)
        }
    }

    private fun disc(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        fill(col); c.drawCircle(x, y, r, p)
    }

    private fun ln(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, col: Int) {
        stroke(col, w); c.drawLine(x1, y1, x2, y2, p)
    }

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, col: Int) {
        rect.set(l, t, r, b); fill(col); c.drawOval(rect, p)
    }

    private fun limb(
        c: Canvas, x: Float, y: Float, a1: Float, l1: Float, a2: Float, l2: Float,
        w: Float, col: Int, hi: Int, band: Int
    ) {
        val x1 = x + sin(a1) * l1
        val y1 = y + cos(a1) * l1
        val x2 = x1 + sin(a2) * l2
        val y2 = y1 + cos(a2) * l2
        val w2 = w * 0.88f
        ln(c, x, y, x1, y1, w, col)
        ln(c, x1, y1, x2, y2, w2, col)
        val n1x = cos(a1) * w * 0.2f
        val n1y = -sin(a1) * w * 0.2f
        ln(c, x + n1x, y + n1y, x1 + n1x, y1 + n1y, w * 0.25f, hi)
        val n2x = cos(a2) * w2 * 0.2f
        val n2y = -sin(a2) * w2 * 0.2f
        ln(c, x1 + n2x, y1 + n2y, x2 + n2x, y2 + n2y, w2 * 0.25f, hi)
        np.style = Paint.Style.STROKE; np.strokeCap = Paint.Cap.ROUND
        np.strokeWidth = w; c.drawLine(x, y, x1, y1, np)
        np.strokeWidth = w2; c.drawLine(x1, y1, x2, y2, np)
        if (band != 0) {
            for (k in 1..3) {
                val f = k / 4f
                bandAt(c, x + (x1 - x) * f, y + (y1 - y) * f, a1, w, band)
                bandAt(c, x1 + (x2 - x1) * f, y1 + (y2 - y1) * f, a2, w2, band)
            }
        }
        ex = x2; ey = y2
    }

    private fun bandAt(c: Canvas, px: Float, py: Float, a: Float, w: Float, col: Int) {
        val nx = cos(a) * w * 0.5f
        val ny = -sin(a) * w * 0.5f
        ln(c, px - nx, py - ny, px + nx, py + ny, w * 0.14f, col)
    }

    private fun foot(c: Canvas, x: Float, y: Float, shoe: Int, sole: Int) {
        rect.set(x - 12f, y - 9f, x + 30f, y + 7f)
        fill(shoe); c.drawRoundRect(rect, 8f, 8f, p)
        ln(c, x - 9f, y + 6f, x + 27f, y + 6f, 4f, sole)
    }

    // ---------- poses ----------
    fun idlePose(t: Float): Pose {
        val q = Pose()
        q.bob = sin(t * 3.2f) * 3f
        q.lean = 0.05f
        return q
    }

    fun poseOf(f: Fighter): Pose {
        val q = Pose()
        val t = f.animT
        q.bob = sin(t * 3.2f) * 3f
        q.lean = 0.05f
        when (f.state) {
            FState.WALK -> {
                val ph = t * 11f
                q.lf1 = sin(ph) * 0.7f; q.lf2 = q.lf1 - 0.3f
                q.lb1 = -sin(ph) * 0.7f; q.lb2 = q.lb1 - 0.3f
                q.bob = abs(sin(ph)) * 5f
                q.lean = 0.12f
            }
            FState.JUMP -> {
                q.lf1 = 0.9f; q.lf2 = 0.1f; q.lb1 = -0.25f; q.lb2 = -0.9f
                q.af1 = 1.6f; q.af2 = 2.4f; q.ab1 = 1.2f; q.ab2 = 2.2f
                q.xf1 = 1.4f; q.xf2 = 2.2f
                q.bob = 0f
            }
            FState.ATTACK -> {
                val pr = (f.stateT / f.attDur).coerceIn(0f, 1f)
                val k = sin(PIF * pr)
                when (f.combo) {
                    1 -> {
                        q.af1 = lerp(0.95f, 1.57f, k); q.af2 = lerp(2.2f, 1.57f, k)
                        q.xf1 = lerp(0.3f, 1.5f, k); q.xf2 = q.xf1
                    }
                    2 -> {
                        q.ab1 = lerp(0.5f, 1.57f, k); q.ab2 = lerp(1.9f, 1.57f, k)
                        q.xb1 = lerp(0.1f, 1.4f, k); q.xb2 = q.xb1
                        q.af1 = 0.6f; q.af2 = 2.0f
                    }
                    else -> {
                        q.lf1 = lerp(0.35f, 1.45f, k); q.lf2 = lerp(0.15f, 1.5f, k)
                        q.af1 = -0.4f; q.af2 = 0.3f; q.ab1 = -0.6f; q.ab2 = 0.2f
                        q.lean = 0.05f - 0.2f * k
                        q.xf1 = 1.2f * k; q.xf2 = q.xf1
                    }
                }
                if (f.combo < 3) q.lean = 0.05f + 0.18f * k
            }
            FState.TECH -> {
                val pr = (f.stateT / 0.5f).coerceIn(0f, 1f)
                val k = sin(PIF * min(1f, pr * 1.1f) * 0.5f)
                if (f.def.id == 0) {
                    q.af1 = lerp(0.95f, 1.57f, k); q.af2 = lerp(2.2f, 1.57f, k)
                    q.ab1 = lerp(0.5f, 1.45f, k); q.ab2 = lerp(1.9f, 1.57f, k)
                } else {
                    q.af1 = lerp(2.9f, 0.8f, pr); q.af2 = q.af1
                    q.xf1 = lerp(2.6f, 1.0f, pr); q.xf2 = q.xf1
                }
                q.glow = k * 0.7f
                q.lean = 0.05f + 0.1f * k
            }
            FState.SPECIAL -> {
                val pr = (f.stateT / 1.1f).coerceIn(0f, 1f)
                val k = min(1f, pr * 2.5f)
                q.glow = 1f
                q.eyes = true
                if (f.def.id == 0) {
                    q.af1 = lerp(0.95f, 1.57f, k); q.af2 = lerp(2.2f, 1.57f, k)
                    q.ab1 = lerp(0.5f, 1.4f, k); q.ab2 = lerp(1.9f, 1.57f, k)
                } else {
                    q.af1 = lerp(0.95f, 1.9f, k); q.af2 = lerp(2.2f, 2.6f, k)
                    q.ab1 = lerp(0.5f, -1.3f, k); q.ab2 = lerp(1.9f, -1.9f, k)
                    q.xf1 = lerp(0.3f, 1.3f, k); q.xf2 = q.xf1
                    q.xb1 = lerp(0.1f, -0.9f, k); q.xb2 = q.xb1
                }
                q.lean = 0.1f
                q.crouch = 0.3f
            }
            FState.GUARD -> {
                q.crouch = 0.5f
                q.af1 = 1.2f; q.af2 = 2.9f; q.ab1 = 0.9f; q.ab2 = 2.9f
                q.xf1 = 0.9f; q.xf2 = 2.6f
                q.lean = -0.05f
                q.bob = 0f
            }
            FState.HIT, FState.KO -> {
                q.lean = -0.32f; q.tilt = -0.3f
                q.af1 = -0.6f; q.af2 = -0.2f; q.ab1 = -0.9f; q.ab2 = -0.5f
                q.xf1 = -0.4f; q.xf2 = 0f
                q.lf1 = 0.5f; q.lf2 = 0.1f; q.lb1 = -0.6f; q.lb2 = -0.2f
                q.bob = 0f
            }
            else -> {}
        }
        return q
    }

    fun drawAura(c: Canvas, def: CharDef, strength: Float, t: Float) {
        if (strength <= 0.02f) return
        val r = 175f + sin(t * 6f) * 12f * strength
        p.style = Paint.Style.FILL
        p.colorFilter = null
        p.maskFilter = null
        p.shader = RadialGradient(
            0f, -130f, r,
            intArrayOf(withAlpha(def.aura, (120 * strength).toInt()), withAlpha(def.aura, (55 * strength).toInt()), 0x00000000),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(0f, -130f, r, p)
        p.shader = null
        if (strength > 0.6f) {
            for (i in 0 until 7) {
                val x = -60f + i * 20f
                val h = 90f + 70f * abs(sin(t * 7f + i * 1.7f))
                ln(c, x, -4f, x + sin(t * 9f + i) * 10f, -h, 5f, withAlpha(def.aura, 110))
            }
        }
    }

    // ---------- fighters ----------
    fun drawFighter(c: Canvas, def: CharDef, q: Pose, t: Float) {
        init()
        val hipY = -122f + q.crouch * 18f + q.bob
        val shX = sin(q.lean) * 78f
        val shY = hipY - cos(q.lean) * 78f
        if (def.id == 0) drawGojo(c, q, hipY, shX, shY) else drawSukuna(c, q, hipY, shX, shY)
    }

    private fun headStart(c: Canvas, q: Pose, shX: Float, shY: Float) {
        val a = q.lean + q.tilt
        c.save()
        c.translate(shX + sin(a) * 38f, shY - cos(a) * 38f)
        c.rotate(a * 180f / PIF)
    }

    private val gojoHair = floatArrayOf(
        -28f, 4f, -44f, -14f, -30f, -18f, -46f, -38f, -24f, -34f, -30f, -60f, -10f, -44f,
        -8f, -70f, 6f, -46f, 16f, -66f, 22f, -42f, 38f, -52f, 32f, -26f, 30f, -6f,
        18f, -12f, 4f, -20f, -10f, -14f
    )
    private val gojoTips = floatArrayOf(-44f, -14f, -46f, -38f, -30f, -60f, -8f, -70f, 16f, -66f, 38f, -52f)

    private fun drawGojo(c: Canvas, q: Pose, hipY: Float, shX: Float, shY: Float) {
        val navy = 0xFF171B30.toInt()
        val navyHi = 0xFF3A4570.toInt()
        val pants = 0xFF1B2036.toInt()
        val skin = 0xFFF2D2B4.toInt()
        val skinSh = 0xFFD9AE8C.toInt()
        val shoe = 0xFF0B0B12.toInt()
        val sole = 0xFFEDEFF7.toInt()

        // braço de trás
        limb(c, shX - 12f, shY + 10f, q.ab1, 44f, q.ab2, 42f, 18f, navy, navyHi, 0)
        disc(c, ex, ey, 9.5f, skin)
        // perna de trás
        limb(c, -8f, hipY + 6f, q.lb1, 62f, q.lb2, 62f, 25f, pants, navyHi, 0)
        foot(c, ex, ey, shoe, sole)
        // tronco
        poly(c, navy, floatArrayOf(shX - 28f, shY, shX + 28f, shY, 24f, hipY + 8f, -24f, hipY + 8f), true)
        disc(c, shX - 24f, shY + 10f, 14f, navy)
        disc(c, shX + 24f, shY + 10f, 14f, navy)
        ln(c, shX - 16f, shY + 22f, 8f, hipY - 4f, 3f, 0x33FFFFFF)
        ln(c, shX + 10f, shY + 16f, 16f, hipY - 14f, 3f, 0x22FFFFFF)
        ln(c, shX, shY + 6f, 0f, hipY + 6f, 2f, 0x66000000)
        for (i in 1..4) {
            val f = i / 5f
            disc(c, lerp(shX, 0f, f), lerp(shY + 8f, hipY, f), 2.6f, 0xFFAAB4D6.toInt())
        }
        ln(c, -24f, hipY + 4f, 24f, hipY + 4f, 5f, 0xFF0D1020.toInt())
        // perna da frente
        limb(c, 8f, hipY + 6f, q.lf1, 62f, q.lf2, 62f, 26f, pants, navyHi, 0)
        foot(c, ex, ey, shoe, sole)
        // gola alta
        poly(c, 0xFF222A4A.toInt(), floatArrayOf(shX - 4f, shY + 4f, shX - 10f, shY - 30f, shX - 28f, shY - 22f, shX - 28f, shY + 6f), true)
        poly(c, 0xFF222A4A.toInt(), floatArrayOf(shX + 4f, shY + 4f, shX + 12f, shY - 30f, shX + 30f, shY - 22f, shX + 28f, shY + 6f), true)
        // cabeça
        headStart(c, q, shX, shY)
        oval(c, -24f, -28f, 26f, 27f, skin)
        oval(c, -22f, 10f, 22f, 27f, skinSh)
        oval(c, -23f, -27f, 25f, 22f, skin)
        poly(c, skinSh, floatArrayOf(24f, 0f, 31f, 9f, 23f, 10f), false)
        ln(c, 9f, 17f, 20f, 16f, 2.2f, 0xFF8A4A4A.toInt())
        if (q.eyes) {
            disc(c, 12f, -2f, 11f, 0x334FC3FF)
            disc(c, 24f, -2f, 11f, 0x334FC3FF)
            oval(c, 3f, -8f, 15f, 2f, 0xFFF4FAFF.toInt())
            oval(c, 17f, -8f, 28f, 2f, 0xFFF4FAFF.toInt())
            disc(c, 10f, -3f, 4.4f, 0xFF3FB6FF.toInt())
            disc(c, 23f, -3f, 4.4f, 0xFF3FB6FF.toInt())
            disc(c, 10f, -3f, 1.8f, 0xFF06203A.toInt())
            disc(c, 23f, -3f, 1.8f, 0xFF06203A.toInt())
            ln(c, 2f, -14f, 15f, -12f, 2.6f, 0xFFDDE8F5.toInt())
            ln(c, 17f, -12f, 28f, -14f, 2.6f, 0xFFDDE8F5.toInt())
        } else {
            rect.set(-27f, -12f, 28f, 3f)
            fill(0xFF0A0A12.toInt()); c.drawRoundRect(rect, 5f, 5f, p)
            ln(c, -20f, -9f, 22f, -9f, 1.6f, 0x55FFFFFF)
            ln(c, -26f, -4f, -36f, -8f, 4f, 0xFF0A0A12.toInt())
        }
        // cabelo branco
        poly(c, 0xFFF4F8FF.toInt(), gojoHair, true)
        var i = 0
        while (i < gojoTips.size) {
            ln(c, -4f, -16f, gojoTips[i] * 0.92f, gojoTips[i + 1] * 0.92f, 1.6f, 0xFFB7D2EC.toInt())
            i += 2
        }
        ln(c, 6f, -20f, 14f, -50f, 1.4f, 0xFFCFE2F5.toInt())
        c.restore()
        // braço da frente
        limb(c, shX + 12f, shY + 10f, q.af1, 44f, q.af2, 42f, 19f, navy, navyHi, 0)
        disc(c, ex, ey, 10f, skin)
    }

    private val sukHair = floatArrayOf(
        -26f, 4f, -32f, -20f, -18f, -24f, -22f, -46f, -6f, -34f, 0f, -58f, 10f, -36f,
        24f, -52f, 22f, -28f, 33f, -26f, 28f, -4f, 14f, -14f, 0f, -18f, -12f, -12f
    )
    private val sukTips = floatArrayOf(-32f, -20f, -22f, -46f, 0f, -58f, 24f, -52f, 33f, -26f)

    private fun drawSukuna(c: Canvas, q: Pose, hipY: Float, shX: Float, shY: Float) {
        val skin = 0xFFEBC2A4.toInt()
        val skinHi = 0xFFFFE0C8.toInt()
        val ink = 0xFF14101C.toInt()
        val white = 0xFFEDEAF2.toInt()
        val whiteHi = 0xFFFFFFFF.toInt()
        val sash = 0xFF2B2336.toInt()

        // braços extras (trás)
        limb(c, shX - 10f, shY + 40f, q.xb1, 36f, q.xb2, 34f, 14f, skin, skinHi, ink)
        disc(c, ex, ey, 8f, skin)
        // braço de trás
        limb(c, shX - 12f, shY + 10f, q.ab1, 44f, q.ab2, 42f, 18f, skin, skinHi, ink)
        disc(c, ex, ey, 9.5f, skin)
        // perna de trás
        limb(c, -8f, hipY + 6f, q.lb1, 62f, q.lb2, 62f, 29f, white, whiteHi, 0)
        foot(c, ex, ey, 0xFF2A2230.toInt(), 0xFF6B5B7A.toInt())
        // tronco nu com tatuagens
        poly(c, skin, floatArrayOf(shX - 28f, shY, shX + 28f, shY, 24f, hipY + 8f, -24f, hipY + 8f), true)
        disc(c, shX - 24f, shY + 10f, 14f, skin)
        disc(c, shX + 24f, shY + 10f, 14f, skin)
        for (k in 0..2) {
            val f = 0.3f + k * 0.2f
            val cx = lerp(shX, 0f, f)
            val cy = lerp(shY + 8f, hipY, f)
            ln(c, cx - 24f, cy, cx + 24f, cy + 2f, 3f, ink)
        }
        ln(c, shX, shY + 6f, 0f, hipY + 4f, 2.6f, ink)
        ln(c, shX - 14f, shY + 24f, shX - 6f, shY + 36f, 2f, ink)
        ln(c, shX + 14f, shY + 24f, shX + 6f, shY + 36f, 2f, ink)
        ln(c, shX - 22f, shY + 16f, 8f, hipY - 8f, 3f, 0x22FFFFFF)
        // xale branco
        poly(c, white, floatArrayOf(shX - 36f, shY - 2f, shX + 36f, shY - 2f, shX + 30f, shY + 20f, shX - 30f, shY + 20f), true)
        ln(c, shX - 30f, shY + 20f, shX + 30f, shY + 20f, 3f, ink)
        // perna da frente
        limb(c, 8f, hipY + 6f, q.lf1, 62f, q.lf2, 62f, 30f, white, whiteHi, 0)
        foot(c, ex, ey, 0xFF2A2230.toInt(), 0xFF6B5B7A.toInt())
        // faixa
        poly(c, sash, floatArrayOf(-30f, hipY - 10f, 30f, hipY - 10f, 28f, hipY + 14f, -28f, hipY + 14f), true)
        ln(c, -29f, hipY, 29f, hipY, 2f, 0xFFB08A3C.toInt())
        // cabeça
        headStart(c, q, shX, shY)
        oval(c, -24f, -28f, 26f, 27f, skin)
        oval(c, -22f, 10f, 22f, 27f, 0xFFD6A98A.toInt())
        oval(c, -23f, -27f, 25f, 22f, skin)
        poly(c, 0xFFD6A98A.toInt(), floatArrayOf(24f, 0f, 31f, 9f, 23f, 10f), false)
        // olhos principais
        oval(c, 3f, -11f, 15f, -2f, 0xFFFFF0F0.toInt())
        oval(c, 17f, -11f, 28f, -2f, 0xFFFFF0F0.toInt())
        disc(c, 10f, -6.5f, 3.6f, 0xFFD62839.toInt())
        disc(c, 23f, -6.5f, 3.6f, 0xFFD62839.toInt())
        disc(c, 10f, -6.5f, 1.4f, ink)
        disc(c, 23f, -6.5f, 1.4f, ink)
        ln(c, 1f, -15f, 15f, -10f, 3f, ink)
        ln(c, 17f, -10f, 29f, -15f, 3f, ink)
        // segundo par de olhos
        oval(c, 4f, 3f, 14f, 9f, 0xFFFFF0F0.toInt())
        oval(c, 17f, 3f, 27f, 9f, 0xFFFFF0F0.toInt())
        disc(c, 10f, 6f, 2.6f, 0xFFD62839.toInt())
        disc(c, 22f, 6f, 2.6f, 0xFFD62839.toInt())
        // marcas no rosto
        ln(c, 3f, 13f, 26f, 13f, 2.6f, ink)
        ln(c, 5f, 18f, 24f, 18f, 2.2f, ink)
        // sorriso
        ln(c, 8f, 22f, 22f, 21f, 2.4f, 0xFF6A2A2A.toInt())
        ln(c, 11f, 23f, 19f, 22.5f, 2f, 0xFFF8F0F0.toInt())
        // cabelo rosa
        poly(c, 0xFFF0809F.toInt(), sukHair, true)
        var i = 0
        while (i < sukTips.size) {
            ln(c, -4f, -16f, sukTips[i] * 0.92f, sukTips[i + 1] * 0.92f, 1.8f, 0xFFC4507C.toInt())
            i += 2
        }
        ln(c, 8f, -22f, 12f, -46f, 1.5f, 0xFFFFB3C9.toInt())
        c.restore()
        // braço extra da frente
        limb(c, shX + 10f, shY + 40f, q.xf1, 36f, q.xf2, 34f, 14f, skin, skinHi, ink)
        disc(c, ex, ey, 8f, skin)
        // braço da frente
        limb(c, shX + 12f, shY + 10f, q.af1, 44f, q.af2, 42f, 19f, skin, skinHi, ink)
        disc(c, ex, ey, 10f, skin)
    }
}
