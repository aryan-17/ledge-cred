package com.ledgecred.ccsettleapp.sms

import org.junit.Assert.*
import org.junit.Test

class SmsParserTest {

    @Test
    fun `HDFC debit SMS classified as DEBIT with correct paise`() {
        val result = SmsParser.classify(
            "Rs.2,500.00 debited from a/c XX1234. Avbl Bal:Rs.45,320.50",
            "HDFCBK"
        )
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(250000L, result.amountPaise)
        assertEquals("1234", result.cardLast4)
        assertEquals("HDFC", result.bank)
    }

    @Test
    fun `OTP SMS returns OTP with null amount`() {
        val result = SmsParser.classify("123456 is your OTP. Do not share.", "HDFCBK")
        assertEquals(TransactionType.OTP, result.type)
        assertNull(result.amountPaise)
    }

    @Test
    fun `declined SMS returns DECLINED`() {
        val result = SmsParser.classify(
            "Transaction of Rs.1,000 on Card XX1234 has been declined",
            "HDFCBK"
        )
        assertEquals(TransactionType.DECLINED, result.type)
    }

    @Test
    fun `credit on card returns REFUND`() {
        val result = SmsParser.classify(
            "Rs.500.00 credited to your credit card XX5678",
            "HDFCBK"
        )
        assertEquals(TransactionType.REFUND, result.type)
        assertEquals(50000L, result.amountPaise)
    }

    @Test
    fun `credit on savings returns SELF_TRANSFER`() {
        val result = SmsParser.classify(
            "INR 42380.00 credited to your SB A/C",
            "SBIINB"
        )
        assertEquals(TransactionType.SELF_TRANSFER, result.type)
        assertEquals(4238000L, result.amountPaise)
    }

    @Test
    fun `hold returns UNPARSED`() {
        val result = SmsParser.classify("Rs.200 hold placed on Card XX1234", "HDFCBK")
        assertEquals(TransactionType.UNPARSED, result.type)
    }

    @Test
    fun `statement returns STATEMENT`() {
        val result = SmsParser.classify(
            "Your HDFC credit card statement is ready. Minimum amount due Rs.500",
            "HDFCBK"
        )
        assertEquals(TransactionType.STATEMENT, result.type)
    }

    @Test
    fun `extractAmountPaise handles commas and decimals`() {
        assertEquals(104300L,  SmsParser.extractAmountPaise("INR 1,043.00 debited"))
        assertEquals(50L,      SmsParser.extractAmountPaise("Rs. 0.50 debited"))
        assertEquals(250000L,  SmsParser.extractAmountPaise("Rs.2,500.00 debited"))
        assertNull(            SmsParser.extractAmountPaise("Your OTP is 123456"))
    }

    @Test
    fun `dedupeHash stable within same minute`() {
        val t = 1754012400000L
        val h1 = SmsParser.dedupeHash("HDFC", 250000L, "1234", t)
        val h2 = SmsParser.dedupeHash("HDFC", 250000L, "1234", t + 30_000) // +30s same minute
        assertEquals(h1, h2)
    }

    @Test
    fun `dedupeHash differs across minutes`() {
        val t = 1754012400000L
        val h1 = SmsParser.dedupeHash("HDFC", 250000L, "1234", t)
        val h2 = SmsParser.dedupeHash("HDFC", 250000L, "1234", t + 60_000) // +1 min
        assertNotEquals(h1, h2)
    }

    @Test
    fun `INR format parsed correctly`() {
        val result = SmsParser.classify(
            "INR 15,000.00 debited from Axis Bank A/c XX9876 at Swiggy",
            "AXISBK"
        )
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(1500000L, result.amountPaise)
        assertEquals("9876", result.cardLast4)
        assertEquals("Axis", result.bank)
    }

    @Test
    fun `IDFC bank sender resolved`() {
        val result = SmsParser.classify(
            "Rs.3,000.00 debited from IDFC Bank A/c XX5432",
            "IDFCBN"
        )
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(300000L, result.amountPaise)
        assertEquals("IDFC", result.bank)
    }

    @Test
    fun `IDFC credit card sender resolved`() {
        val result = SmsParser.classify(
            "Rs.1,200.00 debited via IDFC credit card XX7890",
            "IDFCCD"
        )
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("IDFC", result.bank)
    }
}
