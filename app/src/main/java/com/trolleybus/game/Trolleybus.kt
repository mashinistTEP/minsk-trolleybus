package com.trolleybus.game

import kotlin.math.*

enum class TrolleyState {
    MOVING, AT_STOP, DOORS_OPEN, AT_TERMINAL, IN_DEPOT, GAME_OVER
}

enum class PowerState { ON, OFF }

data class GameOverReason(val title: String, val desc: String)

class Trolleybus(routeType: RouteType) {

    val number = MapData.TROLLEYBUS_NUMBERS.random()
    var currentRoute = routeType
    var routeInfo = MapData.routes.first { it.type == routeType }

    var x = 0f
    var y = 0f
    var angle = 0f
    var speed = 0f
    val maxSpeed = 0.05f
    val acceleration = 0.002f
    val braking = 0.004f

    var state = TrolleyState.AT_TERMINAL
    var powerState = PowerState.ON
    var doorsOpen = false
    var pantographsUp = true

    var stops = MapData.getRouteStops(routeType).toMutableList()
    var currentStopIndex = 0
    var score = 0

    var passengers = 0
    val maxPassengers = 80

    var doorTimer = 0f
    val doorOpenTime = 3f

    var gameOverReason: GameOverReason? = null
    var switchWarning: Switch? = null
    var switchWarningTimer = 0f

    init {
        resetPosition()
    }

    private fun resetPosition() {
        val startStop = stops[0]
        x = startStop.x
        y = startStop.y
        // Угол зависит от маршрута
        angle = when (currentRoute) {
            RouteType.ROUTE_1_TO_VAKZAL    -> (3f * PI / 2f).toFloat() // вверх
            RouteType.ROUTE_1_TO_ANGARSKAYA -> (PI / 2f).toFloat()      // вниз
            RouteType.ROUTE_1_FROM_DEPOT    -> (PI).toFloat()            // влево (из парка)
            RouteType.ROUTE_1P_TO_DEPOT     -> (PI / 2f).toFloat()      // вниз
        }
    }

    fun accelerate() {
        if (state == TrolleyState.GAME_OVER) return
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
        powerState = PowerState.OFF
        speed = max(speed - acceleration * 0.3f, 0f)
    }

    fun toggleDoors() {
        if (state != TrolleyState.AT_STOP &&
            state != TrolleyState.DOORS_OPEN &&
            state != TrolleyState.AT_TERMINAL) return
        doorsOpen = !doorsOpen
        if (doorsOpen) {
            state = TrolleyState.DOORS_OPEN
            doorTimer = 0f
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
        currentStopIndex = stops.indices.minByOrNull {
            MapData.dist(x, y, stops[it].x, stops[it].y)
        } ?: 0
    }

    fun update(delta: Float) {
        if (state == TrolleyState.GAME_OVER) return

        if (doorsOpen) {
            doorTimer += delta
            if (doorTimer >= doorOpenTime) {
                doorsOpen = false
                state = TrolleyState.AT_STOP
            }
        }

        if (speed > 0f) {
            val newX = x + cos(angle) * speed
            val newY = y + sin(angle) * speed
            if (!MapData.isWall(newX, newY)) {
                x = newX; y = newY
                if (state != TrolleyState.DOORS_OPEN) state = TrolleyState.MOVING
            } else {
                speed = 0f
            }
        }

        checkSwitches()
        checkStops()

        if (MapData.getCell(x.toInt(), y.toInt()) == 3 && passengers > 0) {
            triggerGameOver(GameOverReason(
                "Пасажыры ў парку!",
                "Нельга заязджаць у парк з пасажырамі."
            ))
        }
    }

    private fun checkSwitches() {
        for (sw in MapData.switches) {
            val dist = MapData.dist(x, y, sw.x, sw.y)
            if (dist < 1.5f) switchWarning = sw
            if (dist < 0.6f && speed > 0.01f) {
                val correct = if (sw.requiresPower)
                    powerState == PowerState.ON
                else
                    powerState == PowerState.OFF
                if (!correct) {
                    pantographsUp = false
                    triggerGameOver(GameOverReason(
                        "Слёт токапрыёмнікаў!",
                        if (sw.requiresPower)
                            "Трэба пад токам (газ)"
                        else
                            "Трэба накатам (без газу)"
                    ))
                    return
                }
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
            state = if (nextStop.isTerminal || nextStop.isDepotStop)
                TrolleyState.AT_TERMINAL else TrolleyState.AT_STOP

            if (currentStopIndex == stops.size - 1 && !nextStop.isDepotStop) {
                score++
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
