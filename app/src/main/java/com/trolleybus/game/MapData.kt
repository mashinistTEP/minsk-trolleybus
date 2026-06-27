package com.trolleybus.game

import kotlin.math.sqrt

enum class SwitchType {
    DIVERGING_RIGHT,
    DIVERGING_LEFT,
    JOIN,
    CROSS
}

data class Switch(
    val id: Int,
    val type: SwitchType,
    val x: Float,
    val y: Float,
    val requiresPower: Boolean,
    val desc: String
)

data class Stop(
    val name: String,
    val x: Float,
    val y: Float,
    val isTerminal: Boolean = false,
    val isDepotStop: Boolean = false
)

enum class RouteType {
    ROUTE_1_TO_VAKZAL,
    ROUTE_1_TO_ANGARSKAYA,
    ROUTE_1_FROM_DEPOT,
    ROUTE_1P_TO_DEPOT
}

data class RouteInfo(
    val type: RouteType,
    val boardNumber: String,
    val boardTopLine: String,
    val boardBottomLine: String,
    val menuLine1: String,
    val menuLine2: String = "",
    val isDepotRoute: Boolean = false,
    val startsFromDepot: Boolean = false
)

object MapData {

    val TROLLEYBUS_NUMBERS = listOf("5601", "5667", "5607", "5502")

    val stops = mapOf(
        "ДС Ангарская-4"            to Stop("ДС Ангарская-4",            11.5f, 14.0f, isTerminal = true),
        "Станцыя метро Магілеўская" to Stop("Станцыя метро Магілеўская", 11.5f, 11.0f),
        "Плошча Ванеева"            to Stop("Плошча Ванеева",            11.5f,  8.0f),
        "МотаВелаЗавод"            to Stop("МотаВелаЗавод",            11.5f,  6.0f),
        "БелМедПрэпараты"          to Stop("БелМедПрэпараты",          11.5f,  4.0f),
        "Вакзал"                    to Stop("Вакзал",                    11.5f,  2.0f, isTerminal = true),
        "Аранская"                  to Stop("Аранская",                  11.5f,  2.2f, isTerminal = true),
        "Тралейбусны парк нумар 5"  to Stop("Тралейбусны парк нумар 5", 17.5f,  9.5f, isDepotStop = true)
    )

    val route1ToVakzal = listOf(
        stops["ДС Ангарская-4"]!!,
        stops["Станцыя метро Магілеўская"]!!,
        stops["Плошча Ванеева"]!!,
        stops["МотаВелаЗавод"]!!,
        stops["БелМедПрэпараты"]!!,
        stops["Вакзал"]!!
    )

    // Маршрут назад начинается с Вакзала, потом Аранская (другая сторона)
    val route1ToAngarskaya = listOf(
        stops["Вакзал"]!!,
        stops["Аранская"]!!,
        stops["МотаВелаЗавод"]!!,
        stops["Плошча Ванеева"]!!,
        stops["Станцыя метро Магілеўская"]!!,
        stops["ДС Ангарская-4"]!!
    )

    val route1FromDepot = listOf(
        stops["Тралейбусны парк нумар 5"]!!,
        stops["Плошча Ванеева"]!!,
        stops["МотаВелаЗавод"]!!,
        stops["БелМедПрэпараты"]!!,
        stops["Вакзал"]!!
    )

    val route1PToDepot = listOf(
        stops["Вакзал"]!!,
        stops["БелМедПрэпараты"]!!,
        stops["МотаВелаЗавод"]!!,
        stops["Плошча Ванеева"]!!,
        stops["Тралейбусны парк нумар 5"]!!
    )

    val routes = listOf(
        RouteInfo(
            type            = RouteType.ROUTE_1_TO_VAKZAL,
            boardNumber     = "1",
            boardTopLine    = "ДС Ангарская-4",
            boardBottomLine = "Вакзал",
            menuLine1       = "1 маршрут (Да \"Вакзала\")"
        ),
        RouteInfo(
            type            = RouteType.ROUTE_1_TO_ANGARSKAYA,
            boardNumber     = "1",
            boardTopLine    = "Вакзал",
            boardBottomLine = "ДС Ангарская-4",
            menuLine1       = "1 маршрут (Да \"ДС Ангарская-4\")"
        ),
        RouteInfo(
            type            = RouteType.ROUTE_1_FROM_DEPOT,
            boardNumber     = "1",
            boardTopLine    = "ДС Ангарская-4",
            boardBottomLine = "Вакзал",
            menuLine1       = "1 маршрут (з парку) (Да \"Вакзала\")",
            startsFromDepot = true
        ),
        RouteInfo(
            type            = RouteType.ROUTE_1P_TO_DEPOT,
            boardNumber     = "",
            boardTopLine    = "У парк Солтыса, 26",
            boardBottomLine = "па 1 маршруце",
            menuLine1       = "1п маршрут (ў парк)",
            menuLine2       = "(Да \"Плошча Ванеева\" далей Тралейбусны парк)",
            isDepotRoute    = true
        )
    )

    fun getRouteStops(type: RouteType) = when (type) {
        RouteType.ROUTE_1_TO_VAKZAL     -> route1ToVakzal
        RouteType.ROUTE_1_TO_ANGARSKAYA -> route1ToAngarskaya
        RouteType.ROUTE_1_FROM_DEPOT    -> route1FromDepot
        RouteType.ROUTE_1P_TO_DEPOT     -> route1PToDepot
    }

    val switches = listOf(
        Switch(1, SwitchType.DIVERGING_RIGHT, 11.5f, 9.0f, false, "Ответвление ў парк — накатам"),
        Switch(2, SwitchType.DIVERGING_LEFT,  13.0f, 9.0f, true,  "Выезд з парка — пад токам"),
        Switch(3, SwitchType.JOIN,            11.5f, 9.5f, false, "Сходная пасля парка — без тока"),
        Switch(4, SwitchType.CROSS,           11.5f, 2.5f, false, "Скрыжаванне КС каля Вакзала — без тока")
    )

    val map = arrayOf(
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,1,1,1,1,3,3,3,3,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,3,3,3,3,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,1,1,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2),
        intArrayOf(2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2)
    )

    fun getCell(x: Int, y: Int): Int {
        if (x < 0 || x >= 24 || y < 0 || y >= 24) return 2
        return map[y][x]
    }

    fun isWall(x: Float, y: Float) = getCell(x.toInt(), y.toInt()).let { it == 2 || it == 0 }

    fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}
