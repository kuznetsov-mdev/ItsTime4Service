package ru.kuznetsov.itstime4service.ui.components.fields

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import ru.kuznetsov.itstime4service.ui.test.UiTest
import ru.kuznetsov.itstime4service.ui.theme.ItsTime4ServiceTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies return-key behavior for multiline comments and ordinary single-line service fields. */
@OptIn(ExperimentalTestApi::class)
class ServiceRecordFieldsTest : UiTest() {
    @Test
    fun returnInCommentAddsALineWithoutSubmittingTheForm() = runComposeUiTest {
        val comment = mutableStateOf("First line")
        var submissions = 0
        setContent {
            ItsTime4ServiceTheme(darkTheme = false) {
                ServiceRecordFields("Garage", "", comment.value, {}, {}, { comment.value = it }, { submissions++ })
            }
        }
        val field = onNode(hasSetTextAction() and hasText("First line"))
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Default))
        field.performClick().performTextInputSelection(TextRange(comment.value.length))
        field.performKeyInput { pressKey(Key.Enter) }
        onNode(hasSetTextAction() and hasText("First line", substring = true)).performTextInput("Second line")

        runOnIdle {
            assertEquals("First line\nSecond line", comment.value)
            assertEquals(0, submissions)
        }
    }

    @Test
    fun singleLineServiceStillSubmitsWithDone() = runComposeUiTest {
        var submissions = 0
        setContent {
            ItsTime4ServiceTheme(darkTheme = false) {
                ServiceRecordFields("Garage", "", "", {}, {}, {}, { submissions++ })
            }
        }
        onNode(hasSetTextAction() and hasText("Garage")).performImeAction()
        assertEquals(1, submissions)
    }
}
