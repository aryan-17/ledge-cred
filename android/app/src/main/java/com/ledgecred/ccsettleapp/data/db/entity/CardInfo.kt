package com.ledgecred.ccsettleapp.data.db.entity

import androidx.room.ColumnInfo

data class CardInfo(
    @ColumnInfo(name = "bank") val bank: String,
    @ColumnInfo(name = "cardLast4") val cardLast4: String
) {
    val key: String get() = "$bank:$cardLast4"
    val display: String get() = "$bank ··$cardLast4"
}
