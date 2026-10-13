package com.cursedclash.app

import kotlin.math.abs
import kotlin.math.min

object W {
    const val H = 900f
    const val GROUND = 790f
}

class CharDef(
    val id: Int,
    val name: String,
    val title: String,
    val aura: Int,
    val accent: Int,
    val speed: Float,
    val jump: Float,
    val dmg: Float,
    val techName: String,
    val specialName: String,
    val desc: String
)

object Chars {
    val GOJO = CharDef(
        0, "GOJO", "O Mais Forte", 0xFF3AA0FF.toInt(), 0xFF59B8FF.toInt(),
        1.0f, 1100f, 1.0f, "Azul", "Roxo",
        "Veloz e técnico. Azul puxa o rival, Roxo é devastador."
    )
    val SUKUNA = CharDef(
        1, "SUKUNA", "Rei das Maldições", 0xFFFF2D4A.toInt(), 0xFFFF5A70.toInt(),
        0.95f, 1080f, 1.12f, "Desmantelar", "Santuário",
        "Brutal e agressivo. Cortes à distância e uma chuva de golpes."
    )
    val all = listOf(GOJO, SUKUNA)
}

enum class FState { IDLE, WALK, JUMP, ATTACK, TECH, SPECIAL, GUARD, HIT, KO }

class Input {
    var left = false
    var right = false
    var guard = false
    var jump = false
    var attack = false
    var tech = false
    var special = false
    fun clearEdges() {
        jump = false; attack = false; tech = false; special = false
    }
}

class Proj(
    val kind: Int, // 0 Azul, 1 Roxo, 2 Corte, 3 Santuário (zona)
    val owner: Fighter,
    var x: Float,
    var y: Float,
    val vx: Float,
    val r: Float,
    val dmg: Float,
    var life: Float
) {
    var t = 0f
    var tick = 0f
    var dead = false
}

class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val color: Int, val size: Float,
    val line: Boolean, val ang: Float
)

class Fighter(val def: CharDef) {
    companion object {
        val ATT_DUR = floatArrayOf(0.32f, 0.34f, 0.5f)
        val ATT_DMG = floatArrayOf(30f, 32f, 55f)
        val ATT_REACH = floatArrayOf(130f, 130f, 175f)
        val ATT_KB = floatArrayOf(240f, 280f, 640f)
        val ATT_LAUNCH = floatArrayOf(0f, 0f, -480f)
        val ATT_STUN = floatArrayOf(0.28f, 0.28f, 0.6f)
        const val GRAV = 2800f
        const val MAX_HP = 1000f
    }

    var x = 0f
    var y = W.GROUND
    var vx = 0f
    var vy = 0f
    var facing = 1
    var hp = MAX_HP
    var ce = 30f
    var state = FState.IDLE
    var stateT = 0f
    var onGround = true
    var combo = 1
    var queued = false
    var hitDone = false
    var spawned = false
    var hitStun = 0f
    var invuln = 0f
    var flash = 0f
    var animT = 0f
    var koT = 0f
    var techCd = 0f
    var wins = 0

    val attDur: Float get() = ATT_DUR[(combo - 1).coerceIn(0, 2)]

    fun reset(px: Float, face: Int) {
        x = px; y = W.GROUND; vx = 0f; vy = 0f; facing = face
        hp = MAX_HP; ce = 30f; state = FState.IDLE; stateT = 0f; onGround = true
        combo = 1; queued = false; hitDone = false; spawned = false
        hitStun = 0f; invuln = 0f; flash = 0f; koT = 0f; techCd = 0f
    }

    private fun setState(s: FState) {
        if (state != s) {
            state = s; stateT = 0f
        }
    }

    private fun startAttack(n: Int) {
        combo = n
        state = FState.ATTACK
        stateT = 0f
        hitDone = false
        queued = false
    }

