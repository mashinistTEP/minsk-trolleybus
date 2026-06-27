package com.trolleybus.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*

class GameView(context: Context, private var trolleybus: Trolleybus) :
    SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    private var running = false
    private val holder2 = holder

    private val paintSky    = Paint()
    private val paintGround = Paint()
    private val paintWall   = Paint()
    private val paintWire   = Paint()
    private val paintHUD    = Paint()
    private val paintBoard  = Paint()
    private val paintWarning= Paint()
    private val paintBtn    = Paint()
    private val paintBtnTxt = Paint()

    private var screenW = 0
    private var screenH = 0

    private val FOV = Math.PI / 3.0
    private val MAX_DEPTH = 20.0

    private var btnAccel = RectF()
    private var btnBrake = RectF()
    private var btnCoast = RectF()
    private var btnDoors = RectF()
    private var btnRoute = RectF()

    private var accelPressed = false
    private var brakePressed = false
    private var coastPressed = false

    var onChangeRoute: (() -> Unit)? = null
    var onGameOver: ((GameOverReason) -> Unit)? = null

    private var lastTime = System.currentTimeMillis()

    private val wallColors = listOf(
        Color.rgb(180, 140, 100),
        Color.rgb(160, 120,  80),
        Color.rgb(200, 160, 110),
        Color.rgb(140, 110,  80)
    )
    private val depotColor = Color.rgb(80, 120, 80)

    private val MAX_SPEED = 0.05f

    init {
        holder.addCallback(this)

        paintSky.color = Color.rgb(20, 20, 50)
        paintGround.color = Color.rgb(60, 60, 60)

        paintWire.color = Color.rgb(200, 200, 200)
        paintWire.strokeWidth = 2f
        paintWire.style = Paint.Style.STROKE

        paintHUD.color = Color.WHITE
        paintHUD.textSize = 36f
        paintHUD.isAntiAlias = true

        paintBoard.color = Color.rgb(255, 200, 0)
        paintBoard.isAntiAlias = true

        paintWarning.color = Color.rgb(255, 80, 0)
        paintWarning.textSize = 40f
        paintWarning.isAntiAlias = true
        paintWarning.isFakeBoldText = true

        paintBtn.isAntiAlias = true
        paintBtnTxt.color = Color.WHITE
        paintBtnTxt.textSize = 38f
        paintBtnTxt.isAntiAlias = true
        paintBtnTxt.textAlign = Paint.Align.CENTER
        paintBtnTxt.isFakeBoldText = true
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        screenW = width
        screenH = height
        setupButtons()
        running = true
        thread = Thread(this)
        thread!!.start()
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
        screenW = w; screenH = ht
        setupButtons()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        thread?.join()
    }

    private fun setupButtons() {
        val bw = screenW * 0.22f
        val bh = screenH * 0.12f
        val by = screenH * 0.86f
        val gap = screenW * 0.02f

        btnAccel = RectF(gap,            by, gap + bw,      by + bh)
        btnBrake = RectF(gap*2 + bw,     by, gap*2 + bw*2,  by + bh)
        btnCoast = RectF(gap*3 + bw*2,   by, gap*3 + bw*3,  by + bh)
        btnDoors = RectF(screenW - bw*1.3f - gap, by, screenW - gap, by + bh)
        btnRoute = RectF(screenW - bw*1.3f - gap, by - bh - gap,
                         screenW - gap, by - gap)
    }

    override fun run() {
        while (running) {
            val now = System.currentTimeMillis()
            val delta = (now - lastTime) / 1000f
            lastTime = now

            if (accelPressed) trolleybus.accelerate()
            if (brakePressed) trolleybus.brake()
            if (coastPressed) trolleybus.coast()
            trolleybus.update(delta)

            if (trolleybus.state == TrolleyState.GAME_OVER) {
                trolleybus.gameOverReason?.let { onGameOver?.invoke(it) }
            }

            val canvas = holder2.lockCanvas() ?: continue
            try {
                renderFrame(canvas)
            } finally {
                holder2.unlockCanvasAndPost(canvas)
            }

            val elapsed = System.currentTimeMillis() - now
            if (elapsed < 16) Thread.sleep(16 - elapsed)
        }
    }

    private fun renderFrame(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH * 0.5f, paintSky)
        canvas.drawRect(0f, screenH * 0.5f, screenW.toFloat(), screenH.toFloat(), paintGround)
        drawRaycast(canvas)
        drawWires(canvas)
        drawHUD(canvas)
        drawButtons(canvas)
        trolleybus.switchWarning?.let { drawSwitchWarning(canvas, it) }
        drawStopInfo(canvas)
    }

    private fun drawRaycast(canvas: Canvas) {
        val halfH = screenH / 2.0
        val numRays = screenW

        for (ray in 0 until numRays) {
            val rayAngle = trolleybus.angle.toDouble() - (FOV / 2.0) +
                           (ray.toDouble() / numRays.toDouble() * FOV)

            val cosA = cos(rayAngle)
            val sinA = sin(rayAngle)

            var mapX = trolleybus.x.toInt()
            var mapY = trolleybus.y.toInt()

            val deltaDistX = if (cosA == 0.0) 1e30 else abs(1.0 / cosA)
            val deltaDistY = if (sinA == 0.0) 1e30 else abs(1.0 / sinA)

            val stepMX: Int
            val stepMY: Int
            var sideDistX: Double
            var sideDistY: Double

            if (cosA < 0) {
                stepMX = -1
                sideDistX = (trolleybus.x - mapX) * deltaDistX
            } else {
                stepMX = 1
                sideDistX = (mapX + 1.0 - trolleybus.x) * deltaDistX
            }
            if (sinA < 0) {
                stepMY = -1
                sideDistY = (trolleybus.y - mapY) * deltaDistY
            } else {
                stepMY = 1
                sideDistY = (mapY + 1.0 - trolleybus.y) * deltaDistY
            }

            var hit = false
            var side = 0
            var cellType = 2
            var depth = 0

            while (!hit && depth < MAX_DEPTH.toInt()) {
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX
                    mapX += stepMX
                    side = 0
                } else {
                    sideDistY += deltaDistY
                    mapY += stepMY
                    side = 1
                }
                cellType = MapData.getCell(mapX, mapY)
                if (cellType == 2 || cellType == 3) hit = true
                depth++
            }

            if (hit) {
                var dist = if (side == 0)
                    (mapX - trolleybus.x + (1 - stepMX) / 2.0) / cosA
                else
                    (mapY - trolleybus.y + (1 - stepMY) / 2.0) / sinA

                dist *= cos(rayAngle - trolleybus.angle.toDouble())

                val wallH = (screenH / max(dist, 0.1)).toInt()
                val top    = (halfH - wallH / 2).toInt()
                val bottom = (halfH + wallH / 2).toInt()

                val baseColor = if (cellType == 3) depotColor
                                else wallColors[(mapX + mapY) % wallColors.size]

                val shade = max(0f, 1f - (dist / MAX_DEPTH).toFloat())
                val sideShade = if (side == 1) 0.7f else 1.0f
                val r = (Color.red(baseColor)   * shade * sideShade).toInt().coerceIn(0, 255)
                val g = (Color.green(baseColor) * shade * sideShade).toInt().coerceIn(0, 255)
                val b = (Color.blue(baseColor)  * shade * sideShade).toInt().coerceIn(0, 255)

                paintWall.color = Color.rgb(r, g, b)
                canvas.drawLine(ray.toFloat(), top.toFloat(),
                                ray.toFloat(), bottom.toFloat(), paintWall)
            }
        }
    }

    private fun drawWires(canvas: Canvas) {
        val wireY1 = screenH * 0.35f
        val wireY2 = screenH * 0.37f
        paintWire.alpha = 180
        canvas.drawLine(0f, wireY1, screenW.toFloat(), wireY1, paintWire)
        canvas.drawLine(0f, wireY2, screenW.toFloat(), wireY2, paintWire)
        paintWire.alpha = 120
        for (i in 0..8) {
            val px = screenW * i / 8f
            canvas.drawLine(px, screenH * 0.28f, px, wireY1, paintWire)
        }
        paintWire.alpha = 255
    }

    private fun drawHUD(canvas: Canvas) {
        drawBoard(canvas)
        val kmh = (trolleybus.speed / MAX_SPEED * 60).toInt()
        paintHUD.textSize = 36f
        paintHUD.color = Color.WHITE
        canvas.drawText("$kmh км/г", 20f, screenH * 0.08f, paintHUD)
        canvas.drawText("Ачкі: ${trolleybus.score}", 20f, screenH * 0.13f, paintHUD)
        canvas.drawText("Пас: ${trolleybus.passengers}", 20f, screenH * 0.18f, paintHUD)

        val powerColor = if (trolleybus.powerState == PowerState.ON)
            Color.rgb(0, 255, 100) else Color.rgb(255, 100, 0)
        paintHUD.color = powerColor
        val powerText = if (trolleybus.powerState == PowerState.ON) "TOK" else "НАКАТ"
        canvas.drawText(powerText, 20f, screenH * 0.23f, paintHUD)

        if (trolleybus.doorsOpen) {
            paintHUD.color = Color.YELLOW
            canvas.drawText("ДЗВЕРЫ АДЧЫНЕНЫ", screenW * 0.3f, screenH * 0.08f, paintHUD)
        }
        paintHUD.color = Color.WHITE
    }

    private fun drawBoard(canvas: Canvas) {
        val boardRect = RectF(screenW * 0.55f, 10f, screenW - 10f, screenH * 0.22f)
        paintBoard.color = Color.rgb(30, 30, 30)
        paintBoard.style = Paint.Style.FILL
        canvas.drawRoundRect(boardRect, 12f, 12f, paintBoard)
        paintBoard.color = Color.rgb(255, 200, 0)

        val number  = trolleybus.getBoardNumber()
        val topLine = trolleybus.getBoardTopLine()
        val botLine = trolleybus.getBoardBottomLine()

        if (number.isNotEmpty()) {
            paintBoard.textSize = 72f
            paintBoard.textAlign = Paint.Align.LEFT
            canvas.drawText(number, screenW * 0.57f, screenH * 0.16f, paintBoard)
            paintBoard.textSize = 26f
            canvas.drawText(topLine, screenW * 0.65f, screenH * 0.09f, paintBoard)
            canvas.drawText(botLine, screenW * 0.65f, screenH * 0.17f, paintBoard)
        } else {
            paintBoard.textSize = 26f
            paintBoard.textAlign = Paint.Align.CENTER
            val cx = (boardRect.left + boardRect.right) / 2
            canvas.drawText(topLine, cx, screenH * 0.10f, paintBoard)
            canvas.drawText(botLine, cx, screenH * 0.18f, paintBoard)
        }

        paintBoard.textAlign = Paint.Align.LEFT
        paintBoard.textSize = 22f
        paintBoard.color = Color.GRAY
        canvas.drawText("БКМ 321  №${trolleybus.number}",
                        screenW * 0.57f, screenH * 0.21f, paintBoard)
    }

    private fun drawSwitchWarning(canvas: Canvas, sw: Switch) {
        val msg = if (sw.requiresPower) "РАЗГАНЯЙЦЕСЯ!" else "НАКАТАМ!"
        paintWarning.color = Color.argb(200, 255, 60, 0)
        val rect = RectF(screenW * 0.1f, screenH * 0.55f,
                         screenW * 0.9f, screenH * 0.72f)
        canvas.drawRoundRect(rect, 16f, 16f, paintWarning)
        paintWarning.color = Color.WHITE
        paintWarning.textSize = 44f
        paintWarning.textAlign = Paint.Align.CENTER
        canvas.drawText("СТРЭЛКА КС", screenW * 0.5f, screenH * 0.61f, paintWarning)
        paintWarning.textSize = 36f
        canvas.drawText(msg, screenW * 0.5f, screenH * 0.66f, paintWarning)
        paintWarning.textSize = 24f
        paintWarning.color = Color.rgb(255, 220, 180)
        canvas.drawText(sw.desc, screenW * 0.5f, screenH * 0.70f, paintWarning)
        paintWarning.textAlign = Paint.Align.LEFT
    }

    private fun drawStopInfo(canvas: Canvas) {
        if (trolleybus.state != TrolleyState.AT_STOP &&
            trolleybus.state != TrolleyState.DOORS_OPEN &&
            trolleybus.state != TrolleyState.AT_TERMINAL) return

        val idx = (trolleybus.currentStopIndex - 1).coerceAtLeast(0)
        if (idx >= trolleybus.stops.size) return
        val stop = trolleybus.stops[idx]

        paintHUD.style = Paint.Style.FILL
        paintHUD.color = Color.argb(200, 0, 0, 0)
        val rect = RectF(0f, screenH * 0.74f, screenW.toFloat(), screenH * 0.84f)
        canvas.drawRect(rect, paintHUD)
        paintHUD.color = Color.WHITE
        paintHUD.textSize = 34f
        paintHUD.textAlign = Paint.Align.CENTER
        canvas.drawText(stop.name, screenW * 0.5f, screenH * 0.80f, paintHUD)
        paintHUD.textAlign = Paint.Align.LEFT
    }

    private fun drawButtons(canvas: Canvas) {
        drawBtn(canvas, btnAccel, "GAS",    Color.argb(180, 0, 180, 80),  accelPressed)
        drawBtn(canvas, btnBrake, "ТАРМ",   Color.argb(180, 200, 40, 40), brakePressed)
        drawBtn(canvas, btnCoast, "НАКАТ",  Color.argb(180, 60, 60, 200), coastPressed)
        drawBtn(canvas, btnDoors,
            if (trolleybus.doorsOpen) "ЗАЧЫН" else "ДЗВЕРЫ",
            Color.argb(180, 180, 140, 0), false)
        drawBtn(canvas, btnRoute, "МАРШРУТ", Color.argb(180, 80, 80, 80), false)
    }

    private fun drawBtn(canvas: Canvas, rect: RectF, text: String,
                        color: Int, pressed: Boolean) {
        paintBtn.color = if (pressed) Color.argb(220, 255, 255, 255) else color
        paintBtn.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 16f, 16f, paintBtn)
        paintBtn.color = Color.argb(100, 255, 255, 255)
        paintBtn.style = Paint.Style.STROKE
        paintBtn.strokeWidth = 2f
        canvas.drawRoundRect(rect, 16f, 16f, paintBtn)
        paintBtn.style = Paint.Style.FILL
        paintBtnTxt.color = if (pressed) Color.BLACK else Color.WHITE
        canvas.drawText(text, rect.centerX(), rect.centerY() + 13f, paintBtnTxt)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                accelPressed = btnAccel.contains(x, y)
                brakePressed = btnBrake.contains(x, y)
                coastPressed = btnCoast.contains(x, y)
                if (btnDoors.contains(x, y)) trolleybus.toggleDoors()
                if (btnRoute.contains(x, y)) onChangeRoute?.invoke()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                accelPressed = false; brakePressed = false; coastPressed = false
            }
            MotionEvent.ACTION_MOVE -> {
                accelPressed = btnAccel.contains(x, y)
                brakePressed = btnBrake.contains(x, y)
                coastPressed = btnCoast.contains(x, y)
            }
        }
        return true
    }

    fun stop() { running = false }
}
