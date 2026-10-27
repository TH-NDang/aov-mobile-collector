package vn.ndang.aovcollector

import android.widget.TextView

/** Visual task identity only; persisted macro modes and picker actions remain unchanged. */
internal object TaskIcons {
    val builtInModes = listOf("all", "ranking", "board", "chess", "pro-builds", "event-invite")

    fun drawable(mode: String): Int = when (mode) {
        "single", "all", "pick" -> R.drawable.ic_task_hero
        "ranking" -> R.drawable.ic_task_ranking
        "board" -> R.drawable.ic_task_warzone
        "chess" -> R.drawable.ic_task_chess
        "pro-builds" -> R.drawable.ic_task_combo
        "event-invite" -> R.drawable.ic_task_invite
        "manual" -> R.drawable.ic_panel_camera
        else -> R.drawable.ic_task_macro
    }

    fun decorate(label: TextView, mode: String) {
        val density = label.resources.displayMetrics.density
        val size = (24 * density + .5f).toInt()
        val icon = label.context.getDrawable(drawable(mode))?.mutate() ?: return
        icon.setTint(Ui.accent)
        icon.setBounds(0, 0, size, size)
        label.setCompoundDrawablesRelative(icon, null, null, null)
        label.compoundDrawablePadding = (8 * density + .5f).toInt()
        // The adjacent text remains the accessible name; decorative icons add no repeated label.
    }
}
