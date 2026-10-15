package ru.kuznetsov.itstime4service

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform