package vn.ndang.aovcollector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** Grants the test-owned tree from its owning UID, like Android's folder picker. */
public class PhotoGrantActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        grantUriPermission("vn.ndang.aovcollector",DocumentsContract.buildTreeDocumentUri(
            "vn.ndang.aovcollector.test.photos","Pictures"),
            Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        finish();
    }
}
