package com.trolleybus.game

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.*
import kotlin.random.Random

// ─── Экраны ───────────────────────────────────────────────────────────────────
enum class Screen { LOADING, MAIN_MENU, ROUTE_SELECT, GAME, GAME_OVER }

// ─── MainActivity ─────────────────────────────────────────────────────────────
class MainActivity : Activity() {

    private lateinit var mainView: MainView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        mainView = MainView(this)
        setContentView(mainView)
    }

    override fun onPause()  { super.onPause();  mainView.pause() }
    override fun onResume() { super.onResume(); mainView.resume() }
    override fun onBackPressed() {
        if (mainView.currentScreen == Screen.GAME) {
            mainView.switchScreen(Screen.MAIN_MENU)
        } else {
            super.onBackPressed()
        }
    }
}

// ─── Главный View (все экраны кроме GameView) ─────────────────────────────────
class MainView(context: Context) : View(context), Runnable {

    var currentScreen = Screen.LOADING
    private var thread: Thread? = null
    private var running = false

    // Игровой экран (отдельный SurfaceView, добавляется/удаляется динамически)
    private var gameView: GameView? = null
    private var trolleybus: Trolleybus? = null

    // Загрузка
    private var loadProgress = 0f
    private var loadDone = false

    // Фон (пиксельный арт вечерней улицы)
    private val bgBitmap by lazy { buildBackgroundBitmap() }

    // Размытие фона
    private val blurPaint = Paint().apply { isAntiAlias = true }

    // Краски
    private val pText = Paint().apply {
        color = Color.WHITE; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val pTitle = Paint().apply {
        color = Color.WHITE; isAntiAlias = true; textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val pGlass = Paint().apply { isAntiAlias = true }
    private val pOverlay = Paint().apply { color = Color.argb(140, 0, 0, 20) }
    private val pDim = Paint().apply { color = Color.argb(160, 0, 0, 0) }

    // Кнопки главного меню
    private var btnStart = RectF()
    private var btnExit  = RectF()

    // Кнопки выбора маршрута
    private val routeBtns = mutableListOf<RectF>()
    private var btnBack   = RectF()

    // Game over
    private var gameOverReason: GameOverReason? = null
    private var btnRestart = RectF()
    private var btnMenu    = RectF()

    // Смена маршрута поверх игры
    private var showRouteOverlay = false
    private val routeOverlayBtns = mutableListOf<RectF>()

    // Анимация
    private var animT = 0f

    init {
        resume()
        // Запускаем загрузку
        Thread {
            Thread.sleep(1500)
            loadDone = true
        }.start()
    }

    fun pause()  { running = false; thread?.join() }
    fun resume() { running = true; thread = Thread(this); thread!!.start() }

    override fun run() {
        while (running) {
            animT += 0.016f
            if (currentScreen == Screen.LOADING && loadDone) {
                switchScreen(Screen.MAIN_MENU)
            }
            postInvalidate()
            Thread.sleep(16)
        }
    }

    fun switchScreen(screen: Screen) {
        currentScreen = screen
        if (screen != Screen.GAME) {
            gameView?.stop()
            gameView = null
            trolleybus = null
        }
    }

    fun startGame(routeType: RouteType) {
        trolleybus = Trolleybus(routeType)
        // GameView создаётся и добавляется через активити
        val activity = context as MainActivity
        trolleybus?.let { tb ->
            val gv = GameView(context, tb)
            gv.onChangeRoute = { showRouteOverlay = true }
            gv.onGameOver = { reason ->
                gameOverReason = reason
                activity.runOnUiThread { switchScreen(Screen.GAME_OVER) }
            }
            gameView = gv
            activity.runOnUiThread {
                activity.setContentView(gv)
                // Добавляем this поверх для оверлея смены маршрута
            }
        }
        currentScreen = Screen.GAME
    }

    // ─── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (currentScreen) {
            Screen.LOADING      -> drawLoading(canvas)
            Screen.MAIN_MENU    -> drawMainMenu(canvas)
            Screen.ROUTE_SELECT -> drawRouteSelect(canvas)
            Screen.GAME_OVER    -> drawGameOver(canvas)
            Screen.GAME         -> {
                if (showRouteOverlay) drawRouteOverlay(canvas)
            }
        }
    }

