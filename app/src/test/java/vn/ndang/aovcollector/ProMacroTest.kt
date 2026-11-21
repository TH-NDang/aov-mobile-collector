package vn.ndang.aovcollector

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProMacroTest {
    @Test fun completeComboTaskDoesNotLaunchOrReadAccounts() {
        val macro = Macro(BuiltInMacros.proBuilds())
        assertEquals("pro-builds", macro.name)
        assertEquals(1, macro.steps.length())
        assertEquals("pro-builds", macro.steps.getJSONObject(0).getString("type"))
        assertEquals(BuiltInMacros.ALL, macro.steps.getJSONObject(0).getInt("heroes"))
        assertFalse(macro.json.has("checkAccount"))
        assertFalse(macro.json.has("openGame"))
        assertTrue(macro.photosVary)
    }

    @Test fun theNumberOfCombosIsChosenAndOldMacrosTakeFive() {
        val three = Macro(BuiltInMacros.proBuilds(combos = 3))
        assertEquals(3, BuiltInMacros.proCombos(three.json))
        assertEquals(3, BuiltInMacros.proCombos(three.steps.getJSONObject(0)))
        val old = JSONObject(BuiltInMacros.proBuilds()).apply { remove("proCombos") }
        old.getJSONArray("steps").getJSONObject(0).remove("combos")
        assertEquals(ProSimpleReader.MAX, BuiltInMacros.proCombos(Macro(old.toString()).json))
        assertTrue(runCatching { BuiltInMacros.proBuilds(combos = 0) }.isFailure)
    }

    @Test fun invalidCountsAndWrongModeAreRejected() {
        assertTrue(runCatching { BuiltInMacros.proBuilds(0) }.isFailure)
        assertTrue(runCatching { BuiltInMacros.proBuilds(BuiltInMacros.ALL + 1) }.isFailure)
        val json = JSONObject(BuiltInMacros.proBuilds())
        json.put("collectionMode", "single")
        assertTrue(runCatching { Macro(json.toString()) }.isFailure)
    }

    @Test fun screenshotNamesAreHeroRankAndCannotEscapeFolder() {
        assertEquals("ngo-khong", ProCaptureFiles.hero("pro__ngo-khong__top-1"))
        assertEquals("ngo-khong-top-1", ProCaptureFiles.basename("pro__ngo-khong__top-1"))
        assertEquals("ngo-khong-empty-pro-list", ProCaptureFiles.basename("pro__ngo-khong__empty-pro-list"))
        assertNull(ProCaptureFiles.hero("pro__../outside__top-1"))
    }
}
