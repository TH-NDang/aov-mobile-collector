package vn.ndang.gamefixture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.json.JSONArray;
import org.json.JSONObject;

/** A local, synthetic receiver for actual accessibility-injected input. It is not a game. */
public class GestureActivity extends Activity {
    private JSONArray events;
    private TextView surface;
    private float startX;
    private float startY;
    private int moves;
    private boolean immersive;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        surface = new TextView(this);
        surface.setTextColor(Color.WHITE);
        surface.setBackgroundColor(Color.rgb(25, 45, 65));
        surface.setTextSize(22);
        surface.setPadding(32, 32, 32, 32);
        surface.setContentDescription("Synthetic gesture receiver");
        surface.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startX = event.getRawX();
                startY = event.getRawY();
                moves = 0;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                moves++;
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                try {
                    events.put(new JSONObject()
                            .put("startX", startX).put("startY", startY)
                            .put("endX", event.getRawX()).put("endY", event.getRawY())
                            .put("moves", moves).put("action", event.getActionMasked()));
                    persist();
                    view.performClick();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
            return true;
        });
        setContentView(surface);
        surface.setOnApplyWindowInsetsListener((view, insets) -> {
            // Content receives consumed insets when the decor fits visible bars. Observe the
            // actual root state after dispatch instead of treating consumed content as hidden bars.
            surface.post(this::persistInsets);
            return insets;
        });
        surface.getViewTreeObserver().addOnGlobalLayoutListener(this::persistInsets);
        load(getIntent());
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (surface != null) surface.post(this::persistInsets);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        load(intent);
    }

    private void load(Intent intent) {
        try {
            immersive = intent.getBooleanExtra("immersive", false);
            getWindow().getDecorView().setSystemUiVisibility(immersive
                    ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    : 0);
            surface.requestApplyInsets();
            File file = new File(getFilesDir(), "gesture-events.json");
            events = intent.getBooleanExtra("reset", false) || !file.exists()
                    ? new JSONArray()
                    : new JSONArray(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            persist();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void persist() throws Exception {
        File temporary = new File(getFilesDir(), "gesture-events.tmp");
        Files.write(temporary.toPath(), events.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temporary.toPath(), new File(getFilesDir(), "gesture-events.json").toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        surface.setText("Synthetic gesture fixture\nNo game, account, or network\nCompleted inputs: " + events.length());
    }

    private void persistInsets() {
        WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
        if (insets == null) return;
        try {
            String stateText = "requested=" + immersive + ",statusVisible=" + insets.isVisible(WindowInsets.Type.statusBars()) +
                    ",navigationVisible=" + insets.isVisible(WindowInsets.Type.navigationBars());
            Files.write(new File(getFilesDir(), "immersive-state.txt").toPath(), stateText.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