    fun update(inp: Input, foe: Fighter, dt: Float, g: GameView) {
        animT += dt
        stateT += dt
        if (techCd > 0f) techCd -= dt
        if (invuln > 0f) invuln -= dt
        if (flash > 0f) flash -= dt
        if (state != FState.KO) ce = min(100f, ce + 4.5f * dt)

        if (!onGround) vy += GRAV * dt
        x += vx * dt
        y += vy * dt
        if (y >= W.GROUND) {
            y = W.GROUND
            vy = 0f
            if (!onGround) {
                onGround = true
                if (state == FState.JUMP) setState(FState.IDLE)
            }
        }
        x = x.coerceIn(70f, g.worldW - 70f)

        when (state) {
            FState.KO -> {
                koT += dt
                if (onGround) vx -= vx * min(1f, 6f * dt)
            }
            FState.HIT -> {
                hitStun -= dt
                if (onGround) vx -= vx * min(1f, 8f * dt)
                if (hitStun <= 0f && onGround) {
                    setState(FState.IDLE)
                    invuln = 0.3f
                }
            }
            FState.ATTACK -> doAttack(inp, foe, g)
            FState.TECH -> {
                vx = 0f
                if (!spawned && stateT >= 0.22f) {
                    spawned = true
                    g.spawnTech(this, foe)
                }
                if (stateT >= 0.5f) {
                    techCd = 0.5f
                    setState(FState.IDLE)
                }
            }
            FState.SPECIAL -> {
                vx = 0f
                if (!spawned && stateT >= 0.55f) {
                    spawned = true
                    g.spawnSpecial(this, foe)
                }
                if (stateT >= 1.1f) setState(FState.IDLE)
            }
            else -> control(inp, foe, dt, g)
        }
    }

    private fun control(inp: Input, foe: Fighter, dt: Float, g: GameView) {
        if (onGround) facing = if (foe.x >= x) 1 else -1
        val dir = (if (inp.right) 1 else 0) - (if (inp.left) 1 else 0)

        if (inp.guard && onGround) {
            setState(FState.GUARD)
            vx = 0f
            return
        }
        if (state == FState.GUARD) setState(FState.IDLE)

        if (onGround) {
            vx = dir * 400f * def.speed
            if (inp.jump) {
                vy = -def.jump
                onGround = false
                vx = dir * 340f * def.speed
                setState(FState.JUMP)
            } else {
                setState(if (dir != 0) FState.WALK else FState.IDLE)
            }
        } else {
            vx += dir * 900f * dt
            val m = 380f * def.speed
            vx = vx.coerceIn(-m, m)
        }

        if (inp.attack) {
            startAttack(1)
        } else if (inp.special && ce >= 100f && onGround) {
            ce = 0f
            vx = 0f
            spawned = false
            setState(FState.SPECIAL)
            g.announce(def.specialName, def.accent)
        } else if (inp.tech && ce >= 25f && techCd <= 0f && onGround) {
            ce -= 25f
            vx = 0f
            spawned = false
            setState(FState.TECH)
        }
    }

    private fun doAttack(inp: Input, foe: Fighter, g: GameView) {
        val dur = attDur
        val p = stateT / dur
        if (onGround) vx = if (stateT < 0.12f) facing * 230f else vx * 0.8f
        if (inp.attack && stateT > 0.05f) queued = true
        if (!hitDone && p >= 0.3f && p <= 0.65f) tryHit(foe, g)
        if (stateT >= dur) {
            if (queued && combo < 3) startAttack(combo + 1)
            else setState(if (onGround) FState.IDLE else FState.JUMP)
        }
    }

    private fun tryHit(foe: Fighter, g: GameView) {
        val i = (combo - 1).coerceIn(0, 2)
        val dx = (foe.x - x) * facing
        if (dx > -30f && dx < ATT_REACH[i] + 40f && abs(foe.y - y) < 190f && foe.state != FState.KO) {
            val r = foe.takeHit(ATT_DMG[i] * def.dmg, ATT_KB[i], ATT_LAUNCH[i], ATT_STUN[i], this, g)
            if (r != 0) hitDone = true
        }
    }

    /** 0 = ignorado, 1 = defendido, 2 = acertou */
    fun takeHit(dmg: Float, kb: Float, launch: Float, stun: Float, from: Fighter, g: GameView): Int {
        if (invuln > 0f || state == FState.KO) return 0
        val dir = if (from.x <= x) 1f else -1f
        val guarded = state == FState.GUARD && (from.x - x) * facing > 0f
        if (guarded) {
            hp = maxOf(1f, hp - dmg * 0.1f)
            vx = dir * kb * 0.3f
            ce = min(100f, ce + 4f)
            g.spark(x + facing * 50f, y - 130f, 0xFF9AD0FF.toInt(), 7)
            return 1
        }
        hp -= dmg
        from.ce = min(100f, from.ce + 7f + dmg * 0.08f)
        ce = min(100f, ce + 5f)
        flash = 0.14f
        g.onHit(this, from, dmg)
        if (hp <= 0f) {
            hp = 0f
            state = FState.KO
            stateT = 0f
            koT = 0f
            vx = dir * (kb * 0.8f + 260f)
            vy = -650f
            onGround = false
            return 2
        }
        state = FState.HIT
        stateT = 0f
        hitStun = stun
        vx = dir * kb
        if (launch < 0f) {
            vy = launch
            onGround = false
        }
        return 2
    }
}
