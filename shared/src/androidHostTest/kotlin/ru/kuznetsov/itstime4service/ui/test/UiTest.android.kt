package ru.kuznetsov.itstime4service.ui.test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs Compose UI tests on Robolectric at the newest Android version it supports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
actual abstract class UiTest actual constructor()

private const val ROBOLECTRIC_SDK = 36
