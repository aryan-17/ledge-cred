package com.ledgecred.ccsettleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.ledgecred.ccsettleapp.ui.nav.NavGraph
import com.ledgecred.ccsettleapp.ui.nav.Screen
import com.ledgecred.ccsettleapp.ui.theme.CcSettleTheme

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
}
