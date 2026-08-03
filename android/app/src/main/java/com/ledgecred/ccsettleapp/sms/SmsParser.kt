package com.ledgecred.ccsettleapp.sms

object SmsParser {

    private val AMOUNT_REGEX = Regex(
        """(?:INR|Rs\.?|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val CARD_LAST4_REGEX = Regex(
        """(?:XX|x{2,4}|ending\s*)(\d{4})""",
        RegexOption.IGNORE_CASE
    )

    // Classification priority: OTP → DECLINED → STATEMENT → HOLD → credit → debit → UNPARSED
    private val OTP_KEYWORDS      = listOf("otp", "one time password", "one-time", "do not share")
    private val DECLINED_KEYWORDS = listOf("declined", "failed", "unsuccessful")
    private val STATEMENT_KEYWORDS = listOf("statement", "minimum amount due", "min. amount due", "payment due")
    private val HOLD_KEYWORDS     = listOf("hold", "authorization hold", "pre-auth", "pre auth")
    private val CREDIT_KEYWORDS   = listOf("credited", "credit", "refund", "cashback", "reversal")
    private val DEBIT_KEYWORDS    = listOf("debited", "debit", "withdrawn", "spent", "used at", "purchase")
    private val CARD_KEYWORDS     = listOf("card", "credit card", "debit card", "xx")

    private val BANK_SENDER_MAP = mapOf(
        "HDFCBK" to "HDFC",    "HDFCBN" to "HDFC",
        "SBIINB" to "SBI",     "SBICRD" to "SBI",
        "ICICIB" to "ICICI",   "ICICIN" to "ICICI",
        "AXISBK" to "Axis",    "AXISBN" to "Axis",
        "KOTAKB" to "Kotak",   "KOTAKN" to "Kotak",
        "YESBNK" to "Yes Bank","IDFCBN" to "IDFC",
        "INDBNK" to "IndusInd","IDFCCD" to "IDFC",
        "PNBSMS" to "PNB",     "BOIIND" to "BOI"
    )

    fun classify(sms: String, sender: String): ParsedSms {
        val lower    = sms.lowercase()
        val bank     = BANK_SENDER_MAP.entries
            .firstOrNull { sender.contains(it.key, ignoreCase = true) }?.value ?: sender
        val cardLast4 = CARD_LAST4_REGEX.find(sms)?.groupValues?.get(1)

        if (OTP_KEYWORDS.any      { lower.contains(it) }) return ParsedSms(TransactionType.OTP,       null,             null,      bank)
        if (DECLINED_KEYWORDS.any { lower.contains(it) }) return ParsedSms(TransactionType.DECLINED,  null,             cardLast4, bank)
        if (STATEMENT_KEYWORDS.any{ lower.contains(it) }) return ParsedSms(TransactionType.STATEMENT, null,             cardLast4, bank)
        if (HOLD_KEYWORDS.any     { lower.contains(it) }) return ParsedSms(TransactionType.UNPARSED,  null,             cardLast4, bank)

        val amount   = extractAmountPaise(sms)
        val isCredit = CREDIT_KEYWORDS.any { lower.contains(it) }
        val isDebit  = DEBIT_KEYWORDS.any  { lower.contains(it) }
        val isCard   = CARD_KEYWORDS.any   { lower.contains(it) }

        if (isCredit && amount != null) {
            val type = if (isCard) TransactionType.REFUND else TransactionType.SELF_TRANSFER
            return ParsedSms(type, amount, cardLast4, bank)
        }
        if (isDebit && amount != null) return ParsedSms(TransactionType.DEBIT, amount, cardLast4, bank)
        return ParsedSms(TransactionType.UNPARSED, amount, cardLast4, bank)
    }

    fun extractAmountPaise(sms: String): Long? {
        val match = AMOUNT_REGEX.find(sms) ?: return null
        val raw   = match.groupValues[1].replace(",", "")
        return (raw.toDoubleOrNull() ?: return null).let { (it * 100).toLong() }
    }

    /** Truncates txnTime to the minute for deduplication. */
    fun dedupeHash(bank: String, amountPaise: Long, cardLast4: String?, txnTimeMillis: Long): String {
        val minuteRounded = txnTimeMillis / 60_000 * 60_000
        return "$bank|$amountPaise|${cardLast4.orEmpty()}|$minuteRounded".hashCode().toString()
    }
}
