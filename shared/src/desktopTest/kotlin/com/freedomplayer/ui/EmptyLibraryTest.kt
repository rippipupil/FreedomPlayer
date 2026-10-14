package com.freedomplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «Biblioteca vacía» nombra el otro dispositivo: el PC en el móvil y el móvil en el PC. */
class EmptyLibraryTest {
    @get:Rule
    val rule = createComposeRule()

    private fun showEmpty(wide: Boolean) {
        val state = AppState().apply { setDemoEmpty(true) }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.requiredSize(if (wide) 1280.dp else 390.dp, if (wide) 800.dp else 844.dp)) {
                App(state, wide = wide, version = "test")
            }
        }
        rule.mainClock.advanceTimeBy(700)
        rule.onRoot().captureToImage()
    }

    private fun shown(text: String): Int = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun onThePcItNamesThePhone() {
        showEmpty(wide = true)
        assertTrue(shown("VINCULAR MÓVIL") > 0, "el botón dice «VINCULAR MÓVIL»")
        assertEquals(0, shown("VINCULAR PC"))
        assertTrue(shown("Busca tu primera canción o vincula el móvil con un QR.") > 0)
        assertEquals(0, shown("Busca tu primera canción o vincula el PC con un QR."))
    }

    @Test
    fun onThePhoneItNamesThePc() {
        showEmpty(wide = false)
        assertTrue(shown("VINCULAR PC") > 0, "el botón dice «VINCULAR PC»")
        assertEquals(0, shown("VINCULAR MÓVIL"))
        assertTrue(shown("Busca tu primera canción o vincula el PC con un QR.") > 0)
    }
}
