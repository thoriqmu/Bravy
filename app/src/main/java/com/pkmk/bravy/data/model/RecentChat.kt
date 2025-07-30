package com.pkmk.bravy.data.model

data class RecentChat(
    val user: User,
    val chatId: String,
    val lastMessage: Message?
)