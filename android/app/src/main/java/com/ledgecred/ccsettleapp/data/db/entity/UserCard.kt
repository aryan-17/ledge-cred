package com.ledgecred.ccsettleapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_cards")
data class UserCard(
    @PrimaryKey val id: String,
    val bank: String,
    val last4: String,
    val nickname: String? = null,
    val type: String = "card"   // "card" | "account"
) {
    val key: String     get() = "$bank:$last4"
    val display: String get() = nickname ?: if (type == "account") "$bank a/c ··$last4" else "$bank ··$last4"
}
