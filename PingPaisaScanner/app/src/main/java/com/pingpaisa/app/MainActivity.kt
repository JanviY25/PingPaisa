package com.pingpaisa.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.IntentCompat
import com.pingpaisa.app.ui.profile.FinancialProfileScreen
import com.pingpaisa.app.ui.scan.ScanScreen
import com.pingpaisa.app.ui.scan.ScanViewModel
import com.pingpaisa.app.ui.theme.PingPaisaTheme

class MainActivity : ComponentActivity() {
    private val vm: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch carries a share to process; a rotation or "recents" relaunch replays the old intent.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) consumeShare(intent)
        setContent {
            PingPaisaTheme {
                var onProfile by rememberSaveable { mutableStateOf(false) }
                val profile by vm.profile.collectAsState()
                if (onProfile) {
                    BackHandler { onProfile = false }
                    FinancialProfileScreen(profile, onSave = { vm.saveProfile(it); onProfile = false }, onBack = { onProfile = false })
                } else {
                    ScanScreen(vm, onOpenProfile = { onProfile = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShare(intent)
    }

    /** Handles Share → PingPaisa (links, text, one or many images). The extras are removed so nothing is processed twice. */
    private fun consumeShare(i: Intent?) {
        i ?: return
        val action = i.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT)
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)
        val uris: List<Uri> = if (action == Intent.ACTION_SEND) {
            listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
        } else {
            IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        }
        i.action = Intent.ACTION_MAIN
        i.removeExtra(Intent.EXTRA_TEXT); i.removeExtra(Intent.EXTRA_SUBJECT); i.removeExtra(Intent.EXTRA_STREAM)
        if (text.isNullOrBlank() && uris.isEmpty()) return
        vm.handleShared(text, subject, uris)
    }
}
