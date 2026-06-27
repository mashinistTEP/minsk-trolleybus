package com.trolleybus.game

import kotlin.math.*

enum class TrolleyState {
    MOVING,       // едет
    AT_STOP,      // стоит на остановке
    DOORS_OPEN,   // двери открыты
    AT_TERMINAL,  // на конечной
    IN_DEPOT,     // в парке
    GAME_OVER     // ошибка
}

enum class PowerState {
    ON,   // под током
    OFF   // накатом (без тока)
}

data class GameOverReason(
    val title: String,
    val desc: String
)

class Trolleybus(routeType: RouteType) {

    // Идентификация
    val number = MapData.TROLLEYBUS_NUMBERS.random()
    var currentRoute = routeType
    var routeInfo = MapData.routes.first { it.type == routeType }

    // Позиция и движение
    var x = 0f
    var y = 0f
    var angle = 0f        // направление в радианах
    var speed = 0f
    val maxSpeed = 0.05f
    val acceleration = 0.002f
    val braking = 0.004f

    // Состояние
    var state = TrolleyState.AT_TERMINAL
    var powerState = PowerState.ON
    var doorsOpen = false
    var pantographsUp = true  // токоприёмники на КС

    // Маршрут
    var stops = MapData.getRouteStops(routeType).toMutableList()
    var currentStopIndex = 0
    var score = 0

    // Пассажиры
    var passengers = 0
    val maxPassengers = 80

    // Таймер дверей
    var doorTimer = 0f
    val doorOpenTime = 3f  // секунды

    // Сообщение об ошибке
    var gameOverReason: GameOverReason? = null

    // Предупреждение о стрелке
    var switchWarning: Switch? = null
    var switchWarningTimer = 0f

    init {
        // Начальная позиция
        val startStop = stops[0]
        x = startStop.x
        y = startStop.y
        angle = if (routeType == RouteType.ROUTE_1_TO_VAKZAL ||
                    routeType == RouteType.ROUTE_1_FROM_DEPOT)
            (3f * PI / 2f).toFloat()  // вверх (к Вакзалу)
        else
            (PI / 2f).toFloat()       // вниз (к Ангарской)
    }

    fun accelerate() {
        if (state != TrolleyState.MOVING && state != TrolleyState.AT_STOP) return
        if (!pantographsUp) return
        speed = min(speed + acceleration, maxSpeed)
        powerState = PowerState.ON
    }

    fun brake() {
        speed = max(speed - braking, 0f)
        if (speed == 0f && state == TrolleyState.MOVING) {
            state = TrolleyState.AT_STOP
        }
    }

    fun coast() {
        // Накат — двигаемся без тока
        powerState = PowerState.OFF
        speed = max(speed - acceleration * 0.3f, 0f)
    }

    fun toggleDoors() {
        if (state != TrolleyState.AT_STOP && state != TrolleyState.DOORS_OPEN) return
        doorsOpen = !doorsOpen
        if (doorsOpen) {
            state = TrolleyState.DOORS_OPEN
            doorTimer = 0f
            // Пассажиры выходят/заходят
            val exiting = min(passengers, (5..15).random())
            passengers -= exiting
            val entering = (3..12).random()
            passengers = min(passengers + entering, maxPassengers)
        } else {
            state = TrolleyState.AT_STOP
        }
    }

    fun changeRoute(newType: RouteType) {
        currentRoute = newType
        routeInfo = MapData.routes.first { it.type == newType }
        stops = MapData.getRouteStops(newType).toMutableList()
        // Найти ближайшую остановку как текущую
        currentStopIndex = stops.indices.minByOrNull {
            MapData.dist(x, y, stops[it].x, stops[it].y)
        } ?: 0
    }

    fun update(delta: Float) {
        if (state == TrolleyState.GAME_OVER) return

        // Таймер дверей
        if (doorsOpen) {
            doorTimer += delta
            if (doorTimer >= doorOpenTime) {
                doorsOpen = false
                state = TrolleyState.AT_STOP
            }
        }

        // Движение
        if (speed > 0f) {
            val newX = x + cos(angle) * speed
            val newY = y + sin(angle) * speed

            if (!MapData.isWall(newX, newY)) {
                x = newX
                y = newY
                state = TrolleyState.MOVING
            } else {
                speed = 0f
            }
        }

        // Проверка стрелок КС
        checkSwitches()

        // Проверка остановок
        checkStops()

        // Проверка: заехал в парк с пассажирами
        if (MapData.getCell(x.toInt(), y.toInt()) == 3 && passengers > 0) {
            triggerGameOver(
                GameOverReason(
                    "Пасажыры ў парку!",
                    "Нельга заязджаць у парк з пасажырамі. Высадзіце ўсіх пасажыраў перад паркам."
                )
            )
        }
    }

    private fun checkSwitches() {
        for (sw in MapData.switches) {
            val dist = MapData.dist(x, y, sw.x, sw.y)
            if (dist < 0.6f) {
                // Проверяем правильность прохода
                val correct = when {
                    sw.requiresPower  -> powerState == PowerState.ON
                    else              -> powerState == PowerState.OFF
                }
                if (!correct && speed > 0.01f) {
                    // Токоприёмники слетают
                    pantographsUp = false
                    triggerGameOver(
                        GameOverReason(
                            "Слёт токапрыёмнікаў!",
                            "Памылка на стрэлцы: ${sw.desc}\n" +
                            if (sw.requiresPower)
                                "Трэба праходзіць пад токам (разганяцца)"
                            else
                                "Трэба праходзіць без тока (накатам)"
                        )
                    )
                    return
                }
                // Показываем предупреждение заранее
                if (dist < 1.5f) switchWarning = sw
            }
        }
        if (switchWarning != null) {
            switchWarningTimer += 0.016f
            if (switchWarningTimer > 2f) {
                switchWarning = null
                switchWarningTimer = 0f
            }
        }
    }

    private fun checkStops() {
        if (currentStopIndex >= stops.size) return
        val nextStop = stops[currentStopIndex]
        val dist = MapData.dist(x, y, nextStop.x, nextStop.y)

        if (dist < 0.4f && speed < 0.005f) {
            // Приехали на остановку
            if (nextStop.isTerminal || nextStop.isDepotStop) {
                state = TrolleyState.AT_TERMINAL
                if (currentStopIndex == stops.size - 1) {
                    // Конечная — начисляем очко
                    if (!nextStop.isDepotStop) score++
                    state = TrolleyState.IN_DEPOT
                }
            } else {
                state = TrolleyState.AT_STOP
            }
            currentStopIndex++
        }
    }

    private fun triggerGameOver(reason: GameOverReason) {
        state = TrolleyState.GAME_OVER
        speed = 0f
        gameOverReason = reason
    }

    fun getBoardTopLine()    = routeInfo.boardTopLine
    fun getBoardBottomLine() = routeInfo.boardBottomLine
    fun getBoardNumber()     = routeInfo.boardNumber
}