    // ─── Загрузка ──────────────────────────────────────────────────────────────
    private fun drawLoading(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // Фон
        canvas.drawBitmap(bgBitmap, null,
            RectF(0f, 0f, w, h), blurPaint)
        canvas.drawRect(0f, 0f, w, h, pOverlay)

        pTitle.textSize = h * 0.06f
        pTitle.color = Color.WHITE
        canvas.drawText("Загрузка...", w / 2, h / 2, pTitle)

        // Мигание точек
        val dots = ".".repeat(((animT * 2).toInt() % 4))
        pText.textSize = h * 0.04f
        pText.color = Color.argb(200, 255, 255, 255)
        canvas.drawText(dots, w / 2, h * 0.58f, pText)
    }

    // ─── Главное меню ──────────────────────────────────────────────────────────
    private fun drawMainMenu(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()

        // Фон
        canvas.drawBitmap(bgBitmap, null, RectF(0f, 0f, w, h), blurPaint)
        canvas.drawRect(0f, 0f, w, h, pOverlay)

        // Заголовок
        pTitle.textSize = h * 0.07f
        pTitle.color = Color.WHITE
        canvas.drawText("Мінскі тралейбус", w / 2, h * 0.25f, pTitle)
        pText.textSize = h * 0.03f
        pText.color = Color.argb(180, 255, 220, 100)
        canvas.drawText("Сімулятар вадзіцеля", w / 2, h * 0.32f, pText)

        // Кнопки
        val bw = w * 0.6f; val bh = h * 0.09f; val bx = w / 2 - bw / 2
        btnStart = RectF(bx, h * 0.55f, bx + bw, h * 0.55f + bh)
        btnExit  = RectF(bx, h * 0.67f, bx + bw, h * 0.67f + bh)

        drawGlassBtn(canvas, btnStart, "На маршрут!", Color.argb(120, 0, 150, 80))
        drawGlassBtn(canvas, btnExit,  "Выхад",       Color.argb(120, 150, 0, 0))
    }

    // ─── Выбор маршрута ────────────────────────────────────────────────────────
    private fun drawRouteSelect(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()

        canvas.drawBitmap(bgBitmap, null, RectF(0f, 0f, w, h), blurPaint)
        canvas.drawRect(0f, 0f, w, h, pOverlay)

        pTitle.textSize = h * 0.05f
        pTitle.color = Color.WHITE
        canvas.drawText("На якім паедзеш?", w / 2, h * 0.14f, pTitle)

        routeBtns.clear()
        val bw = w * 0.82f; val bh = h * 0.10f
        val bx = w / 2 - bw / 2
        val colors = listOf(
            Color.argb(130, 0, 100, 180),
            Color.argb(130, 0, 120, 160),
            Color.argb(130, 0, 80, 160),
            Color.argb(130, 100, 0, 160)
        )

        MapData.routes.forEachIndexed { i, route ->
            val by = h * 0.22f + i * (bh + h * 0.025f)
            val rect = RectF(bx, by, bx + bw, by + bh)
            routeBtns.add(rect)
            drawGlassBtn(canvas, rect, "", colors[i])

            // Текст маршрута
            pTitle.textSize = h * 0.032f
            pTitle.color = Color.WHITE
            canvas.drawText(route.menuLine1, w / 2, by + bh * 0.42f, pTitle)
            if (route.menuLine2.isNotEmpty()) {
                pText.textSize = h * 0.024f
                pText.color = Color.argb(200, 255, 220, 150)
                canvas.drawText(route.menuLine2, w / 2, by + bh * 0.78f, pText)
            }
        }

        // Кнопка назад
        val backH = h * 0.07f
        btnBack = RectF(bx, h * 0.88f, bx + bw, h * 0.88f + backH)
        drawGlassBtn(canvas, btnBack, "← Назад", Color.argb(100, 80, 80, 80))
    }

