package vn.ndang.aovcollector

import android.content.Context
import android.graphics.Color
import android.widget.ImageButton

/** Icon-only header actions retain a full touch target and accessible name. */
internal object PanelHeaderActions {
    fun button(context: Context, icon: Int, description: String, action: () -> Unit) = ImageButton(context).apply {
        val density = resources.displayMetrics.density

        fun dp(value: Int) = (value * density + .5f).toInt()
        setImageResource(icon)
        imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        contentDescription = description
        tooltipText = description
        minimumWidth = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.argb(90, 210, 220, 255)),
            Ui.shape(Color.rgb(47, 52, 74), dp(10)),
            Ui.shape(Color.WHITE, dp(10))
        )
        setOnClickListener { action() }
    }
}
