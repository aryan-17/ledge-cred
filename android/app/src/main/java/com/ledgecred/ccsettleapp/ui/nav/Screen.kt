package com.ledgecred.ccsettleapp.ui.nav

sealed class Screen(val route: String) {
    object Onboarding     : Screen("onboarding")
    object Home           : Screen("home")
    object Settle         : Screen("settle/{eventId}") {
        fun route(eventId: String) = "settle/$eventId"
    }
    object Waiting        : Screen("waiting/{eventId}") {
        fun route(eventId: String) = "waiting/$eventId"
    }
    object PartialReceipt : Screen("partial/{eventId}") {
        fun route(eventId: String) = "partial/$eventId"
    }
    object Review         : Screen("review")
    object History        : Screen("history")
    object Settings       : Screen("settings")
}
