package vn.ndang.chatfixture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** CI-only foreign application window. No overlay permission, account, network, or game data. */
public class EdgeHandleActivity extends Activity {
    private int touches;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("close", false)) { finish(); return; }
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        setFinishOnTouchOutside(false);
        TextView handle = new TextView(this);
        handle.setText("CI\nedge");
        handle.setTextColor(Color.WHITE);
        handle.setBackgroundColor(Color.rgb(130, 65, 20));
        handle.setContentDescription("Synthetic foreign edge input receiver");
        handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        handle.setAccessibilityPaneTitle("Synthetic foreign edge input receiver");
        handle.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (!handle.isShown() || handle.getWidth() == 0 || handle.getHeight() == 0) return;
                handle.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                // Announce this real, visible pane without requesting input or accessibility focus.
                handle.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                Rect bounds = new Rect();
                handle.getGlobalVisibleRect(bounds);
                String stateText = "enabled=" + getSystemService(AccessibilityManager.class).isEnabled() +
                        ",attached=" + handle.isAttachedToWindow() + ",shown=" + handle.isShown() +
                        ",windowFocus=" + handle.hasWindowFocus() + ",bounds=" + bounds +
                        ",flags=" + getWindow().getAttributes().flags;
                try {
                    Files.write(new File(getFilesDir(), "edge-state.txt").toPath(), stateText.getBytes(StandardCharsets.UTF_8));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        });
        handle.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                touches++;
                persist();
                view.performClick();
            }
            return true;
        });
        setContentView(handle);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 1;
        params.y = 100;
        params.width = 60;
        params.height = 200;
        params.setFitInsetsTypes(0);
        getWindow().setAttributes(params);
        persist();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("close", false)) finish();
    }

    private void persist() {
        try {
            Files.write(new File(getFilesDir(), "edge-touches.txt").toPath(),
                    Integer.toString(touches).getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
