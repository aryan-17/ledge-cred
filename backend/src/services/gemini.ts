import { GoogleGenerativeAI } from '@google/generative-ai'
import type { ClassifyMessage, ClassifyResult, TransactionType } from '../types'

let genAI: GoogleGenerativeAI

function getGenAI() {
  if (!genAI) genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!)
  return genAI
}

const VALID_TYPES: TransactionType[] = [
  'DEBIT', 'REFUND', 'SELF_TRANSFER', 'OTP', 'DECLINED', 'STATEMENT', 'UNPARSED'
]

export async function classifySmsMessages(
  messages: ClassifyMessage[]
): Promise<ClassifyResult[]> {
  const model = getGenAI().getGenerativeModel({ model: 'gemini-2.0-flash' })

  const prompt = `You are classifying Indian bank SMS alerts for a personal finance app.
Classify each message as exactly one of: DEBIT, REFUND, SELF_TRANSFER, OTP, DECLINED, STATEMENT, UNPARSED.

Definitions:
- DEBIT: money spent on a purchase via card or account
- REFUND: credit on card for a returned purchase
- SELF_TRANSFER: transfer between the user's own accounts
- OTP: one-time password message
- DECLINED: a transaction that was declined
- STATEMENT: monthly statement or minimum payment due notification
- UNPARSED: cannot determine type with confidence

Input (JSON array):
${JSON.stringify(messages)}

Return ONLY a valid JSON array with no markdown fences:
[{"id": "<id>", "suggestedType": "<type>", "confidence": <0.0–1.0>}]`

  const result = await model.generateContent(prompt)
  const text = result.response.text().trim()
  const parsed: { id: string; suggestedType: string; confidence: number }[] = JSON.parse(text)

  return parsed.map((r) => ({
    id: r.id,
    suggestedType: VALID_TYPES.includes(r.suggestedType as TransactionType)
      ? (r.suggestedType as TransactionType)
      : 'UNPARSED',
    confidence: Math.min(1, Math.max(0, r.confidence))
  }))
}
