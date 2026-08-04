// Mirror of android/SmsParser.kt — for dev testing only

const AMOUNT_REGEX = /(?:INR|Rs\.?|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)/i

const CARD_LAST4_REGEX = /(?:XX|x{2,4}|ending\s*|[Cc]ard\s+(?:no\.?\s*)?)(\d{4})/i

const OTP_KEYWORDS      = ['otp', 'one time password', 'one-time', 'do not share']
const DECLINED_KEYWORDS = ['declined', 'failed', 'unsuccessful']
const STATEMENT_KEYWORDS = ['statement', 'minimum amount due', 'min. amount due', 'payment due']
const HOLD_KEYWORDS     = ['hold', 'authorization hold', 'pre-auth', 'pre auth']
// 'credited' not 'credit' — avoids matching "credit card" as a credit transaction
const CREDIT_KEYWORDS   = ['credited', 'refund', 'cashback', 'reversal', 'money back']
const DEBIT_KEYWORDS    = ['debited', 'debit', 'withdrawn', 'spent', 'used at', 'purchase', 'payment of', 'paid']
const CARD_KEYWORDS     = ['card', 'credit card', 'debit card', 'xx']

const BANK_SENDER_MAP: Record<string, string> = {
  HDFCBK: 'HDFC', HDFCBN: 'HDFC',
  SBIINB: 'SBI',  SBICRD: 'SBI',
  ICICIB: 'ICICI', ICICIN: 'ICICI',
  AXISBK: 'Axis', AXISBN: 'Axis',
  KOTAKB: 'Kotak', KOTAKN: 'Kotak',
  YESBNK: 'Yes Bank', IDFCBN: 'IDFC',
  INDBNK: 'IndusInd', IDFCCD: 'IDFC',
  PNBSMS: 'PNB',
  BOIIND: 'BOI',
  SLICEIT: 'Slice', SLICEPA: 'Slice'
}

export type TransactionType =
  'DEBIT' | 'REFUND' | 'SELF_TRANSFER' | 'UNPARSED' | 'OTP' | 'DECLINED' | 'STATEMENT'

export interface ParsedSms {
  type: TransactionType
  amountPaise: number | null
  cardLast4: string | null
  bank: string
}

export function parseSms(sender: string, body: string): ParsedSms {
  const lower = body.toLowerCase()
  const bank  = Object.entries(BANK_SENDER_MAP)
    .find(([k]) => sender.toUpperCase().includes(k))?.[1] ?? sender
  const cardLast4 = CARD_LAST4_REGEX.exec(body)?.[1] ?? null

  const match = (keywords: string[]) => keywords.some(k => lower.includes(k))

  if (match(OTP_KEYWORDS))       return { type: 'OTP',       amountPaise: null, cardLast4: null, bank }
  if (match(DECLINED_KEYWORDS))  return { type: 'DECLINED',  amountPaise: null, cardLast4,       bank }
  if (match(STATEMENT_KEYWORDS)) return { type: 'STATEMENT', amountPaise: null, cardLast4,       bank }
  if (match(HOLD_KEYWORDS))      return { type: 'UNPARSED',  amountPaise: null, cardLast4,       bank }

  const amount   = extractAmountPaise(body)
  const isCredit = match(CREDIT_KEYWORDS)
  const isDebit  = match(DEBIT_KEYWORDS)
  const isCard   = match(CARD_KEYWORDS)

  // Debit checked first — "spent/debited" wins even if "credit card" appears in the text
  if (isDebit && amount !== null) {
    return { type: 'DEBIT', amountPaise: amount, cardLast4, bank }
  }
  if (isCredit && amount !== null) {
    return { type: isCard ? 'REFUND' : 'SELF_TRANSFER', amountPaise: amount, cardLast4, bank }
  }
  return { type: 'UNPARSED', amountPaise: amount, cardLast4, bank }
}

export function extractAmountPaise(body: string): number | null {
  const match = AMOUNT_REGEX.exec(body)
  if (!match) return null
  const raw = match[1].replace(/,/g, '')
  const rupees = parseFloat(raw)
  return isNaN(rupees) ? null : Math.round(rupees * 100)
}
