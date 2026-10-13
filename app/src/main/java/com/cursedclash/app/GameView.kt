package com.cursedclash.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

private enum class Phase { INTRO, FIGHT, KO, END }

private class Btn(val id: Int, var cx: Float, var cy: Float, var r: Float, val label: String) {
    fun hit(x: Float, y: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        val rr = r * 1.12f
        return dx * dx + dy * dy <= rr * rr
    }
}

class GameView(
    ctx: Context,
    private val playerDef: CharDef,
    private val cpuDef: CharDef,
    private val difficulty: Int,
    private val vibrateOn: Boolean,
    private val onExit: () -> Unit
) : View(ctx) {

    var worldW = 1600f
    private var scale = 1f
    private val d = ctx.resources.displayMetrics.density

    private val p1 = Fighter(playerDef)
    private val p2 = Fighter(cpuDef)
    private val inp1 = Input()
    private val inp2 = Input()
    private val projs = ArrayList<Proj>()
    private val parts = ArrayList<Particle>()
    private var bg: Bitmap? = null
    private var last = 0L

    private var phase = Phase.INTRO
    private var phaseT = 0f
    private var round = 1
    private var timeLeft = 60f
    private var shake = 0f
    private var hitStop = 0f
    private var paused = false
    private var fightShown = false

    private var banner = ""
    private var bannerT = 0f
    private var bannerMax = 1f
    private var bannerColor = Color.WHITE
    private var flashA = 0f
    private var flashCol = Color.WHITE

    private var hs1 = Fighter.MAX_HP
    private var hs2 = Fighter.MAX_HP

    private val btns = ArrayList<Btn>()
    private var held = BooleanArray(7)
    private val pauseBtn = Btn(7, 0f, 0f, 0f, "")
    private val rectA = RectF()
    private val rectB = RectF()

    private val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val path = Path()

    // IA
    private var aiT = 0f
    private var aiMode = 0
    private var aiFollow = 0f
    private var aiCombo = 0

    init {
        tp.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        tp.textAlign = Paint.Align.CENTER
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ---------------------------------------------------------------- layout
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w <= 0 || h <= 0) return
        scale = h / W.H
        worldW = w / scale
        bg = Backdrop.render(w, h)
        val wf = w.toFloat()
        val hf = h.toFloat()
        btns.clear()
        btns.add(Btn(0, 85f * d, hf - 85f * d, 56f * d, "L"))
        btns.add(Btn(1, 215f * d, hf - 85f * d, 56f * d, "R"))
        btns.add(Btn(2, 150f * d, hf - 205f * d, 46f * d, "DEFESA"))
        btns.add(Btn(3, wf - 225f * d, hf - 185f * d, 50f * d, "PULO"))
        btns.add(Btn(4, wf - 95f * d, hf - 100f * d, 66f * d, "GOLPE"))
        btns.add(Btn(5, wf - 225f * d, hf - 75f * d, 50f * d, "TÉCNICA"))
        btns.add(Btn(6, wf - 95f * d, hf - 235f * d, 50f * d, "ESPECIAL"))
        pauseBtn.cx = wf / 2f
        pauseBtn.cy = 88f * d
        pauseBtn.r = 15f * d
        val bw = 220f * d
        val bh = 54f * d
        rectA.set(wf / 2f - bw / 2f, hf * 0.5f, wf / 2f + bw / 2f, hf * 0.5f + bh)
        rectB.set(wf / 2f - bw / 2f, hf * 0.5f + bh + 14f * d, wf / 2f + bw / 2f, hf * 0.5f + bh * 2f + 14f * d)
        startMatch()
    }

    private fun startMatch() {
        p1.wins = 0
        p2.wins = 0
        round = 1
        result()
        resetRound()
    }

    private fun result() {
        paused = false
    }

    private fun resetRound() {
        p1.reset(worldW * 0.32f, 1)
        p2.reset(worldW * 0.68f, -1)
        projs.clear()
        parts.clear()
        timeLeft = 60f
        phase = Phase.INTRO
        phaseT = 0f
        fightShown = false
        hs1 = Fighter.MAX_HP
        hs2 = Fighter.MAX_HP
        aiT = 0.8f
        aiFollow = 0f
        aiCombo = 0
        showBanner("ROUND $round", 1.4f, Color.WHITE)
    }

    private fun showBanner(text: String, dur: Float, color: Int) {
        banner = text
        bannerT = dur
        bannerMax = dur
        bannerColor = color
    }

    fun announce(text: String, color: Int) {
        showBanner(text, 1.1f, color)
        flashA = 0.6f
        flashCol = color
    }

    fun pauseGame() {
        if (phase != Phase.END) {
            paused = true
            held = BooleanArray(7)
        }
    }

    // ---------------------------------------------------------------- loop
    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        var dt = if (last == 0L) 0.016f else (now - last) / 1e9f
        last = now
        if (dt > 0.05f) dt = 0.05f
        if (!paused) step(dt)
        render(c)
        postInvalidateOnAnimation()
    }

    private fun step(dt0: Float) {
        var dt = dt0
        if (hitStop > 0f) {
            hitStop -= dt
            dt *= 0.15f
        }
        if (phase == Phase.KO && phaseT < 1.2f) dt *= 0.45f
        phaseT += dt
        if (bannerT > 0f && phase != Phase.END) bannerT -= dt
        if (shake > 0f) shake = max(0f, shake - dt * 40f)
        if (flashA > 0f) flashA = max(0f, flashA - dt * 2f)

        when (phase) {
            Phase.INTRO -> {
                runFighters(dt, false)
                if (!fightShown && phaseT > 1.5f) {
                    fightShown = true
                    phase = Phase.FIGHT
                    phaseT = 0f
                    showBanner("LUTE!", 0.8f, 0xFFFFD84D.toInt())
                }
            }
            Phase.FIGHT -> {
                timeLeft -= dt
                runFighters(dt, true)
                if (p1.state == FState.KO || p2.state == FState.KO) {
                    endRound(if (p1.state == FState.KO) 2 else 1, "K.O.")
                } else if (timeLeft <= 0f) {
                    timeLeft = 0f
                    val r1 = p1.hp
                    val r2 = p2.hp
                    endRound(if (r1 > r2) 1 else if (r2 > r1) 2 else 0, "TEMPO!")
                }
            }
            Phase.KO -> {
                runFighters(dt, false)
                if (phaseT > 2.6f) {
                    if (p1.wins >= 2 || p2.wins >= 2) {
                        phase = Phase.END
                        phaseT = 0f
                        val win = p1.wins >= 2
                        showBanner(if (win) "VITÓRIA!" else "DERROTA", 9999f, if (win) 0xFFFFD84D.toInt() else 0xFFFF4D6D.toInt())
                    } else {
                        round++
                        resetRound()
                    }
                }
            }
            Phase.END -> runFighters(dt, false)
        }

        updateParts(dt)
        hs1 = if (hs1 > p1.hp) max(p1.hp, hs1 - 350f * dt) else p1.hp
        hs2 = if (hs2 > p2.hp) max(p2.hp, hs2 - 350f * dt) else p2.hp
    }

    private fun endRound(winner: Int, text: String) {
        phase = Phase.KO
        phaseT = 0f
        if (winner == 1) p1.wins++ else if (winner == 2) p2.wins++
        showBanner(text, 2.4f, 0xFFFF4D6D.toInt())
        shake = 14f
    }

    private fun runFighters(dt: Float, live: Boolean) {
        if (live) {
            inp1.left = held[0]
            inp1.right = held[1]
            inp1.guard = held[2]
            think(dt)
        } else {
            inp1.left = false; inp1.right = false; inp1.guard = false
            inp1.clearEdges(); inp2.left = false; inp2.right = false; inp2.guard = false; inp2.clearEdges()
        }
        p1.update(inp1, p2, dt, this)
        p2.update(inp2, p1, dt, this)
        inp1.clearEdges()
        inp2.clearEdges()
        if (live) updateProjs(dt)

        // separação dos corpos
        val dx = p2.x - p1.x
        if (p1.onGround && p2.onGround && abs(dx) < 68f && p1.state != FState.KO && p2.state != FState.KO) {
            val push = (68f - abs(dx)) / 2f
            val s = if (dx >= 0f) 1f else -1f
            p1.x = (p1.x - s * push).coerceIn(70f, worldW - 70f)
            p2.x = (p2.x + s * push).coerceIn(70f, worldW - 70f)
        }
    }

    // ---------------------------------------------------------------- IA
    private fun think(dt: Float) {
        val a = p2
        val b = p1
        inp2.left = false; inp2.right = false; inp2.guard = false
        if (a.state == FState.KO || b.state == FState.KO) return
        val dx = b.x - a.x
        val dist = abs(dx)
        val toward = if (dx >= 0f) 1 else -1
        val react = when (difficulty) { 0 -> 0.55f; 1 -> 0.32f; else -> 0.18f }

        aiT -= dt
        if (aiT <= 0f) {
            aiT = react + Random.nextFloat() * react
            val r = Random.nextFloat()
            aiMode = when {
                a.ce >= 100f && dist in 150f..800f && r < 0.5f -> 5
                dist > 520f -> if (r < 0.22f && a.ce >= 25f && a.techCd <= 0f) 4 else 0
                dist > 200f -> if (r < 0.18f && a.ce >= 25f && a.techCd <= 0f) 4 else if (r < 0.28f) 6 else 0
                else -> when {
                    r < 0.55f -> 3
                    r < 0.72f -> 2
                    r < 0.88f -> 1
                    else -> 6
                }
            }
            val guardChance = when (difficulty) { 0 -> 0.15f; 1 -> 0.4f; else -> 0.65f }
            if (b.state == FState.ATTACK && dist < 260f && Random.nextFloat() < guardChance) aiMode = 2

            when (aiMode) {
                3 -> {
                    inp2.attack = true
                    aiCombo = if (Random.nextFloat() < 0.6f) 1 + Random.nextInt(2) else 0
                    aiFollow = 0.27f
                }
                4 -> inp2.tech = true
                5 -> inp2.special = true
                6 -> inp2.jump = true
            }
        }

        when (aiMode) {
            0 -> if (dist > 140f) { if (toward > 0) inp2.right = true else inp2.left = true }
            1 -> if (toward > 0) inp2.left = true else inp2.right = true
            2 -> inp2.guard = true
            3 -> if (dist > 150f) { if (toward > 0) inp2.right = true else inp2.left = true }
            6 -> if (dist > 120f) { if (toward > 0) inp2.right = true else inp2.left = true }
        }
        if (aiCombo > 0) {
            aiFollow -= dt
            if (aiFollow <= 0f) {
                inp2.attack = true
                aiCombo--
                aiFollow = 0.27f
            }
        }
    }

    // ---------------------------------------------------------------- ações
    fun spawnTech(f: Fighter, foe: Fighter) {
        if (f.def.id == 0) {
            projs.add(Proj(0, f, f.x + f.facing * 100f, f.y - 150f, f.facing * 430f, 42f, 70f * f.def.dmg, 1.8f))
        } else {
            projs.add(Proj(2, f, f.x + f.facing * 90f, f.y - 140f, f.facing * 1250f, 46f, 55f * f.def.dmg, 1.0f))
        }
    }

    fun spawnSpecial(f: Fighter, foe: Fighter) {
        if (f.def.id == 0) {
            projs.add(Proj(1, f, f.x + f.facing * 110f, f.y - 150f, f.facing * 900f, 95f, 230f * f.def.dmg, 2.2f))
        } else {
            projs.add(Proj(3, f, foe.x, f.y, 0f, 330f, 26f * f.def.dmg, 0.85f))
        }
    }

    @Suppress("DEPRECATION")
    fun onHit(victim: Fighter, attacker: Fighter, dmg: Float) {
        spark(victim.x, victim.y - 140f, attacker.def.accent, if (dmg >= 50f) 18 else 10)
        spark(victim.x, victim.y - 140f, Color.WHITE, 5)
        shake = max(shake, min(16f, 4f + dmg * 0.08f))
        hitStop = if (dmg >= 50f) 0.07f else 0.03f
        if (vibrateOn && victim === p1) {
            try { vib?.vibrate(if (dmg >= 50f) 45L else 20L) } catch (e: Exception) { }
        }
    }

    fun spark(x: Float, y: Float, color: Int, n: Int) {
        if (parts.size > 260) return
        for (i in 0 until n) {
            val a = Random.nextFloat() * 6.2832f
            val s = 200f + Random.nextFloat() * 520f
            val l = 0.3f + Random.nextFloat() * 0.25f
            parts.add(Particle(x, y, cos(a) * s, sin(a) * s, l, l, color, 5f + Random.nextFloat() * 8f, false, 0f))
        }
    }

    private fun slash(x: Float, y: Float, color: Int) {
        if (parts.size > 260) return
        parts.add(Particle(x, y, 0f, 0f, 0.28f, 0.28f, color, 130f + Random.nextFloat() * 120f, true, -0.9f + Random.nextFloat() * 1.8f))
    }

    private fun updateProjs(dt: Float) {
        val it = projs.iterator()
        while (it.hasNext()) {
            val pr = it.next()
            pr.t += dt
            pr.life -= dt
            val foe = if (pr.owner === p1) p2 else p1
            if (pr.kind == 3) {
                pr.x = foe.x
                pr.tick -= dt
                slash(foe.x + (Random.nextFloat() - 0.5f) * 300f, foe.y - 40f - Random.nextFloat() * 200f, 0xFFFF3050.toInt())
                if (pr.tick <= 0f) {
                    pr.tick = 0.14f
                    foe.takeHit(pr.dmg, 120f, 0f, 0.22f, pr.owner, this)
                }
            } else {
                pr.x += pr.vx * dt
                if (pr.kind == 0 && foe.state != FState.KO && foe.onGround && abs(foe.x - pr.x) < 480f) {
                    foe.x += sign(pr.x - foe.x) * 210f * dt
                }
                val hx = abs(foe.x - pr.x) < pr.r + 38f
                val hy = pr.y > foe.y - 260f - pr.r * 0.5f && pr.y < foe.y + pr.r * 0.5f
                if (hx && hy && foe.state != FState.KO) {
                    val kb = when (pr.kind) { 0 -> 350f; 1 -> 900f; else -> 520f }
                    val launch = when (pr.kind) { 0 -> -300f; 1 -> -520f; else -> -250f }
                    val stun = when (pr.kind) { 0 -> 0.55f; 1 -> 0.9f; else -> 0.4f }
                    val r = foe.takeHit(pr.dmg, kb, launch, stun, pr.owner, this)
                    if (r != 0) {
                        pr.dead = true
                        spark(pr.x, pr.y, pr.owner.def.accent, if (pr.kind == 1) 40 else 22)
                        if (pr.kind == 1) { shake = 18f; flashA = 0.5f; flashCol = 0xFFB066FF.toInt() }
                    }
                }
                if (pr.x < -200f || pr.x > worldW + 200f) pr.dead = true
            }
            if (pr.life <= 0f) pr.dead = true
            if (pr.dead) it.remove()
        }
    }

    private fun updateParts(dt: Float) {
        val it = parts.iterator()
        while (it.hasNext()) {
            val q = it.next()
            q.life -= dt
            q.x += q.vx * dt
            q.y += q.vy * dt
            q.vy += 900f * dt
            if (q.life <= 0f) it.remove()
        }
    }

    // ---------------------------------------------------------------- toque
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val a = e.actionMasked
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex
            val x = e.getX(i)
            val y = e.getY(i)
            if (paused || phase == Phase.END) {
                overlayTap(x, y)
                return true
            }
            if (pauseBtn.hit(x, y)) {
                pauseGame()
                return true
            }
        }
        if (paused || phase == Phase.END) return true

        val skip = if (a == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        val nh = BooleanArray(7)
        if (a != MotionEvent.ACTION_UP && a != MotionEvent.ACTION_CANCEL) {
            for (i in 0 until e.pointerCount) {
                if (i == skip) continue
                val x = e.getX(i)
                val y = e.getY(i)
                for (b in btns) if (b.hit(x, y)) nh[b.id] = true
            }
        }
        if (nh[3] && !held[3]) inp1.jump = true
        if (nh[4] && !held[4]) inp1.attack = true
        if (nh[5] && !held[5]) inp1.tech = true
        if (nh[6] && !held[6]) inp1.special = true
        held = nh
        return true
    }

    private fun overlayTap(x: Float, y: Float) {
        if (rectA.contains(x, y)) {
            if (paused) paused = false else if (phase == Phase.END) startMatch()
        } else if (rectB.contains(x, y)) {
            onExit()
        }
    }

    // ---------------------------------------------------------------- desenho
    private fun render(c: Canvas) {
        val bm = bg
        if (bm != null) c.drawBitmap(bm, 0f, 0f, null) else c.drawColor(Color.BLACK)

        c.save()
        if (shake > 0.5f) c.translate((Random.nextFloat() - 0.5f) * shake * 2f * d, (Random.nextFloat() - 0.5f) * shake * 2f * d)
        c.scale(scale, scale)

        drawShadow(c, p1)
        drawShadow(c, p2)
        if (p1.y >= p2.y) { drawFighter(c, p2); drawFighter(c, p1) } else { drawFighter(c, p1); drawFighter(c, p2) }
        for (pr in projs) drawProj(c, pr)
        for (q in parts) drawPart(c, q)
        c.restore()

        for (pr in projs) if (pr.kind == 3) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = 0x33300008
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            break
        }
        if (flashA > 0f) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = (flashCol and 0x00FFFFFF) or ((flashA * 120).toInt().coerceIn(0, 255) shl 24)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        drawHud(c)
        drawControls(c)
        drawBanner(c)
        if (paused) drawOverlay(c, "PAUSADO", "CONTINUAR", "SAIR")
        else if (phase == Phase.END) drawOverlay(c, null, "REVANCHE", "MENU")
    }

    private fun drawShadow(c: Canvas, f: Fighter) {
        val k = 1f - ((W.GROUND - f.y) / 700f).coerceIn(0f, 0.6f)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = (80 * k).toInt().coerceIn(0, 255) shl 24
        rf.set(f.x - 60f * k, W.GROUND - 2f, f.x + 60f * k, W.GROUND + 16f)
        c.drawOval(rf, paint)
    }

    private fun drawFighter(c: Canvas, f: Fighter) {
        c.save()
        c.translate(f.x, f.y)
        c.scale(f.facing.toFloat(), 1f)
        if (f.state == FState.KO) {
            val k = (f.koT * 4f).coerceIn(0f, 1f)
            c.rotate(-85f * k)
            c.translate(0f, -10f * k)
        }
        val strength = when {
            f.state == FState.SPECIAL -> 1f
            f.state == FState.TECH -> 0.6f
            f.ce >= 100f -> 0.3f + 0.1f * sin(f.animT * 6f)
            else -> 0f
        }
        Art.drawAura(c, f.def, strength, f.animT)
        Art.flashOn = f.flash > 0f
        Art.drawFighter(c, f.def, Art.poseOf(f), f.animT)
        Art.flashOn = false
        if (f.state == FState.GUARD) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.shader = null
            paint.color = 0x889AD0FF.toInt()
            rf.set(f.x * 0f + 10f, -250f, 150f, -20f)
            c.drawArc(rf, -80f, 160f, false, paint)
        }
        c.restore()
    }

    private fun drawProj(c: Canvas, pr: Proj) {
        paint.style = Paint.Style.FILL
        when (pr.kind) {
            0 -> {
                val r = pr.r * (1f + 0.08f * sin(pr.t * 18f))
                paint.shader = RadialGradient(
                    pr.x, pr.y, r * 1.8f,
                    intArrayOf(0xFFFFFFFF.toInt(), 0xFF3AA0FF.toInt(), 0x883A60FF.toInt(), 0x00000000),
                    floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP
                )
                c.drawCircle(pr.x, pr.y, r * 1.8f, paint)
                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = 0xCC9FD8FF.toInt()
                for (i in 0 until 3) {
                    rf.set(pr.x - r * 1.4f, pr.y - r * 1.4f, pr.x + r * 1.4f, pr.y + r * 1.4f)
                    c.drawArc(rf, pr.t * 400f + i * 120f, 70f, false, paint)
                }
            }
            1 -> {
                val r = pr.r * (1f + 0.06f * sin(pr.t * 22f))
                paint.shader = RadialGradient(
                    pr.x, pr.y, r * 1.7f,
                    intArrayOf(0xFFFFFFFF.toInt(), 0xFFC070FF.toInt(), 0x997020D0.toInt(), 0x00000000),
                    floatArrayOf(0f, 0.3f, 0.65f, 1f), Shader.TileMode.CLAMP
                )
                c.drawCircle(pr.x, pr.y, r * 1.7f, paint)
                paint.shader = null
                val a = pr.t * 14f
                paint.color = 0xFF3AA0FF.toInt()
                c.drawCircle(pr.x + cos(a) * r * 0.9f, pr.y + sin(a) * r * 0.9f, r * 0.32f, paint)
                paint.color = 0xFFFF2D4A.toInt()
                c.drawCircle(pr.x - cos(a) * r * 0.9f, pr.y - sin(a) * r * 0.9f, r * 0.32f, paint)
            }
            2 -> {
                val dir = if (pr.vx >= 0f) 1f else -1f
                paint.style = Paint.Style.STROKE
                paint.shader = null
                for (i in 3 downTo 0) {
                    val ox = pr.x - dir * i * 34f
                    rf.set(ox - pr.r, pr.y - pr.r * 1.7f, ox + pr.r, pr.y + pr.r * 1.7f)
                    val start = if (dir > 0f) -62f else 118f
                    paint.strokeWidth = pr.r * 0.5f * (1f - i * 0.2f)
                    paint.color = ((0xFF3B5C.toInt()) and 0x00FFFFFF) or ((255 - i * 60).coerceAtLeast(30) shl 24)
                    c.drawArc(rf, start, 124f, false, paint)
                    if (i == 0) {
                        paint.strokeWidth = pr.r * 0.16f
                        paint.color = Color.WHITE
                        c.drawArc(rf, start, 124f, false, paint)
                    }
                }
            }
        }
    }

    private fun drawPart(c: Canvas, q: Particle) {
        val a = (q.life / q.maxLife).coerceIn(0f, 1f)
        paint.shader = null
        paint.color = (q.color and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
        if (q.line) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f * a + 1f
            paint.strokeCap = Paint.Cap.ROUND
            val dx = cos(q.ang) * q.size / 2f
            val dy = sin(q.ang) * q.size / 2f
            c.drawLine(q.x - dx, q.y - dy, q.x + dx, q.y + dy, paint)
        } else {
            paint.style = Paint.Style.FILL
            c.drawCircle(q.x, q.y, q.size * a, paint)
        }
    }

    private fun drawHud(c: Canvas) {
        val w = width.toFloat()
        val top = 28f * d
        val barH = 20f * d
        val margin = 16f * d
        val barW = w / 2f - 56f * d - margin
        bar(c, margin, top, barW, barH, hs1 / Fighter.MAX_HP, p1.hp / Fighter.MAX_HP, false)
        bar(c, w - margin - barW, top, barW, barH, hs2 / Fighter.MAX_HP, p2.hp / Fighter.MAX_HP, true)
        ceBar(c, margin, top + barH + 5f * d, barW * 0.75f, 8f * d, p1.ce / 100f, false, p1.def.aura)
        ceBar(c, w - margin - barW * 0.75f, top + barH + 5f * d, barW * 0.75f, 8f * d, p2.ce / 100f, true, p2.def.aura)

        tp.style = Paint.Style.FILL
        tp.shader = null
        tp.color = Color.WHITE
        tp.textSize = 13f * d
        tp.textAlign = Paint.Align.LEFT
        c.drawText(p1.def.name, margin, top - 6f * d, tp)
        tp.textAlign = Paint.Align.RIGHT
        c.drawText(p2.def.name, w - margin, top - 6f * d, tp)

        // vitórias
        for (i in 0 until 2) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = if (p1.wins > i) 0xFFFFD84D.toInt() else 0x55FFFFFF
            c.drawCircle(margin + 6f * d + i * 18f * d, top + barH + 28f * d, 6f * d, paint)
            paint.color = if (p2.wins > i) 0xFFFFD84D.toInt() else 0x55FFFFFF
            c.drawCircle(w - margin - 6f * d - i * 18f * d, top + barH + 28f * d, 6f * d, paint)
        }

        // cronômetro
        val cx = w / 2f
        val cy = 44f * d
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0xAA0A0614.toInt()
        c.drawCircle(cx, cy, 26f * d, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * d
        paint.color = if (timeLeft < 10f) 0xFFFF4D6D.toInt() else 0xCCFFFFFF.toInt()
        c.drawCircle(cx, cy, 26f * d, paint)
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = 26f * d
        tp.color = if (timeLeft < 10f) 0xFFFF4D6D.toInt() else Color.WHITE
        val secs = kotlin.math.ceil(timeLeft.toDouble()).toInt().coerceAtLeast(0)
        c.drawText(secs.toString(), cx, cy + 9f * d, tp)

        // botão de pausa
        paint.style = Paint.Style.FILL
        paint.color = 0x88000000.toInt()
        c.drawCircle(pauseBtn.cx, pauseBtn.cy, pauseBtn.r, paint)
        paint.color = Color.WHITE
        c.drawRect(pauseBtn.cx - 5f * d, pauseBtn.cy - 6f * d, pauseBtn.cx - 2f * d, pauseBtn.cy + 6f * d, paint)
        c.drawRect(pauseBtn.cx + 2f * d, pauseBtn.cy - 6f * d, pauseBtn.cx + 5f * d, pauseBtn.cy + 6f * d, paint)
    }

    private fun bar(c: Canvas, x: Float, y: Float, w: Float, h: Float, lag: Float, cur: Float, right: Boolean) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0xAA0A0614.toInt()
        rf.set(x - 3f * d, y - 3f * d, x + w + 3f * d, y + h + 3f * d)
        c.drawRoundRect(rf, 8f * d, 8f * d, paint)
        val lc = lag.coerceIn(0f, 1f)
        val cc = cur.coerceIn(0f, 1f)
        paint.color = 0xFFF2F2F2.toInt()
        seg(c, x, y, w, h, lc, right)
        paint.color = when {
            cc > 0.5f -> 0xFF4CD964.toInt()
            cc > 0.25f -> 0xFFFFC83D.toInt()
            else -> 0xFFFF3B4E.toInt()
        }
        seg(c, x, y, w, h, cc, right)
        paint.color = 0x33FFFFFF
        seg(c, x, y, w, h * 0.4f, cc, right)
    }

    private fun seg(c: Canvas, x: Float, y: Float, w: Float, h: Float, f: Float, right: Boolean) {
        if (f <= 0f) return
        val fw = w * f
        if (right) rf.set(x + w - fw, y, x + w, y + h) else rf.set(x, y, x + fw, y + h)
        c.drawRoundRect(rf, 6f * d, 6f * d, paint)
    }

    private fun ceBar(c: Canvas, x: Float, y: Float, w: Float, h: Float, f: Float, right: Boolean, col: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x88000000.toInt()
        rf.set(x, y, x + w, y + h)
        c.drawRoundRect(rf, 4f * d, 4f * d, paint)
        paint.color = if (f >= 1f) 0xFFFFE066.toInt() else col
        seg(c, x, y, w, h, f.coerceIn(0f, 1f), right)
    }

    private fun drawControls(c: Canvas) {
        if (phase == Phase.END) return
        for (b in btns) {
            val on = held[b.id]
            var alpha = if (on) 0x77 else 0x38
            var ring = 0x88FFFFFF.toInt()
            if (b.id == 6 && p1.ce >= 100f) ring = 0xFFFFE066.toInt()
            if ((b.id == 5 && p1.ce < 25f) || (b.id == 6 && p1.ce < 100f)) alpha = 0x20
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = (alpha shl 24) or 0xFFFFFF
            c.drawCircle(b.cx, b.cy, b.r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f * d
            paint.color = ring
            c.drawCircle(b.cx, b.cy, b.r, paint)
            if (b.id == 6 && p1.ce >= 100f) {
                paint.strokeWidth = 4f * d
                paint.color = 0x66FFE066
                c.drawCircle(b.cx, b.cy, b.r + 4f * d + 3f * d * sin(p1.animT * 8f), paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            if (b.id == 0 || b.id == 1) {
                val s = if (b.id == 0) -1f else 1f
                path.reset()
                path.moveTo(b.cx + s * 20f * d, b.cy)
                path.lineTo(b.cx - s * 12f * d, b.cy - 18f * d)
                path.lineTo(b.cx - s * 12f * d, b.cy + 18f * d)
                path.close()
                c.drawPath(path, paint)
            } else {
                tp.style = Paint.Style.FILL
                tp.textAlign = Paint.Align.CENTER
                tp.color = Color.WHITE
                tp.textSize = b.r * (if (b.label.length > 6) 0.27f else 0.33f)
                c.drawText(b.label, b.cx, b.cy + tp.textSize * 0.35f, tp)
            }
        }
    }

    private fun drawBanner(c: Canvas) {
        if (bannerT <= 0f || banner.isEmpty()) return
        val el = bannerMax - bannerT
        val sc = 1f + 0.35f * max(0f, 1f - el * 6f)
        val a = min(1f, bannerT * 4f)
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = 64f * d * sc
        val y = height * 0.36f
        tp.style = Paint.Style.STROKE
        tp.strokeWidth = 9f * d
        tp.color = (0xFF000000.toInt() and 0x00FFFFFF) or ((a * 220).toInt() shl 24)
        c.drawText(banner, width / 2f, y, tp)
        tp.style = Paint.Style.FILL
        tp.color = (bannerColor and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
        c.drawText(banner, width / 2f, y, tp)
    }

    private fun drawOverlay(c: Canvas, title: String?, a: String, b: String) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x99000000.toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (title != null) {
            tp.style = Paint.Style.FILL
            tp.textAlign = Paint.Align.CENTER
            tp.color = Color.WHITE
            tp.textSize = 48f * d
            c.drawText(title, width / 2f, height * 0.38f, tp)
        } else {
            drawBanner(c)
        }
        menuBtn(c, rectA, a, 0xFFFF2D55.toInt(), 0xFFB5179E.toInt())
        menuBtn(c, rectB, b, 0xFF3A5BFF.toInt(), 0xFF2A2F6B.toInt())
    }

    private fun menuBtn(c: Canvas, r: RectF, text: String, c1: Int, c2: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(r.left, r.top, r.right, r.top, c1, c2, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 14f * d, 14f * d, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * d
        paint.color = 0x88FFFFFF.toInt()
        c.drawRoundRect(r, 14f * d, 14f * d, paint)
        tp.style = Paint.Style.FILL
        tp.textAlign = Paint.Align.CENTER
        tp.color = Color.WHITE
        tp.textSize = 20f * d
        c.drawText(text, r.centerX(), r.centerY() + 7f * d, tp)
    }
}
