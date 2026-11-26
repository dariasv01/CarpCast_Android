package com.david.carpcast.journal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.david.carpcast.ui.theme.CarpCastTheme

class JournalActivity : ComponentActivity() {
    companion object { const val EXTRA_SNAPSHOT_ID = "snapshot_id" }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.rgb(7, 21, 34))
        )
        val snapshotId = intent.getStringExtra(EXTRA_SNAPSHOT_ID)
        setContent { CarpCastTheme { JournalHost(FieldStore(this), snapshotId, onBack = { finish() }) } }
    }
}
