# CC Settle — Design Document

**Purpose:** Track credit card spends via SMS, aggregate into a running "pending to settle" balance, and nudge the user to self-transfer that amount into the account their card bill is paid from — full or partial, over UPI, one tap.

**Status:** v2 — updated to match the high-fidelity design handoff (`CC Settle.dc.html` + `README.md`). Functional changes from v1 are the daily-cap-aware gauge, partial settles, a waiting-for-credit-SMS state, an inline Claude classifier suggestion in the review queue, and a first-run permission flow. UI direction (dark theme, tokens, typography) is now owned by that handoff; this doc stays the logic/architecture source of truth.

---

## 1. Problem statement

The user pays all expenses on a credit card and manually self-transfers the equivalent amount to a secondary bank account, from which the card bill gets paid. Today this relies on memory. The app should track this automatically, respect real-world constraints (UPI daily caps, partial payments), and remove the manual reconciliation.

## 2. Non-goals

- No bank statement / Account Aggregator integration (no FIU registration available to an individual).
- No automatic money movement. UPI requires PIN entry by design (NPCI); the app only prepares a prefilled intent.
- No budgeting, categorization, or spend analytics beyond what's needed for the settle balance.
- Not published to Play Store initially — sideloaded APK, since SMS read permission is Play-restricted for this use case.

## 3. Why SMS, not an API

| Source | Real-time | Available to individual |
|---|---|---|
| Account Aggregator (RBI framework) | Periodic | No — regulated entity only |
| Setu / Perfios / Decentro | Periodic | No — business contract |
| Bank developer APIs | Varies | No — corporate banking only |
| SMS alerts | Instant | **Yes** |

SMS parsing is the only real-time, individually-accessible option in India today.

## 4. High-level architecture

```
Bank SMS (debit + credit alerts)
        │
        ▼
SmsReceiver (BroadcastReceiver, SMS_RECEIVED)
        │
        ▼
Parser — classify + extract amount, ref, card last-4
        │
        ├──▶ confident → Room ledger
        │
        └──▶ unparsed → review queue ──▶ nightly Claude batch classify (suggestion + confidence, not auto-applied)
        │
        ▼
Room ledger  (pendingPaise = Σdebits − Σrefunds − Σmatched self-transfers)
        │
        ▼
Daily digest job (WorkManager, e.g. 22:00)
        │
        ├──▶ Email / push: live pending amount, one settle link
        │        │
        │        ▼
        │    Settle screen (in-app or static redirect page) → adjustable amount → upi:// intent
        │        │
        │        ▼
        │    Waiting state → match credit SMS → full or partial receipt
        │
        └──▶ if pendingPaise > dailyCapPaise: split into multiple settle events up front
```

## 5. Data model (Room)

**`transactions`**
| column | type | notes |
|---|---|---|
| id | Long (PK) | |
| raw_sms | String | original text, for debugging/re-parse |
| amount_paise | Long | never Double — avoid float drift |
| type | Enum | `DEBIT / CREDIT / REFUND / SELF_TRANSFER / OTP / DECLINED / STATEMENT / UNPARSED` |
| card_last4 | String? | null for savings-account SMS |
| bank | String | |
| txn_time | Instant | |
| sms_time | Instant | may differ from txn_time |
| dedupe_hash | String | hash(bank + amount + card_last4 + rounded txn_time) |
| matched_settle_event_id | Long? | FK, set when this credit clears a settle event |
| suggested_type | Enum? | from nightly Claude batch classify, `UNPARSED` rows only |
| suggested_confidence | Float? | 0–1, shown in the review queue |
| reviewed | Boolean | true once confirmed (manually or one-tap accept) |

**`settle_events`** — one row per settle attempt, not per day. A day with a partial pay + a top-up produces two rows sharing the same `parent_ref`.
| column | type | notes |
|---|---|---|
| id | Long (PK) | |
| parent_ref | String | day-level ref, e.g. `CCS20260801` |
| suffix | String? | `A`, `B`… when the day has more than one settle event (partial, or split-above-cap) |
| status | Enum | `AWAITING / CLEARED / PARTIAL / MANUAL_MATCH / EXPIRED` |
| requested_amount_paise | Long | what the user chose to send (may be < pending at creation time) |
| pending_snapshot_paise | Long | total pending balance *at the moment this event was created* |
| created_at | Instant | |
| cleared_at | Instant? | set when a matching credit SMS arrives |
| cleared_amount_paise | Long? | may differ from requested if the user's UPI app rounds |

