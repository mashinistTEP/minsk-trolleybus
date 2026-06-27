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

    private val paintSky     = Paint()
    private val paintGround  = Paint()
    private val paintWall    = Paint()
    private val paintWire    = Paint()
    private val paintHUD     = Paint()
    private val paintBoard   = Paint()
    private val paintBtn     = Paint()
    private val paintCabin   = Paint()

    private var screenW = 0
    private var screenH = 0

    private val FOV = Math.PI / 3.0
    private val MAX_DEPTH = 20.0
    private val MAX_SPEED = 0.05f

    // Кнопки
    private var btnGas    = RectF()  // большая педаль газа (правый нижний угол)
    private var btnBrake  = RectF()  // педаль тормоза (левее газа)
    private var btnLeft   = RectF()  // поворот влево
    private var btnRight  = RectF()  // поворот вправо
    private var btnDoors  = RectF()  // двери
    private var btnRoute  = RectF()  // смена маршрута

    // Состояние нажатий
    private var gasPressed   = false
    private var brakePressed = false
    private var leftPressed  = false
    private var rightPressed = false

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
    private val roadColor  = Color.rgb(60, 60, 65)
    private val grassColor = Color.rgb(50, 90, 40)

    init {
        holder.addCallback(this)
        paintWire.color = Color.rgb(200, 200, 200)
        paintWire.strokeWidth = 3f
        paintWire.style = Paint.Style.STROKE
        paintHUD.isAntiAlias = true
        paintBoard.isAntiAlias = true
        paintBtn.isAntiAlias = true
        paintCabin.isAntiAlias = true
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        screenW = width; screenH = height
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
        val ph = screenH * 0.28f  // высота большой педали газа
        val pw = screenW * 0.10f  // ширина большой педали газа
        val gap = screenW * 0.01f

        // Газ — правый нижний угол
        btnGas = RectF(
            screenW - pw - gap,
            screenH - ph - gap,
            screenW - gap,
            screenH - gap
        )
        // Тормоз — левее газа, в 2 раза меньше
        val bw = pw * 0.5f
        val bh = ph * 0.5f
        btnBrake = RectF(
            btnGas.left - bw - gap,
            screenH - bh - gap,
            btnGas.left - gap,
            screenH - gap
        )
        // Стрелки поворота — левый нижний угол
        val aw = screenW * 0.08f
        val ah = screenH * 0.18f
        btnLeft  = RectF(gap, screenH - ah - gap, gap + aw, screenH - gap)
        btnRight = RectF(gap + aw + gap, screenH - ah - gap, gap + aw*2 + gap, screenH - gap)

        // Двери и маршрут — сверху справа
        val btnW = screenW * 0.10f
        val btnH = screenH * 0.12f
        btnDoors = RectF(screenW - btnW - gap, gap, screenW - gap, gap + btnH)
        btnRoute = RectF(screenW - btnW*2 - gap*2, gap, screenW - btnW - gap*2, gap + btnH)
    }

    override fun run() {
        while (running) {
            val now = System.currentTimeMillis()
            val delta = (now - lastTime) / 1000f
            lastTime = now

            // Управление
            if (gasPressed)   trolleybus.accelerate()
            if (brakePressed) trolleybus.brake()
            if (!gasPressed && !brakePressed) trolleybus.coast()
            if (leftPressed)  trolleybus.angle -= 0.03f
            if (rightPressed) trolleybus.angle += 0.03f

            trolleybus.update(delta)

            if (trolleybus.state == TrolleyState.GAME_OVER) {
                trolleybus.gameOverReason?.let { onGameOver?.invoke(it) }
            }

            val canvas = holder2.lockCanvas() ?: continue
            try { renderFrame(canvas) }
            finally { holder2.unlockCanvasAndPost(canvas) }

            val elapsed = System.currentTimeMillis() - now
            if (elapsed < 16) Thread.sleep(16 - elapsed)
        }
    }

    private fun renderFrame(canvas: Canvas) {
        // Небо
        val skyPaint = Paint()
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, screenH * 0.55f,
            Color.rgb(20, 20, 60), Color.rgb(80, 60, 120),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH * 0.55f, skyPaint)

        // Земля
        canvas.drawRect(0f, screenH * 0.55f, screenW.toFloat(), screenH.toFloat(),
            Paint().apply { color = grassColor })

        drawRaycast(canvas)
        drawWires(canvas)
        drawCabin(canvas)
        drawHUD(canvas)
        drawButtons(canvas)
        drawStopInfo(canvas)
    }

    private fun drawRaycast(canvas: Canvas) {
        val halfH = screenH * 0.5
        val numRays = screenW

        for (ray in 0 until numRays) {
            val rayAngle = trolleybus.angle.toDouble() - FOV / 2.0 +
                           ray.toDouble() / numRays.toDouble() * FOV
            val cosA = cos(rayAngle)
            val sinA = sin(rayAngle)

            var mapX = trolleybus.x.toInt()
            var mapY = trolleybus.y.toInt()

            val ddx = if (cosA == 0.0) 1e30 else abs(1.0 / cosA)
            val ddy = if (sinA == 0.0) 1e30 else abs(1.0 / sinA)

            val stepX: Int; val stepY: Int
            var sdx: Double; var sdy: Double

            if (cosA < 0) { stepX = -1; sdx = (trolleybus.x - mapX) * ddx }
            else           { stepX =  1; sdx = (mapX + 1.0 - trolleybus.x) * ddx }
            if (sinA < 0) { stepY = -1; sdy = (trolleybus.y - mapY) * ddy }
            else           { stepY =  1; sdy = (mapY + 1.0 - trolleybus.y) * ddy }

            var hit = false; var side = 0; var cell = 0; var depth = 0

            while (!hit && depth < MAX_DEPTH.toInt()) {
                if (sdx < sdy) { sdx += ddx; mapX += stepX; side = 0 }
                else            { sdy += ddy; mapY += stepY; side = 1 }
                cell = MapData.getCell(mapX, mapY)
                if (cell == 2 || cell == 3) hit = true
                depth++
            }

            if (hit) {
                var dist = if (side == 0)
                    (mapX - trolleybus.x + (1 - stepX) / 2.0) / cosA
                else
                    (mapY - trolleybus.y + (1 - stepY) / 2.0) / sinA
                dist *= cos(rayAngle - trolleybus.angle.toDouble())

                val wallH = (screenH / max(dist, 0.1)).toInt()
                val top    = (halfH - wallH / 2).toInt().coerceAtLeast(0)
                val bottom = (halfH + wallH / 2).toInt().coerceAtMost(screenH)

                val base = if (cell == 3) depotColor
                           else wallColors[(mapX + mapY) % wallColors.size]
                val shade = max(0f, 1f - (dist / MAX_DEPTH).toFloat())
                val ss = if (side == 1) 0.7f else 1.0f
                val r = (Color.red(base)   * shade * ss).toInt().coerceIn(0,255)
                val g = (Color.green(base) * shade * ss).toInt().coerceIn(0,255)
                val b = (Color.blue(base)  * shade * ss).toInt().coerceIn(0,255)

                paintWall.color = Color.rgb(r, g, b)
                canvas.drawLine(ray.toFloat(), top.toFloat(),
                                ray.toFloat(), bottom.toFloat(), paintWall)

                // Дорога под стеной
                paintWall.color = roadColor
                canvas.drawLine(ray.toFloat(), bottom.toFloat(),
                                ray.toFloat(), screenH.toFloat(), paintWall)
            }
        }
    }

    private fun drawWires(canvas: Canvas) {
        // Два провода КС с перспективой
        val vanishX = screenW * 0.5f
        val vanishY = screenH * 0.42f
        val wire1L = screenH * 0.38f
        val wire1R = screenH * 0.38f

        paintWire.strokeWidth = 2f
        paintWire.alpha = 200

        // Левый провод
        canvas.drawLine(0f, wire1L, vanishX, vanishY, paintWire)
        // Правый провод
        canvas.drawLine(screenW.toFloat(), wire1R, vanishX, vanishY, paintWire)

        // Второй провод чуть ниже
        paintWire.alpha = 150
        canvas.drawLine(0f, wire1L + 15f, vanishX, vanishY + 8f, paintWire)
        canvas.drawLine(screenW.toFloat(), wire1R + 15f, vanishX, vanishY + 8f, paintWire)

        // Подвесы
        paintWire.alpha = 100
        paintWire.strokeWidth = 1f
        for (i in 0..6) {
            val px = screenW * i / 6f
            canvas.drawLine(px, screenH * 0.25f, px, wire1L, paintWire)
        }
        paintWire.alpha = 255
        paintWire.strokeWidth = 3f
    }

    private fun drawCabin(canvas: Canvas) {
        val w = screenW.toFloat()
        val h = screenH.toFloat()

        // Лобовое стекло (рамка)
        val glassLeft   = w * 0.18f
        val glassRight  = w * 0.82f
        val glassTop    = h * 0.04f
        val glassBottom = h * 0.72f

        // Затемнение по краям (эффект кабины)
        val leftPillar  = RectF(0f, 0f, glassLeft, h)
        val rightPillar = RectF(glassRight, 0f, w, h)
        val topBar      = RectF(glassLeft, 0f, glassRight, glassTop)
        val bottomBar   = RectF(0f, glassBottom, w, h)

        paintCabin.color = Color.rgb(30, 25, 20)
        canvas.drawRect(leftPillar,  paintCabin)
        canvas.drawRect(rightPillar, paintCabin)
        canvas.drawRect(topBar,      paintCabin)

        // Панель приборов (нижняя часть)
        paintCabin.color = Color.rgb(25, 20, 18)
        canvas.drawRect(bottomBar, paintCabin)

        // Горизонтальная линия панели
        paintCabin.color = Color.rgb(50, 45, 40)
        paintCabin.style = Paint.Style.STROKE
        paintCabin.strokeWidth = 3f
        canvas.drawLine(0f, glassBottom, w, glassBottom, paintCabin)

        // Руль (по центру снизу)
        val wheelX = w * 0.5f
        val wheelY = h * 0.88f
        val wheelR = h * 0.10f

        paintCabin.style = Paint.Style.STROKE
        paintCabin.strokeWidth = 8f
        paintCabin.color = Color.rgb(20, 20, 20)
        canvas.drawCircle(wheelX, wheelY, wheelR, paintCabin)

        // Спицы руля
        paintCabin.strokeWidth = 5f
        canvas.drawLine(wheelX, wheelY - wheelR, wheelX, wheelY + wheelR, paintCabin)
        canvas.drawLine(wheelX - wheelR, wheelY, wheelX + wheelR, wheelY, paintCabin)
        canvas.drawLine(
            wheelX - wheelR * 0.7f, wheelY - wheelR * 0.7f,
            wheelX + wheelR * 0.7f, wheelY + wheelR * 0.7f, paintCabin
        )

        // Ступица руля
        paintCabin.style = Paint.Style.FILL
        paintCabin.color = Color.rgb(40, 40, 40)
        canvas.drawCircle(wheelX, wheelY, wheelR * 0.15f, paintCabin)

        // Поворот руля
        if (leftPressed || rightPressed) {
            val rot = if (leftPressed) -30f else 30f
            val cm = canvas.save()
            canvas.rotate(rot, wheelX, wheelY)
            paintCabin.style = Paint.Style.STROKE
            paintCabin.strokeWidth = 5f
            paintCabin.color = Color.rgb(20, 20, 20)
            canvas.drawLine(wheelX, wheelY - wheelR, wheelX, wheelY + wheelR, paintCabin)
            canvas.restoreToCount(cm)
        }

        // Приборы на панели
        drawDashboard(canvas, w, h, glassBottom)

        // Рамка лобового стекла
        paintCabin.style = Paint.Style.STROKE
        paintCabin.strokeWidth = 4f
        paintCabin.color = Color.rgb(40, 35, 30)
        canvas.drawRect(glassLeft, glassTop, glassRight, glassBottom, paintCabin)

        paintCabin.style = Paint.Style.FILL
    }

    private fun drawDashboard(canvas: Canvas, w: Float, h: Float, panelY: Float) {
        // Спидометр (слева от руля)
        val spdX = w * 0.30f
        val spdY = panelY + h * 0.07f
        val spdR = h * 0.055f
        paintCabin.style = Paint.Style.STROKE
        paintCabin.strokeWidth = 3f
        paintCabin.color = Color.rgb(60, 60, 60)
        canvas.drawCircle(spdX, spdY, spdR, paintCabin)

        // Стрелка спидометра
        val kmh = trolleybus.speed / MAX_SPEED
        val spdAngle = (-150 + kmh * 300) * Math.PI / 180.0
        paintCabin.color = Color.rgb(255, 80, 80)
        paintCabin.strokeWidth = 2f
        canvas.drawLine(
            spdX, spdY,
            spdX + (cos(spdAngle) * spdR * 0.8f).toFloat(),
            spdY + (sin(spdAngle) * spdR * 0.8f).toFloat(),
            paintCabin
        )

        // Цифра скорости
        paintCabin.style = Paint.Style.FILL
        paintHUD.textSize = h * 0.04f
        paintHUD.color = Color.rgb(200, 200, 200)
        paintHUD.textAlign = Paint.Align.CENTER
        val kmhInt = (trolleybus.speed / MAX_SPEED * 60).toInt()
        canvas.drawText("$kmhInt", spdX, spdY + h * 0.015f, paintHUD)
        paintHUD.textSize = h * 0.025f
        canvas.drawText("км/г", spdX, spdY + spdR + h * 0.02f, paintHUD)

        paintHUD.textAlign = Paint.Align.LEFT
    }

    private fun drawHUD(canvas: Canvas) {
        val w = screenW.toFloat()
        val h = screenH.toFloat()

        // Табло по центру сверху
        drawBoard(canvas, w, h)
    }

    private fun drawBoard(canvas: Canvas, w: Float, h: Float) {
        val boardW = w * 0.35f
        val boardH = h * 0.16f
        val boardX = w / 2 - boardW / 2
        val boardY = h * 0.02f

        // Фон табло
        paintBoard.color = Color.rgb(20, 20, 20)
        paintBoard.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(boardX, boardY, boardX+boardW, boardY+boardH),
            10f, 10f, paintBoard)

        paintBoard.color = Color.rgb(255, 200, 0)
        val number  = trolleybus.getBoardNumber()
        val topLine = trolleybus.getBoardTopLine()
        val botLine = trolleybus.getBoardBottomLine()

        if (number.isNotEmpty()) {
            paintBoard.textSize = boardH * 0.65f
            paintBoard.textAlign = Paint.Align.LEFT
            canvas.drawText(number, boardX + boardW * 0.05f,
                boardY + boardH * 0.72f, paintBoard)
            paintBoard.textSize = boardH * 0.28f
            canvas.drawText(topLine, boardX + boardW * 0.22f,
                boardY + boardH * 0.40f, paintBoard)
            canvas.drawText(botLine, boardX + boardW * 0.22f,
                boardY + boardH * 0.78f, paintBoard)
        } else {
            paintBoard.textSize = boardH * 0.28f
            paintBoard.textAlign = Paint.Align.CENTER
            canvas.drawText(topLine, boardX + boardW/2, boardY + boardH*0.40f, paintBoard)
            canvas.drawText(botLine, boardX + boardW/2, boardY + boardH*0.78f, paintBoard)
        }

        // Модель и номер
        paintBoard.textSize = boardH * 0.18f
        paintBoard.color = Color.GRAY
        paintBoard.textAlign = Paint.Align.CENTER
        canvas.drawText("БКМ 321  №${trolleybus.number}",
            boardX + boardW/2, boardY + boardH + h*0.025f, paintBoard)

        // Монетка + очки (правее табло)
        paintBoard.textSize = h * 0.055f
        paintBoard.color = Color.rgb(255, 200, 0)
        paintBoard.textAlign = Paint.Align.LEFT
        canvas.drawText("●:${trolleybus.score}",
            boardX + boardW + w*0.02f, boardY + boardH*0.7f, paintBoard)

        paintBoard.textAlign = Paint.Align.LEFT
    }

    private fun drawStopInfo(canvas: Canvas) {
        if (trolleybus.state != TrolleyState.AT_STOP &&
            trolleybus.state != TrolleyState.DOORS_OPEN &&
            trolleybus.state != TrolleyState.AT_TERMINAL) return

        val idx = (trolleybus.currentStopIndex - 1).coerceAtLeast(0)
        if (idx >= trolleybus.stops.size) return
        val stop = trolleybus.stops[idx]

        val w = screenW.toFloat(); val h = screenH.toFloat()
        paintHUD.style = Paint.Style.FILL
        paintHUD.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(w*0.2f, h*0.45f, w*0.8f, h*0.58f, paintHUD)
        paintHUD.color = Color.WHITE
        paintHUD.textSize = h * 0.055f
        paintHUD.textAlign = Paint.Align.CENTER
        canvas.drawText(stop.name, w*0.5f, h*0.535f, paintHUD)
        paintHUD.textAlign = Paint.Align.LEFT
    }

    private fun drawButtons(canvas: Canvas) {
        // Педаль газа (большая, правый угол)
        drawPedal(canvas, btnGas, gasPressed, true)
        // Педаль тормоза (меньше)
        drawPedal(canvas, btnBrake, brakePressed, false)
        // Стрелки поворота
        drawArrowBtn(canvas, btnLeft,  leftPressed,  false)
        drawArrowBtn(canvas, btnRight, rightPressed, true)
        // Двери
        drawSmallBtn(canvas, btnDoors,
            if (trolleybus.doorsOpen) "ЗЧ" else "ДВ",
            Color.argb(160, 180, 140, 0))
        // Маршрут
        drawSmallBtn(canvas, btnRoute, "МШ", Color.argb(160, 60, 60, 120))
    }

    private fun drawPedal(canvas: Canvas, rect: RectF, pressed: Boolean, isGas: Boolean) {
        val color = if (isGas) Color.rgb(30, 120, 30) else Color.rgb(120, 30, 30)
        val pressColor = if (isGas) Color.rgb(50, 200, 50) else Color.rgb(200, 50, 50)

        // Тень педали
        paintBtn.color = Color.argb(120, 0, 0, 0)
        canvas.drawRoundRect(RectF(rect.left+4f, rect.top+6f, rect.right+4f, rect.bottom+6f),
            12f, 12f, paintBtn)

        // Основа педали
        paintBtn.color = if (pressed) pressColor else color
        canvas.drawRoundRect(rect, 12f, 12f, paintBtn)

        // Текстура педали (горизонтальные полосы)
        paintBtn.color = Color.argb(60, 0, 0, 0)
        paintBtn.style = Paint.Style.STROKE
        paintBtn.strokeWidth = 3f
        var y = rect.top + 10f
        while (y < rect.bottom - 5f) {
            canvas.drawLine(rect.left + 6f, y, rect.right - 6f, y, paintBtn)
            y += 8f
        }
        paintBtn.style = Paint.Style.FILL

        // Блик
        paintBtn.color = Color.argb(40, 255, 255, 255)
        canvas.drawRoundRect(RectF(rect.left+4f, rect.top+4f,
            rect.right-4f, rect.centerY()), 8f, 8f, paintBtn)
    }

    private fun drawArrowBtn(canvas: Canvas, rect: RectF,
                              pressed: Boolean, isRight: Boolean) {
        paintBtn.color = if (pressed)
            Color.argb(220, 100, 100, 200)
        else Color.argb(160, 50, 50, 120)
        canvas.drawRoundRect(rect, 10f, 10f, paintBtn)

        // Стрелка
        val path = Path()
        val cx = rect.centerX(); val cy = rect.centerY()
        val aw = rect.width() * 0.35f; val ah = rect.height() * 0.3f
        if (isRight) {
            path.moveTo(cx - aw, cy - ah)
            path.lineTo(cx + aw, cy)
            path.lineTo(cx - aw, cy + ah)
        } else {
            path.moveTo(cx + aw, cy - ah)
            path.lineTo(cx - aw, cy)
            path.lineTo(cx + aw, cy + ah)
        }
        path.close()
        paintBtn.color = Color.WHITE
        canvas.drawPath(path, paintBtn)
    }

    private fun drawSmallBtn(canvas: Canvas, rect: RectF, text: String, color: Int) {
        paintBtn.color = color
        canvas.drawRoundRect(rect, 8f, 8f, paintBtn)
        paintBtn.color = Color.argb(80, 255, 255, 255)
        paintBtn.style = Paint.Style.STROKE
        paintBtn.strokeWidth = 1f
        canvas.drawRoundRect(rect, 8f, 8f, paintBtn)
        paintBtn.style = Paint.Style.FILL
        paintHUD.color = Color.WHITE
        paintHUD.textSize = rect.height() * 0.38f
        paintHUD.textAlign = Paint.Align.CENTER
        canvas.drawText(text, rect.centerX(), rect.centerY() + paintHUD.textSize*0.35f, paintHUD)
        paintHUD.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Сбрасываем состояние
        gasPressed = false; brakePressed = false
        leftPressed = false; rightPressed = false

        for (i in 0 until event.pointerCount) {
            val x = event.getX(i); val y = event.getY(i)
            if (btnGas.contains(x, y))   gasPressed   = true
            if (btnBrake.contains(x, y)) brakePressed = true
            if (btnLeft.contains(x, y))  leftPressed  = true
            if (btnRight.contains(x, y)) rightPressed = true
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val idx = event.actionIndex
            val x = event.getX(idx); val y = event.getY(idx)
            if (btnDoors.contains(x, y)) trolleybus.toggleDoors()
            if (btnRoute.contains(x, y)) onChangeRoute?.invoke()
        }

        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            gasPressed = false; brakePressed = false
            leftPressed = false; rightPressed = false
            // Пересчитываем оставшиеся пальцы
            for (i in 0 until event.pointerCount) {
                if (i == event.actionIndex) continue
                val x = event.getX(i); val y = event.getY(i)
                if (btnGas.contains(x, y))   gasPressed   = true
                if (btnBrake.contains(x, y)) brakePressed = true
                if (btnLeft.contains(x, y))  leftPressed  = true
                if (btnRight.contains(x, y)) rightPressed = true
            }
        }

        return true
    }

    fun stop() { running = false }
}
