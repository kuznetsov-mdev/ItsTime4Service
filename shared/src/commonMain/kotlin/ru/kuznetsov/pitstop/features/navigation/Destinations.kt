package ru.kuznetsov.pitstop.features.navigation

/** Every screen of the app as a navigation route; destinations with arguments build their concrete route via `createRoute`. */
sealed class Destination(val route: String) {
    data object Cars : Destination("cars")

    data object AddCar : Destination("add-car")

    data object CarDetail : Destination("cars/{$ARG_CAR_ID}") {
        fun createRoute(carId: String) = "cars/$carId"
    }

    data object EditCar : Destination("cars/{$ARG_CAR_ID}/edit") {
        fun createRoute(carId: String) = "cars/$carId/edit"
    }
    data object History : Destination("cars/{$ARG_CAR_ID}/history") {
        fun createRoute(carId: String) = "cars/$carId/history"
    }

    data object Settings : Destination("settings")

    companion object {
        const val ARG_CAR_ID = "carId"
    }
}