**Derived, never stored:**
```
pendingPaise = Σ DEBIT − Σ REFUND − Σ (cleared or partially-cleared SELF_TRANSFER)
```

Money is always **paise in a Long**. Display with Indian digit grouping (`₹1,04,300`), not Western thousands-grouping.

## 6. SMS classification rules

Classify before extracting amount, in this order:

1. **OTP** → discard, never counts.
2. **Declined** → discard.
3. **Statement** → discard from ledger, store separately.
4. **Credit / refund** on card → `REFUND`, subtract.
5. **Credit on savings matching an `AWAITING` settle event** (amount + time window) → clears that event; if `cleared_amount_paise < requested_amount_paise` mark `PARTIAL`, else `CLEARED`.
6. **Debit** on card → `DEBIT`, add.
7. **Anything else** → `UNPARSED`, enters the review queue.

Dedupe every inbound SMS on `hash(bank, amount, card_last4, txn_time rounded to minute)`.

**Nightly Claude fallback:** a batched call classifies the `UNPARSED` backlog and writes `suggested_type` + `suggested_confidence` — it never auto-applies. The review queue surfaces this as a single suggestion strip (e.g. "Claude suggests: Ignore · pre-auth hold · confidence 0.91"); accepting it is one tap, not a re-decision. Settings expose a toggle to disable this and a "re-parse all stored SMS" action for when classification rules improve.

## 7. The settle loop

