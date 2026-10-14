package com.freedomplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** Títulos de vídeo tal y como llegan de YouTube, y cómo entran en la biblioteca. */
class CleaningTest {
    private fun clean(title: String, channel: String) = TitleCleaner.clean(title, channel)

    @Test
    fun officialTagsInAnyLanguageGo() {
        assertEquals(CleanTitle("Never Gonna Give You Up", "Rick Astley"), clean("Rick Astley - Never Gonna Give You Up (Official Music Video)", "Rick Astley"))
        assertEquals(CleanTitle("DESPECHÁ", "ROSALÍA"), clean("ROSALÍA - DESPECHÁ (Official Video)", "ROSALÍA"))
        assertEquals(CleanTitle("Tusa", "KAROL G"), clean("KAROL G - Tusa (Video Oficial)", "KarolGVEVO"))
        assertEquals(CleanTitle("Bohemian Rhapsody", "Queen"), clean("Queen – Bohemian Rhapsody [Official Video]", "Queen Official"))
        assertEquals(CleanTitle("Alors on danse", "Stromae"), clean("Stromae - Alors on danse (Clip Officiel)", "stromaeVEVO"))
        assertEquals(CleanTitle("Believer", "Imagine Dragons"), clean("Imagine Dragons - Believer (Lyrics/Letra)", "Imagine Dragons"))
    }

    @Test
    fun severalArtistsBeforeTheDashAreKept() {
        assertEquals(CleanTitle("Dákiti", "Bad Bunny x Jhay Cortez"), clean("Bad Bunny x Jhay Cortez - Dákiti (Video Oficial)", "Bad Bunny"))
        assertEquals(CleanTitle("Music Sessions #53", "Shakira, Bizarrap"), clean("Shakira, Bizarrap - Music Sessions #53 (Official Video)", "Bizarrap"))
        assertEquals(CleanTitle("Despacito ft. Daddy Yankee", "Luis Fonsi"), clean("Luis Fonsi - Despacito ft. Daddy Yankee", "LuisFonsiVEVO"))
    }

    @Test
    fun looseNoiseQuotesAndRepeatedArtistAtTheEnd() {
        assertEquals(CleanTitle("Shut Down", "BLACKPINK"), clean("BLACKPINK - 'Shut Down' M/V", "BLACKPINK"))
        assertEquals(CleanTitle("Numb", "Linkin Park"), clean("Numb [Official Music Video] [4K Upgrade] – Linkin Park", "Linkin Park"))
        assertEquals(CleanTitle("Yellow", "Coldplay"), clean("Coldplay - Yellow - Official Video", "Coldplay"))
        assertEquals(CleanTitle("Blinding Lights", "The Weeknd"), clean("The Weeknd - Blinding Lights (Official Audio) HD", "TheWeekndVEVO"))
    }

    @Test
    fun whatGivesInformationStays() {
        assertEquals(CleanTitle("Get Lucky ft. Pharrell Williams", "Daft Punk"), clean("Daft Punk - Get Lucky (Official Audio) ft. Pharrell Williams", "Daft Punk"))
        assertEquals(CleanTitle("Home Video", "Lana Del Rey"), clean("Home Video", "Lana Del Rey"), "un título que acaba en «Video» se queda")
        assertEquals(CleanTitle("Bailando (Remix)", "Enrique Iglesias"), clean("Enrique Iglesias - Bailando (Remix) (Audio)", "Enrique Iglesias"))
        assertEquals(CleanTitle("Callejón 04 — cover acústico", "Sofía Rueda"), clean("Callejón 04 — cover acústico (Video Oficial) HD", "Sofía Rueda"))
        assertEquals(CleanTitle("Don't Stop Me Now (Live at Wembley)", "Queen"), clean("Queen - Don't Stop Me Now (Live at Wembley) (Official Video)", "Queen"))
    }

    @Test
    fun aTitleThatIsOnlyNoiseIsNotLost() {
        assertEquals("Official Video", clean("Official Video", "Canal").title)
        assertEquals(CleanTitle("Topic Song", "Night Signal"), clean("Topic Song", "Night Signal - Topic"))
    }
}
