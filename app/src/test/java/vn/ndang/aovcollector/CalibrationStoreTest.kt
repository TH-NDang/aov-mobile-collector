package vn.ndang.aovcollector

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationStoreTest {
    @Test fun delayedOrReorderedRecognitionCannotBecomeATestTarget() {
        assertTrue(CalibrationFreshness.valid(1000, 1000))
        assertTrue(CalibrationFreshness.valid(1000, 3500))
        assertFalse(CalibrationFreshness.valid(1000, 3501))
        assertFalse(CalibrationFreshness.valid(1000, 999))
        assertFalse(CalibrationFreshness.valid(-1, 1000))
    }

    @Test fun expiredTestLeaseCannotContinueOrReleaseAnotherCapture() {
        val shield = CaptureOverlayShield { }
        val test = shield.acquire()
        val screenshot = shield.acquire()
        shield.release(test)
        var continuations = 0
        if (shield.contains(test)) continuations++
        assertEquals(0, continuations)
        assertTrue(shield.contains(screenshot))
        shield.release(test)
        assertTrue(shield.contains(screenshot))
        shield.release(screenshot)
    }

    private val profile = CalibrationProfile(2400, 1080, 1, 20, 0, 30, 0)
    private val action = CalibrationAction("hero.skill.tap", "Skill", listOf(CalibrationPoint(0.3, 0.4)))
    private val target = listOf(CalibrationPoint(0.4, 0.6))

    @Test fun absoluteRoundTripPersistsAcrossStoreInstancesAndIgnoresLabelsAndTiming() {
        val memory = MemoryPreferences()
        assertTrue(CalibrationStore(memory.prefs).save(profile, action, target))
        val renamed = action.copy(label = "Translated label", durationMs = 999, holdMs = 200)
        assertEquals(target, CalibrationStore(memory.prefs).resolve(profile, renamed))
        assertEquals(1, memory.commits)
    }

    @Test fun relativeCalibrationUsesFreshDetectedGeometry() {
        val store = CalibrationStore(MemoryPreferences().prefs)
        val relative = action.copy(relative = true)
        assertTrue(store.save(profile, relative, target))
        val fresh = relative.copy(points = listOf(CalibrationPoint(0.5, 0.3)))
        val resolved = store.resolve(profile, fresh).single()
        assertEquals(0.6, resolved.x, 0.0000001)
        assertEquals(0.5, resolved.y, 0.0000001)
        val edge = relative.copy(points = listOf(CalibrationPoint(0.99, 0.99)))
        assertEquals(edge.points, store.resolve(profile, edge))
        assertEquals(action.points, store.resolve(profile, action))
    }

    @Test fun unsavedEditsNeverOverrideSavedPointsAndResetSurvivesRestart() {
        val memory = MemoryPreferences()
        val store = CalibrationStore(memory.prefs)
        assertTrue(store.save(profile, action, target))
        val unsaved = listOf(CalibrationPoint(.8, .9))
        assertFalse(unsaved == store.resolve(profile, action))
        assertEquals(target, CalibrationStore(memory.prefs).resolve(profile, action))
        assertTrue(store.reset(profile, action.id))
        assertEquals(action.points, CalibrationStore(memory.prefs).resolve(profile, action))
        assertEquals(2, memory.commits)
    }

    @Test fun relativeSwipeKeepsIndependentEndpointOffsetsOnFreshRuntimeGeometry() {
        val memory = MemoryPreferences()
        val swipe = CalibrationAction("runtime.swipe", "Swipe", listOf(CalibrationPoint(.7, .8), CalibrationPoint(.7, .2)), relative = true)
        val adjusted = listOf(CalibrationPoint(.75, .85), CalibrationPoint(.65, .3))
        assertTrue(CalibrationStore(memory.prefs).save(profile, swipe, adjusted))
        val fresh = swipe.copy(points = listOf(CalibrationPoint(.6, .7), CalibrationPoint(.6, .1)))
        val actual = CalibrationStore(memory.prefs).resolve(profile, fresh)
        assertEquals(.65, actual[0].x, .000001)
        assertEquals(.75, actual[0].y, .000001)
        assertEquals(.55, actual[1].x, .000001)
        assertEquals(.2, actual[1].y, .000001)
        assertEquals(swipe.points, CalibrationStore(memory.prefs).resolve(profile, swipe.copy(id = "runtime.other")))
    }

    @Test fun eachProfileDimensionRotationAndInsetIsIsolated() {
        val store = CalibrationStore(MemoryPreferences().prefs)
        assertTrue(store.save(profile, action, target))
        listOf(
            profile.copy(width = 2500),
            profile.copy(height = 1200),
            profile.copy(rotation = 3),
            profile.copy(left = 21),
            profile.copy(top = 1),
            profile.copy(right = 31),
            profile.copy(bottom = 1)
        ).forEach { assertEquals(action.points, store.resolve(it, action)) }
    }

    @Test fun resetActionAndResetProfilePreserveUnrelatedEntries() {
        val memory = MemoryPreferences()
        val store = CalibrationStore(memory.prefs)
        val other = action.copy(id = "hero.back")
        val portrait = profile.copy(rotation = 0)
        store.save(profile, action, target)
        store.save(profile, other, target)
        store.save(portrait, action, target)
        memory.values["unrelated"] = "preserved"
        assertTrue(store.reset(profile, action.id))
        assertEquals(action.points, store.resolve(profile, action))
        assertEquals(target, store.resolve(profile, other))
        assertTrue(store.resetProfile(profile))
        assertEquals(other.points, store.resolve(profile, other))
        assertEquals(target, store.resolve(portrait, action))
        assertEquals("preserved", memory.values["unrelated"])
    }

    @Test fun rejectsInvalidProfilesIdsAndTargets() {
        val store = CalibrationStore(MemoryPreferences().prefs)
        listOf(
            profile.copy(width = 0),
            profile.copy(height = -1),
            profile.copy(rotation = 4),
            profile.copy(left = -1),
            profile.copy(right = 2400),
            profile.copy(top = Int.MAX_VALUE)
        ).forEach { assertFalse(store.save(it, action, target)) }
        listOf("", "../target", "User visible label", "x/y", "é", "x".repeat(129)).forEach {
            assertFalse(store.save(profile, action.copy(id = it), target))
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.001, 1.001).forEach {
            assertFalse(store.save(profile, action, listOf(CalibrationPoint(it, 0.5))))
        }
        assertFalse(store.save(profile, action, emptyList()))
        assertFalse(store.save(profile, action, target + target))
        assertTrue(store.resolve(profile, action.copy(points = listOf(CalibrationPoint(Double.NaN, 0.5)))).isEmpty())
    }

    @Test fun malformedAndIncompatibleRecordsUseDefaultsWithoutPartialApplication() {
        val memory = MemoryPreferences()
        val store = CalibrationStore(memory.prefs)
        store.save(profile, action, target)
        val key = memory.values.keys.single()
        listOf(
            "", "garbage", "2|absolute|1|0.4,0.6", "1|relative|1|0.1,0.2",
            "1|absolute|2|0.4,0.6", "1|absolute|1|NaN,0.6", "1|absolute|1|Infinity,0.6",
            "1|absolute|1|1.01,0.6", "1|absolute|1|-0.01,0.6", "1|absolute|1|0.4,0.6|",
            "1|absolute|1|0.4,0.6,0.7", "1|absolute|1| 0.4,0.6", "x".repeat(32769)
        ).forEach {
            memory.values[key] = it
            assertEquals(it.take(100), action.points, store.resolve(profile, action))
        }
        memory.values[key] = 123
        assertEquals(action.points, store.resolve(profile, action))
        memory.values.remove(key)
        assertEquals(action.points, store.resolve(profile, action))
    }

    @Test fun geometryCountAndModeMustMatch() {
        val pair = action.copy(points = action.points + target)
        val record = CalibrationCodec.encode(pair, target + action.points)
        assertNull(CalibrationCodec.decode(record, action))
        assertNull(CalibrationCodec.decode(record, pair.copy(relative = true)))
        assertEquals(target + action.points, CalibrationCodec.decode(record, pair))
    }

    @Test fun commitFailuresAndPreferenceExceptionsAreReported() {
        val memory = MemoryPreferences()
        val store = CalibrationStore(memory.prefs)
        memory.commitResult = false
        assertFalse(store.save(profile, action, target))
        assertFalse(store.reset(profile, action.id))
        assertFalse(store.resetProfile(profile))
        memory.throwOnAccess = true
        assertFalse(store.save(profile, action, target))
        assertFalse(store.reset(profile, action.id))
        assertFalse(store.resetProfile(profile))
        assertEquals(action.points, store.resolve(profile, action))
    }

    /** Dynamic proxies avoid Android framework behavior in local JVM tests. */
    private class MemoryPreferences {
        val values = mutableMapOf<String, Any>()
        var commits = 0
        var commitResult = true
        var throwOnAccess = false
        val prefs: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            if (throwOnAccess) throw IllegalStateException("Unavailable preferences")
            when (method.name) {
                "getString" -> values[args!![0]]?.let { it as String } ?: args[1]
                "getAll" -> values.toMap()
                "edit" -> editor()
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val changes = mutableMapOf<String, Any?>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)
            ) { proxy, method, args ->
                when (method.name) {
                    "putString" -> {
                        changes[args!![0] as String] = args[1]
                        proxy
                    }
                    "remove" -> {
                        changes[args!![0] as String] = null
                        proxy
                    }
                    "commit" -> {
                        commits++
                        if (commitResult) {
                            changes.forEach { (key, value) ->
                                if (value == null) values.remove(key) else values[key] = value
                            }
                        }
                        commitResult
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as SharedPreferences.Editor
        }
    }
}
