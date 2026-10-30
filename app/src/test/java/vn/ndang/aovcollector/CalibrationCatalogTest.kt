package vn.ndang.aovcollector

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationCatalogTest {
    private fun builtin(mode: String): JSONObject {
        val name = when (mode) {
            "chess" -> "chess-wiki"
            "ranking" -> "hero-ranking"
            "all" -> "batch-heroes"
            "pick" -> "named-heroes"
            "single" -> "single-hero"
            else -> "unsupported"
        }
        return JSONObject().put("collectionMode", mode).put("name", name)
            .put("builtinVersion", BuiltInMacros.VERSION).put("targetPackage", "com.garena.game.kgvn")
    }

    private fun chess(fast: Boolean = true) = builtin("chess")
        .put("chessTopics", JSONArray(ChessWiki.TOPICS.map { it.key })).put("fast", fast)

    @Test fun customReaderStepsDoNotEnableStoredOverrides() {
        for (type in listOf("skills", "ranking", "chess")) {
            val custom = JSONObject().put("name", "my-custom-macro").put("targetPackage", "com.garena.game.kgvn")
                .put("collectionMode", type).put("steps", JSONArray().put(JSONObject().put("type", type)))
            val allowedIds = CalibrationCatalog.steps(custom).map { it.action.id }.toSet()
            assertTrue(allowedIds.isEmpty())
        }
    }

    @Test fun identifiersAreUniqueAndStaticPointsAreValid() {
        for (macro in listOf(chess(), builtin("ranking"), builtin("all"))) {
            val steps = CalibrationCatalog.steps(macro)
            assertTrue(steps.isNotEmpty())
            assertEquals(steps.size, steps.map { it.action.id }.distinct().size)
            for (step in steps) {
                assertTrue(step.title.isNotBlank() && step.hint.isNotBlank())
                if (step.recognition == null) {
                    assertTrue(CalibrationCodec.validPoints(step.action.points))
                } else {
                    assertEquals(step.action.id, step.recognition)
                    assertTrue(step.action.relative)
                    assertTrue(step.action.points.isEmpty())
                }
            }
        }
    }

    @Test fun selectedChessTopicsKeepCanonicalOrderAndNeverIntroduceMatchmaking() {
        val macro = builtin("chess").put("chessTopics", JSONArray(listOf("phe", "quy-trinh", "phe")))
        val ids = CalibrationCatalog.steps(macro).map { it.action.id }
        assertEquals("chess.v1.lobby.mode", ids.first())
        assertTrue(ids.indexOf("chess.v1.topic.quy-trinh.sidebar") < ids.indexOf("chess.v1.topic.phe.sidebar"))
        assertFalse(ids.any { it.contains("nghe") || it.contains("match") || it.contains("start") })
        assertFalse(ids.contains("chess.v1.topic.phe.tab"))
        assertTrue(ids.contains("chess.v1.topic.phe.card"))
        assertFalse(ids.contains("chess.v1.topic.quy-trinh.card"))
    }

    @Test fun allActualScrollRetriesAndHeaderHaveRunnerTiming() {
        for (fast in listOf(true, false)) {
            val steps = CalibrationCatalog.steps(chess(fast))
            val timing = ChessWiki.Timing(fast)
            for (topic in ChessWiki.TOPICS.filter { it.key != "tat-ca-quan-co" }) {
                for (retry in 0..3) {
                    val action = steps.single { it.action.id == "chess.v1.topic.${topic.key}.scroll.$retry" }.action
                    assertEquals(timing.move, action.durationMs)
                    assertEquals(timing.hold, action.holdMs)
                    assertEquals(2, action.points.size)
                }
            }
            val header = steps.single { it.action.id == "chess.v1.topic.header" }.action
            assertEquals(timing.move, header.durationMs)
            assertEquals(0L, header.holdMs)
            assertEquals(CalibrationPoint(.83, .18), header.points.first())
        }
    }

    @Test fun measuredHeroListDragsUseValidRelativeBaselinesWithoutRecognition() {
        for (fast in listOf(true, false)) {
            val steps = CalibrationCatalog.steps(chess(fast))
            assertFalse(steps.any { it.action.id.startsWith("chess.v1.topic.tat-ca-quan-co.scroll.") })
            for ((direction, ys) in listOf("down" to (.92 to .32), "up" to (.24 to .84))) {
                val step = steps.single { it.action.id == "chess.v1.herolist.$direction" }
                assertNull(step.recognition)
                assertTrue(step.action.relative)
                assertTrue(CalibrationCodec.validPoints(step.action.points))
                assertEquals(listOf(CalibrationPoint(.76, ys.first), CalibrationPoint(.76, ys.second)), step.action.points)
                assertEquals(700L, step.action.durationMs)
                assertEquals(450L, step.action.holdMs)
            }
        }
        val other = builtin("chess").put("chessTopics", JSONArray(listOf("phe")))
        assertFalse(CalibrationCatalog.steps(other).any { it.action.id.startsWith("chess.v1.herolist.") })
    }

    @Test fun rankingOptionsUseRealEnumIdsAndRequestedModes() {
        val steps = CalibrationCatalog.steps(builtin("ranking"))
        assertEquals(
            listOf(
                "ranking.v1.table.switch",
                "ranking.v1.mode.open",
                "ranking.v1.mode.option.all",
                "ranking.v1.mode.option.five",
                "ranking.v1.mode.option.elite"
            ),
            steps.map {
                it.action.id
            }
        )
        val selected = builtin("ranking").put(
            "rankCombos",
            JSONArray(
                listOf(
                    RankScreens.COMBOS.first {
                        it.mode ==
                            RankScreens.Mode.ELITE
                    }.slug
                )
            )
        )
        assertEquals(3, CalibrationCatalog.steps(selected).size)
        assertEquals("ranking.v1.mode.option.elite", CalibrationCatalog.steps(selected).last().action.id)
    }

    @Test fun importedAndUnsupportedMacrosDoNotInventActions() {
        assertTrue(CalibrationCatalog.steps(JSONObject()).isEmpty())
        assertTrue(CalibrationCatalog.steps(builtin("board")).isEmpty())
        assertTrue(CalibrationCatalog.steps(builtin("chess")).isEmpty())
    }

    @Test fun onlyKnownBuiltinMetadataEnablesCatalogAndImportedCoordinatesAreIgnored() {
        for (mode in listOf("chess", "ranking", "all", "pick", "single")) {
            assertTrue(CalibrationCatalog.steps(JSONObject().put("collectionMode", mode)).isEmpty())
            for (missing in listOf("name", "builtinVersion", "targetPackage")) {
                val invalid = builtin(mode).apply { remove(missing) }
                assertTrue(CalibrationCatalog.steps(invalid).isEmpty())
            }
            for (version in listOf(0, -1, BuiltInMacros.VERSION + 1)) {
                assertTrue(CalibrationCatalog.steps(builtin(mode).put("builtinVersion", version)).isEmpty())
            }
            assertTrue(CalibrationCatalog.steps(builtin(mode).put("name", "imported")).isEmpty())
            assertTrue(CalibrationCatalog.steps(builtin(mode).put("targetPackage", "other.package")).isEmpty())
        }
        val expected = CalibrationCatalog.steps(builtin("single"))
        assertEquals(expected, CalibrationCatalog.steps(builtin("single").put("builtinVersion", 1)))
        val importedSteps = JSONArray().put(JSONObject().put("type", "tap").put("x", .99).put("y", .01))
            .put(JSONObject().put("type", "swipe").put("x", .01).put("y", .99).put("toX", .99).put("toY", .01))
        assertEquals(expected, CalibrationCatalog.steps(builtin("single").put("steps", importedSteps)))
    }

    @Test fun heroModesOfferEverySkillSlotWithoutGuessingItsPosition() {
        val all = CalibrationCatalog.steps(builtin("all"))
        for (mode in listOf("pick", "single")) assertEquals(all, CalibrationCatalog.steps(builtin(mode)))
        assertEquals(18, all.size)
        assertEquals(SkillReader.SCROLL, all.single { it.action.id == "skills.v1.description.scroll" }.action)
        for (count in listOf(4, 5)) {
            for (slot in 0 until count) {
                val step = all.single { it.action.id == "skills.v1.row.$count.slot.$slot" }
                assertEquals(step.action.id, step.recognition)
                assertTrue(step.action.relative)
                assertTrue(step.action.points.isEmpty())
            }
        }
        assertEquals(CalibrationPoint(HeroGrid.BACK_X, HeroGrid.BACK_Y), all.last().action.points.single())
    }
}
