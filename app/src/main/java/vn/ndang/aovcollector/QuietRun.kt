package vn.ndang.aovcollector

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ZenPolicy

/**
 * Do Not Disturb for the length of a run, as an automatic rule of the app's own: calls, repeat callers, alarms and
 * media still come through; nothing else rings or pops up over the game. The user's own Do Not Disturb settings are
 * never changed. The rule is switched off when the run pauses or stops, and again when the service starts, in case
 * the app was killed during a run.
 */
internal class QuietRun(private val context: Context) {
    companion object {
        const val PREFERENCE = "quietRun"
        private const val ACTIVE = "quietRunActive"
        private val CONDITION: Uri = Uri.parse("condition://vn.ndang.aovcollector/run")
    }

    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val prefs get() = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    val wanted: Boolean get() = prefs.getBoolean(PREFERENCE, false)

    val allowed: Boolean get() = manager?.isNotificationPolicyAccessGranted == true

    /** On for this run: only calls (and alarms) can show a banner. */
    var active = false
        private set

    /** Turns Do Not Disturb on if the user asked for it; the line for the run's log, or null when nothing changed. */
    fun start(): String? {
        if (!wanted || active) return null
        val nm = manager ?: return null
        if (!nm.isNotificationPolicyAccessGranted) {
            return "Chưa cấp quyền Không làm phiền cho AOV Collector; thông báo vẫn có thể bật lên khi chạy"
        }
        return try {
            prefs.edit().putBoolean(ACTIVE, true).commit()
            nm.setAutomaticZenRuleState(rule(nm), Condition(CONDITION, "Đang thu thập", Condition.STATE_TRUE))
            active = true
            "Đã bật Không làm phiền khi chạy: chỉ cuộc gọi và báo thức vẫn báo"
        } catch (e: Exception) {
            prefs.edit().remove(ACTIVE).apply()
            "Không bật được Không làm phiền (${e.javaClass.simpleName}); thông báo vẫn có thể bật lên"
        }
    }

    /** Switches the app's rule off, also after a run the app did not end itself. */
    fun stop(): String? {
        if (!active && !prefs.getBoolean(ACTIVE, false)) return null
        active = false
        return try {
            val nm = manager ?: return null
            nm.automaticZenRules.filterValues { it.conditionId == CONDITION }.keys.forEach {
                nm.setAutomaticZenRuleState(it, Condition(CONDITION, "Đã dừng", Condition.STATE_FALSE))
            }
            prefs.edit().remove(ACTIVE).apply()
            "Đã tắt Không làm phiền của app"
        } catch (e: Exception) {
            "Không tắt được Không làm phiền (${e.javaClass.simpleName}); tắt bằng tay trong Cài đặt nếu còn bật"
        }
    }

    private fun rule(nm: NotificationManager): String {
        val wanted = AutomaticZenRule(
            "AOV Collector đang chạy",
            null,
            ComponentName(context, MainActivity::class.java),
            CONDITION,
            ZenPolicy.Builder()
                .allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
                .allowRepeatCallers(true)
                .allowAlarms(true)
                .allowMedia(true)
                .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
                .allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
                .allowReminders(false)
                .allowEvents(false)
                .allowSystem(false)
                .showPeeking(false)
                .showFullScreenIntent(false)
                .showLights(false)
                .showInAmbientDisplay(false)
                .showStatusBarIcons(true)
                .showBadges(true)
                .showInNotificationList(true)
                .build(),
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            true
        )
        val existing = nm.automaticZenRules.entries.firstOrNull { it.value.conditionId == CONDITION }?.key
        return if (existing != null) {
            nm.updateAutomaticZenRule(existing, wanted)
            existing
        } else {
            nm.addAutomaticZenRule(wanted)
        }
    }
}