    // ─── Оверлей смены маршрута (поверх игры) ─────────────────────────────────
    private fun drawRouteOverlay(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, pDim)

        pTitle.textSize = h * 0.05f
        pTitle.color = Color.WHITE
        canvas.drawText("Змяніць маршрут", w / 2, h * 0.14f, pTitle)

        routeOverlayBtns.clear()
        val bw = w * 0.82f; val bh = h * 0.10f
        val bx = w / 2 - bw / 2

        MapData.routes.forEachIndexed { i, route ->
            val by = h * 0.20f + i * (bh + h * 0.025f)
            val rect = RectF(bx, by, bx + bw, by + bh)
            routeOverlayBtns.add(rect)
            drawGlassBtn(canvas, rect, route.menuLine1,
                if (route.type == trolleybus?.currentRoute)
                    Color.argb(180, 0, 180, 80)
                else Color.argb(130, 60, 60, 120))
        }

        val backH = h * 0.07f
        btnBack = RectF(bx, h * 0.88f, bx + bw, h * 0.88f + backH)
        drawGlassBtn(canvas, btnBack, "← Адмяніць", Color.argb(100, 80, 80, 80))
    }

    // ─── Game Over ─────────────────────────────────────────────────────────────
    private fun drawGameOver(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawBitmap(bgBitmap, null, RectF(0f, 0f, w, h), blurPaint)
        canvas.drawRect(0f, 0f, w, h, pDim)

        pTitle.textSize = h * 0.06f
        pTitle.color = Color.rgb(255, 80, 80)
        canvas.drawText("Памылка ў кіраванні!", w / 2, h * 0.22f, pTitle)

        gameOverReason?.let { reason ->
            pTitle.textSize = h * 0.045f
            pTitle.color = Color.WHITE
            canvas.drawText(reason.title, w / 2, h * 0.34f, pTitle)

            pText.textSize = h * 0.028f
            pText.color = Color.argb(220, 255, 220, 180)
            // Многострочный текст
            reason.desc.split("\n").forEachIndexed { i, line ->
                canvas.drawText(line, w / 2, h * 0.44f + i * h * 0.05f, pText)
            }
        }

        // Очки
        pTitle.textSize = h * 0.04f
        pTitle.color = Color.rgb(255, 220, 0)
        canvas.drawText("Ачкі: ${trolleybus?.score ?: 0}", w / 2, h * 0.64f, pTitle)

        val bw = w * 0.55f; val bh = h * 0.09f; val bx = w / 2 - bw / 2
        btnRestart = RectF(bx, h * 0.72f, bx + bw, h * 0.72f + bh)
        btnMenu    = RectF(bx, h * 0.83f, bx + bw, h * 0.83f + bh)

        drawGlassBtn(canvas, btnRestart, "Паўтарыць",    Color.argb(130, 0, 130, 60))
        drawGlassBtn(canvas, btnMenu,    "Галоўнае меню",Color.argb(130, 80, 80, 80))
    }

    // ─── Стеклянная кнопка ────────────────────────────────────────────────────
    private fun drawGlassBtn(canvas: Canvas, rect: RectF, text: String, color: Int) {
        // Основной фон
        pGlass.color = color
        pGlass.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 20f, 20f, pGlass)

        // Блик сверху
        val shineRect = RectF(rect.left + 4f, rect.top + 4f,
                              rect.right - 4f, rect.centerY())
        pGlass.color = Color.argb(60, 255, 255, 255)
        canvas.drawRoundRect(shineRect, 16f, 16f, pGlass)

        // Рамка
        pGlass.color = Color.argb(100, 255, 255, 255)
        pGlass.style = Paint.Style.STROKE
        pGlass.strokeWidth = 1.5f
        canvas.drawRoundRect(rect, 20f, 20f, pGlass)
        pGlass.style = Paint.Style.FILL

        // Текст
        if (text.isNotEmpty()) {
            pText.textSize = height * 0.035f
            pText.color = Color.WHITE
            canvas.drawText(text, rect.centerX(), rect.centerY() + pText.textSize * 0.35f, pText)
        }
    }

    // ─── Пиксельный фон (вечерняя улица Минска) ───────────────────────────────
    private fun buildBackgroundBitmap(): Bitmap {
        val bw = 120; val bh = 200
        val bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        val p = Paint()

        // Небо
        p.color = Color.rgb(15, 15, 40); c.drawRect(0f, 0f, bw.toFloat(), bh * 0.55f, p)

        // Звёзды
        p.color = Color.rgb(255, 255, 200)
        val rng = Random(42)
        repeat(30) {
            val sx = rng.nextFloat() * bw
            val sy = rng.nextFloat() * bh * 0.4f
            c.drawCircle(sx, sy, 0.8f, p)
        }

        // Луна
        p.color = Color.rgb(240, 230, 180)
        c.drawCircle(bw * 0.8f, bh * 0.08f, 6f, p)

        // Дорога
        p.color = Color.rgb(50, 50, 55)
        c.drawRect(0f, bh * 0.72f, bw.toFloat(), bh.toFloat(), p)

        // Тротуар
        p.color = Color.rgb(80, 80, 85)
        c.drawRect(0f, bh * 0.68f, bw.toFloat(), bh * 0.72f, p)

        // Здания слева
        drawBuilding(c, p, 2f,  bh * 0.28f, 22f, bh * 0.68f, Color.rgb(60, 50, 80))
        drawBuilding(c, p, 26f, bh * 0.38f, 18f, bh * 0.68f, Color.rgb(50, 60, 70))
        drawBuilding(c, p, 46f, bh * 0.32f, 20f, bh * 0.68f, Color.rgb(70, 55, 65))

        // Здания справа
        drawBuilding(c, p, 72f, bh * 0.30f, 20f, bh * 0.68f, Color.rgb(55, 50, 75))
        drawBuilding(c, p, 94f, bh * 0.40f, 24f, bh * 0.68f, Color.rgb(60, 65, 70))

        // Окна зданий (жёлтые огни)
        p.color = Color.rgb(255, 220, 100)
        drawWindows(c, p, 2f,  bh * 0.28f, 22f, bh * 0.68f, rng)
        drawWindows(c, p, 26f, bh * 0.38f, 18f, bh * 0.68f, rng)
        drawWindows(c, p, 46f, bh * 0.32f, 20f, bh * 0.68f, rng)
        drawWindows(c, p, 72f, bh * 0.30f, 20f, bh * 0.68f, rng)
        drawWindows(c, p, 94f, bh * 0.40f, 24f, bh * 0.68f, rng)

        // Фонари
        p.color = Color.rgb(255, 200, 80)
        c.drawCircle(30f, bh * 0.65f, 3f, p)
        c.drawCircle(90f, bh * 0.65f, 3f, p)
        p.color = Color.argb(60, 255, 200, 80)
        c.drawCircle(30f, bh * 0.65f, 8f, p)
        c.drawCircle(90f, bh * 0.65f, 8f, p)

        // Провода КС
        p.color = Color.rgb(160, 160, 160)
        p.strokeWidth = 1f
        p.style = Paint.Style.STROKE
        c.drawLine(0f, bh * 0.60f, bw.toFloat(), bh * 0.61f, p)
        c.drawLine(0f, bh * 0.62f, bw.toFloat(), bh * 0.63f, p)
        p.style = Paint.Style.FILL

        // Троллейбус БКМ 321 (силуэт)
        drawTrolleybus(c, p, bw * 0.15f, bh * 0.63f)

        // Размытие (блочное, для пиксельного эффекта)
        return Bitmap.createScaledBitmap(bm, bw * 4, bh * 4, true)
            .let { Bitmap.createScaledBitmap(it, bw, bh, true) }
    }

    private fun drawBuilding(c: Canvas, p: Paint, x: Float, y: Float,
                              w: Float, bottom: Float, color: Int) {
        p.color = color
        c.drawRect(x, y, x + w, bottom, p)
        // Крыша чуть темнее
        p.color = Color.rgb(
            (Color.red(color) * 0.7f).toInt(),
            (Color.green(color) * 0.7f).toInt(),
            (Color.blue(color) * 0.7f).toInt()
        )
        c.drawRect(x, y, x + w, y + 3f, p)
    }

    private fun drawWindows(c: Canvas, p: Paint, bx: Float, by: Float,
                             bw: Float, bottom: Float, rng: Random) {
        val rows = ((bottom - by) / 8).toInt()
        val cols = (bw / 6).toInt()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (rng.nextFloat() > 0.35f) {
                    val wx = bx + col * 6f + 1f
                    val wy = by + row * 8f + 2f
                    p.color = if (rng.nextFloat() > 0.2f)
                        Color.rgb(255, 220, 100)
                    else Color.rgb(150, 180, 255)
                    c.drawRect(wx, wy, wx + 3f, wy + 4f, p)
                }
            }
        }
    }

    private fun drawTrolleybus(c: Canvas, p: Paint, x: Float, y: Float) {
        // Кузов
        p.color = Color.rgb(200, 30, 30)
        c.drawRect(x, y - 12f, x + 35f, y, p)
        // Крыша
        p.color = Color.rgb(220, 220, 220)
        c.drawRect(x + 1f, y - 15f, x + 34f, y - 12f, p)
        // Окна
        p.color = Color.rgb(180, 220, 255)
        for (i in 0..3) c.drawRect(x + 3f + i * 8f, y - 11f, x + 8f + i * 8f, y - 4f, p)
        // Колёса
        p.color = Color.rgb(30, 30, 30)
        c.drawCircle(x + 6f, y + 2f, 3f, p)
        c.drawCircle(x + 28f, y + 2f, 3f, p)
        // Штанги КС
        p.color = Color.rgb(180, 180, 180)
        p.strokeWidth = 1f
        p.style = Paint.Style.STROKE
        c.drawLine(x + 12f, y - 15f, x + 8f,  y - 22f, p)
        c.drawLine(x + 22f, y - 15f, x + 26f, y - 22f, p)
        p.style = Paint.Style.FILL
    }

    // ─── Touch ────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val x = event.x; val y = event.y

        when (currentScreen) {
            Screen.MAIN_MENU -> {
                if (btnStart.contains(x, y)) switchScreen(Screen.ROUTE_SELECT)
                if (btnExit.contains(x, y))  (context as Activity).finish()
            }
            Screen.ROUTE_SELECT -> {
                routeBtns.forEachIndexed { i, rect ->
                    if (rect.contains(x, y)) startGame(MapData.routes[i].type)
                }
                if (btnBack.contains(x, y)) switchScreen(Screen.MAIN_MENU)
            }
            Screen.GAME -> {
                if (showRouteOverlay) {
                    routeOverlayBtns.forEachIndexed { i, rect ->
                        if (rect.contains(x, y)) {
                            trolleybus?.changeRoute(MapData.routes[i].type)
                            showRouteOverlay = false
                        }
                    }
                    if (btnBack.contains(x, y)) showRouteOverlay = false
                }
            }
            Screen.GAME_OVER -> {
                if (btnRestart.contains(x, y)) {
                    trolleybus?.currentRoute?.let { startGame(it) }
                }
                if (btnMenu.contains(x, y)) switchScreen(Screen.MAIN_MENU)
            }
            else -> {}
        }
        return true
    }
}
