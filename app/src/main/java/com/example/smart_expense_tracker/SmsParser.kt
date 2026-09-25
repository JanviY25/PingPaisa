package com.example.smart_expense_tracker

object SmsParser {

    // Matches: "Sent Rs.2.00"
    private val amountRegex = Regex("""Sent Rs\.?\s?([\d,]+\.?\d*)""")

    // Matches: "To SANTOSH YADAV" (stops at newline)
    private val toRegex = Regex("""To\s+([A-Za-z\s]+?)\n""")

    // Matches: "On 17/09/26"
    private val dateRegex = Regex("""On\s+(\d{2}/\d{2}/\d{2})""")

    // Matches: "Ref 626022634234"
    private val refRegex = Regex("""Ref\s+(\d+)""")

    fun parse(smsText: String): Transaction? {
        val amountMatch = amountRegex.find(smsText) ?: return null
        val toMatch = toRegex.find(smsText) ?: return null
        val dateMatch = dateRegex.find(smsText) ?: return null
        val refMatch = refRegex.find(smsText) ?: return null

        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val recipient = toMatch.groupValues[1].trim()
        val date = dateMatch.groupValues[1]
        val ref = refMatch.groupValues[1]

        return Transaction(
            amount = amount,
            recipient = recipient,
            date = date,
            refNumber = ref,
            rawText = smsText
        )
    }
}