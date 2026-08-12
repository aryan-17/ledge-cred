package com.ledgecred.ccsettleapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.ledgecred.ccsettleapp.sms.SmsInboxReader
import com.ledgecred.ccsettleapp.ui.nav.NavGraph
import com.ledgecred.ccsettleapp.ui.nav.Screen
import com.ledgecred.ccsettleapp.ui.theme.CcSettleTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CcSettleTheme {
                val navController = rememberNavController()
                val startDest = if (FirebaseAuth.getInstance().currentUser != null)
                    Screen.Home.route
                else
                    Screen.Onboarding.route
                NavGraph(navController = navController, startDestination = startDest)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Read SMS inbox every time app comes to foreground — catches missed SMS on Vivo/MIUI
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch {
                SmsInboxReader.sync(this@MainActivity)
            }
        }
    }
}
