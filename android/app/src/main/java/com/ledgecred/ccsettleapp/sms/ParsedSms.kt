package com.ledgecred.ccsettleapp.sms

enum class TransactionType {
    DEBIT, REFUND, SELF_TRANSFER, UNPARSED, OTP, DECLINED, STATEMENT
}

data class ParsedSms(
    val type: TransactionType,
    val amountPaise: Long?,
    val cardLast4: String?,
    val bank: String?
)
