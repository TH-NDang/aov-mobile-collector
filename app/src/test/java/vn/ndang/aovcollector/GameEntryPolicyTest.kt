package vn.ndang.aovcollector

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEntryPolicyTest {
    private fun macro(mode: String, first: JSONObject = JSONObject().put("type", "wait").put("ms", 0)): Macro = Macro(
        JSONObject().put("name", "entry-policy").put("targetPackage", "com.garena.game.kgvn")
            .put("collectionMode", mode).put("steps", JSONArray().put(first).put(JSONObject().put("type", "wait").put("ms", 0)))
            .toString()
    )

    @Test fun legacyAndMissingAccountFlagsNeverBypassVerification() {
        for (mode in listOf("all", "chess", "pro-builds", "custom", "single")) {
            val legacy = macro(mode)
            assertTrue(GameEntryPolicy.requiresAccount(legacy))
            legacy.json.put("checkAccount", false)
            assertTrue(GameEntryPolicy.requiresAccount(legacy))
        }
    }

    @Test fun semanticNavigatorsUseTheSharedGateWithoutExplicitEnter() {
        val pro = Macro(BuiltInMacros.proBuilds())
        assertEquals(GameEntryPolicy.Destination.LOBBY, GameEntryPolicy.destination(pro, 0))
        val list = Macro(BuiltInMacros.create("heroes", 2, batch = true, openGame = false))
        assertEquals(GameEntryPolicy.Destination.LIST, GameEntryPolicy.destination(list, 0))
    }

    @Test fun importedModeMetadataNeverAuthorizesNavigationBeforeCoordinates() {
        for (mode in listOf("all", "pick", "ranking", "board", "chess", "pro-builds")) {
            val input = macro(mode, JSONObject().put("type", "tap").put("x", .5).put("y", .5))
            assertEquals(mode, GameEntryPolicy.Destination.CURRENT, GameEntryPolicy.destination(input, 0))
        }
    }

    @Test fun coordinateMacrosAndEveryResumedCursorKeepTheirScreen() {
        for (mode in listOf("custom", "single")) {
            assertEquals(GameEntryPolicy.Destination.CURRENT, GameEntryPolicy.destination(macro(mode), 0))
        }
        for (mode in listOf("all", "pick", "ranking", "board", "chess", "pro-builds", "custom", "single")) {
            assertEquals(GameEntryPolicy.Destination.CURRENT, GameEntryPolicy.destination(macro(mode), 1))
        }
    }

    @Test fun explicitEnterDestinationIsHonoredWithoutRewritingMacro() {
        val input = macro("custom", JSONObject().put("type", "enter").put("to", "lobby"))
        val before = input.raw
        assertEquals(GameEntryPolicy.Destination.LOBBY, GameEntryPolicy.destination(input, 0))
        assertEquals(before, input.raw)
        assertEquals(2, input.steps.length())
    }
}
