package vn.ndang.aovcollector

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract

/** Runs as the provider-owning test UID, just as Android's folder picker grants a selected tree. */
class PhotoGrantActivity:Activity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        grantUriPermission("vn.ndang.aovcollector",DocumentsContract.buildTreeDocumentUri(
            "vn.ndang.aovcollector.test.photos","Pictures"),
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        finish()
    }
}