1. Digest job computes `pendingPaise`. If `pendingPaise > dailyCapPaise`, pre-split into multiple settle events (each ≤ cap) rather than emitting one link that will fail at the bank.
2. For each settle event: `parent_ref` = day code, `suffix` assigned if more than one event exists that day.
3. Email/push carries the live pending amount and a link to the settle screen (in-app deep link, or a static redirect page if opened outside the app — `upi://` cannot be linkified in email directly).
4. **Settle screen is adjustable, not fixed.** Default `settleDraftPaise = pendingPaise` (or the event's `requested_amount_paise`), but a slider (¼ / ½ / custom / full) lets the user send less. Whatever isn't sent stays in `pendingPaise` — the running balance never resets on a partial.
5. Pay fires `upi://pay?pa=<vpa>&pn=Self&am=<amount>&cu=INR&tn=CC%20settle&tr=<ref>` and hands off to the UPI chooser. PIN entry is never automated.
6. App moves to a **waiting** state. A credit SMS matching `(amount, ±time window)` clears the event:
   - Full match → success screen.
   - Partial match (`cleared_amount_paise < requested_amount_paise`) → partial receipt: shows settled share vs. remainder, remainder keeps accruing, next digest asks for remainder + new spend.
   - No match within ~5 minutes → offer manual "mark as settled" confirmation rather than leaving the user stuck.
7. A partial settle that's later topped up creates a second settle event (`suffix` B) against the same `parent_ref`; history renders both as one thread.

**Why not embed `upi://` directly in an email:** mail clients only linkify `http(s)`. If the digest is delivered by email rather than push, it must link to a static or in-app settle screen that itself constructs the `upi://` intent.

## 8. Edge cases

- **Amount goes stale** — spend after the digest is sent under-transfers. The settle screen shows the amount as "live · updates as you spend," not a frozen snapshot.
- **Above daily UPI cap** — split into multiple settle events per (7.1) rather than one failing link.
- **Partial settle** — first-class flow, not an error state (see §7, §9 partial screens).
- **Multiple cards** — disambiguate by last-4; aggregate into one combined pending balance by default, each card toggleable off in Settings.
- **EMI conversion** — flagged to the review queue for manual handling in v1, not auto-corrected.
- **Fuel/hotel pre-auth holds** — excluded by keyword rules ("hold", "authorization"); confirmed only once the actual debit SMS lands.
- **OEM battery killers** (MIUI, ColorOS, Funtouch) — WorkManager jobs get silently killed. First-run flow must request battery-optimization exemption and OEM-specific autostart, and Settings surfaces a persistent alert if the exemption is later revoked.
- **Missed / misclassified SMS** — the review queue plus nightly Claude fallback is the safety net; no silent auto-correction.

## 9. App structure

**Stack:** Kotlin, Jetpack Compose, Room, WorkManager, `BroadcastReceiver` for `SMS_RECEIVED`, DataStore for preferences, `EncryptedSharedPreferences` for any stored credentials.

**Screens** (see the design handoff for exact visuals — this is the functional map):

1. **Home** — gauge (denominator = `dailyCapPaise`, not an arbitrary max) showing pending vs. settled-today as two arc segments once a partial exists; three stat cards (today's spend, last settle, review count); live "Settle ₹X now" CTA; recent activity list; tab bar with a review-queue badge.
2. **Settle (adjustable)** — hero amount editable via slider + quick chips (¼ / ½ / custom / full); three-line ledger (pending now / you send / **stays pending**); UPI app picker; daily-cap caution panel when relevant; CTA label tracks the chosen amount.
3. **Waiting for credit SMS** — pulse animation, 3-step checklist (link generated / handed off / awaiting credit), "no SMS after 5 min?" manual-confirm fallback.
4. **Partial receipt** — settled vs. remainder split bar, reference suffix, "send the remaining ₹X" CTA plus "done."
5. **Review queue** — one unparsed SMS at a time (swipeable stack), raw text verbatim, inline Claude suggestion + confidence, three actions (ignore / refund / debit), progress counter.
6. **History** — stats (settled this month, streak, or carried-over amber stat when a partial is outstanding), a sparkline of daily settle amounts, and a list of settle events — full settles compact, partial/topped-up events shown with a split indicator.
7. **Settings** — battery-optimization alert (when relevant), settle destination (VPA, digest time/email, split-above-cap toggle), per-card toggles, parser section (Claude fallback toggle, re-parse-all action).
8. **First-run onboarding** — a short permission sequence (SMS read, battery exemption, OEM autostart, test the digest job), each step showing done/current/upcoming state, with a "what we'll read" disclosure and a skip-to-manual option.

## 10. State model

Keeping this explicit avoids the app and the digest job disagreeing about "the" balance:

- `pendingPaise` — derived, never stored.
- `todaySpendPaise`, `unreviewedCount`, `lastSettleAt`, `streakDays` — derived/cached for Home and History.
- `settleDraftPaise` — the in-progress slider value on the settle screen; defaults to the relevant settle event's `requested_amount_paise`.
- `activeSettleEvent` — the `settle_events` row currently in `AWAITING`, drives the waiting screen.
- `dailyCapPaise` — from Settings; the gauge's denominator and the split threshold.
- `reviewQueue` — `UNPARSED` transactions, each optionally carrying `suggested_type` / `suggested_confidence`.
- Settings: `vpa`, `digestTime`, `digestEmail`, `splitAboveCap`, `cardsEnabled[]`, `claudeFallbackEnabled`.

## 11. UI/UX direction

The design handoff (`CC Settle.dc.html`) is the source of truth for visuals — dark theme, Instrument Sans + JetBrains Mono, amber/green/red semantics, the arc gauge, and the swipeable review stack. Functional principles that constrain any reimplementation:

- **The gauge's denominator is the daily UPI cap, not an arbitrary max** — it's the number that determines whether a spend is "too big to settle in one go."
- **A partial settle never resets the gauge or the balance.** Whatever isn't sent stays pending and keeps accruing.
- **Accepting a Claude suggestion in the review queue is one tap.** The suggestion is a shortcut, not a re-decision the user has to reason through again.
- **The settle amount is always live**, both in the CTA label and on the settle screen itself — never a frozen number from when the digest was generated.
- **No PIN automation, ever** — every settle hands off to the UPI app chooser and waits.

Earlier mockups in this conversation (light theme, static full amount only) are superseded by this handoff for anything they conflict with — the dark palette, adjustable/partial settle, and gauge are the current direction.

## 12. Security notes

- Card numbers stored as last-4 only; never full PAN.
- SMTP/app-password or API keys go in `EncryptedSharedPreferences`, never plain DataStore.
- The settle link carries amount and VPA in a URL — acceptable for a personal, self-directed transfer; don't extend this pattern to anything shared or third-party.

## 13. Build phases

1. **Phase 1** — SMS receiver + parser + Room ledger + Home (fixed full-amount gauge), verify classification accuracy against real SMS.
2. **Phase 2** — Digest job + settle screen (fixed amount) + waiting state + full-clear matching.
3. **Phase 3** — Review queue (swipeable, with nightly Claude fallback) + Settings (cards, VPA, digest, parser toggles) + first-run onboarding.
4. **Phase 4** — Partial settle (adjustable amount, split ledger, partial receipt, history split rendering) + daily-cap pre-splitting.
5. **Phase 5 (optional)** — Android App Link so digests open the app directly; monthly statement PDF reconciliation as a truth-up layer.
