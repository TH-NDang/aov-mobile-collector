package vn.ndang.chatfixture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Exported CI-only command, not the floating window owner.
 * Finish immediately so the game regains focus while a separate background
 * service owns the genuine foreign TYPE_APPLICATION_OVERLAY.
 */
public class EdgeHandleActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent command = new Intent(this, EdgeOverlayService.class);
        command.putExtra("close", getIntent().getBooleanExtra("close", false));
        if (startService(command) == null) {
            throw new IllegalStateException("Could not start the synthetic edge overlay service");
        }
        finish();
    }
}