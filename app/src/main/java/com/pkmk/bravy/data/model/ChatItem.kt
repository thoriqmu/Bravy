package com.pkmk.bravy.data.model

// Sealed interface ini akan menjadi tipe data dasar untuk adapter kita
sealed interface ChatItem {
    // Wrapper untuk pesan yang sudah ada
    data class MessageItem(val message: Message) : ChatItem

    // Item baru untuk pembatas tanggal
    data class DateSeparatorItem(val date: String) : ChatItem
}