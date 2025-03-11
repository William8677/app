package com.williamfq.xhat.ui.Navigation

enum class ChatType {
    PERSONAL,
    GROUP
}

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Main : Screen("main")
    object Chats : Screen("chats")
    object Stories : Screen("stories")
    object Channels : Screen("channels")
    object Communities : Screen("communities")
    object ChatList : Screen("chat_list")
    object Chat : Screen("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/$chatId"
    }
    object ChatDetail : Screen("chat_detail/{chatId}") {
        fun createRoute(chatId: String) = "chat_detail/$chatId"
    }
    object Calls : Screen("calls")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object PhoneNumber : Screen("phone_number")
    object VerificationCode : Screen("verification_code/{phoneNumber}/{verificationId}") {
        fun createRoute(phoneNumber: String, verificationId: String) = "verification_code/$phoneNumber/$verificationId"
    }
    object ProfileSetup : Screen("profile_setup")
    object PanicLocation : Screen("panic_location/{chatId}/{chatType}") {
        fun createRoute(chatId: String, chatType: ChatType) = "panic_location/$chatId/${chatType.name}"
    }
}
