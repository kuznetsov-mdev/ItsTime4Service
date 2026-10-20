package ru.kuznetsov.itstime4service.ui.components.fields

/**
 * What the platform's numeric keyboard offers. [hasDoneKey] is `false` on iOS, whose number pad has no return key:
 * a focused numeric field then shows its own check button to finish the input (see [FieldBox]).
 */
internal expect object NumericKeyboard {
    val hasDoneKey: Boolean
}
