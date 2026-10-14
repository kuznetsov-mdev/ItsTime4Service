package ru.kuznetsov.pitstop.features.navigation

/** Every screen of the app as a navigation route; destinations with arguments build their concrete route via `createRoute`. */
sealed class Destination(val route: String) {
    data object Cars : Destination("cars")

    data object AddCar : Destination("add-car")

    data object CarDetail : Destination("cars/{$ARG_CAR_ID}") {
        fun createRoute(carId: String) = "cars/$carId"
    }

    data object NewTask : Destination("cars/{$ARG_CAR_ID}/new-task") {
        fun createRoute(carId: String) = "cars/$carId/new-task"
    }
    data object EditTask : Destination("cars/{$ARG_CAR_ID}/tasks/{$ARG_TASK_ID}") {
        fun createRoute(carId: String, taskId: String) = "cars/$carId/tasks/$taskId"
    }
    data object History : Destination("history?$ARG_CAR_ID={$ARG_CAR_ID}") {
        fun createRoute(carId: String? = null) = if (carId == null) "history" else "history?$ARG_CAR_ID=$carId"
    }

    data object Settings : Destination("settings")

    companion object {
        const val ARG_CAR_ID = "carId"
        const val ARG_TASK_ID = "taskId"
    }
}
