# Handoff: CC Settle — credit-card self-settle app

## Overview
CC Settle is a personal (sideloaded) Android/iOS app that reads bank SMS alerts, aggregates credit-card
spend into a running "pending to settle" balance, and nudges the user to self-transfer that amount over
UPI into the account their card bill is paid from. The original product spec is included as
`cc-settle-app-design.md` — read it first; it defines the SMS classifier, Room schema, WorkManager
digest job and the settle loop. This document covers the **UI** only.

## About the Design Files
`CC Settle.dc.html` is a **design reference created in HTML** — a prototype showing intended look and
behaviour, not production code to copy. The task is to **recreate these screens in the target codebase's
own environment** (the spec proposes Kotlin + Jetpack Compose) using its established patterns, theming
and component library. If no codebase exists yet, pick the most appropriate framework and implement
there. Do not ship the HTML.

The file is a canvas of phone-sized screen mocks laid out in two "turns":
- **Turn 1 (`#1a`–`#1g`)** — the base screen set.
- **Turn 2 (`#2a`–`#2d`)** — the partial-settle flow, added later. Where turn 2 and turn 1 disagree
  (Home, History), **turn 2 is the newer intent**.

Open the file in a browser to view. Each screen is a 390 × 844 frame (iPhone-class logical size) inside
a decorative bezel; the bezel is presentation only — ignore its 9px border and 46px radius.

## Fidelity
**High-fidelity.** Colours, type, spacing, radii and copy are final and should be matched closely.
Screens are static mocks — no working interaction is implemented, so behaviour below is the spec.

---

## Design Tokens

### Colour
| Token | Hex | Use |
|---|---|---|
| bg | `#0A0B0D` | screen background |
| bg-nav | `#0C0E11` | bottom tab bar |
| surface | `#101317` | cards, list rows, inputs |
| surface-raised | `#141920` | the top card in a stack (review queue), emphasised rows |
| surface-sunken | `#0C0F13` / `#0D1014` | raw-SMS block, dashed info panels |
| track | `#1B1E24` / `#16191E` | slider + gauge tracks |
| chip | `#1C2029` | UPI app icon placeholders |
| border | `rgba(255,255,255,.06)` | default card border |
| border-strong | `rgba(255,255,255,.09)` | raised card border |
| divider | `rgba(255,255,255,.05)` | inside-card rules |
| divider-list | `rgba(255,255,255,.045)` | activity-row rules |
| text | `#F2F3F5` | primary |
| text-2 | `#EDEFF2` / `#D6DAE0` / `#C6CBD2` | secondary tiers |
| text-3 | `#8A9099` | labels |
| text-4 | `#6C737D` | body-muted |
| text-5 | `#5C636D` / `#525963` | metadata |
| text-6 | `#4E555F` / `#3B4149` | disabled / inactive tab |
| amber | `#FFB020` | active accent, "owed" |
| amber-bright | `#FFC94D` | balance numerals |
| amber-deep | `#FF8A3D` | gradient end, remainder emphasis |
| amber-ink | `#14100A` | text on amber buttons |
| amber-tint-bg | `rgba(255,176,32,.06)` | info/warn panel fill |
| amber-tint-border | `rgba(255,176,32,.14)`–`.32` | info/warn panel border |
| amber-icon-bg | `#17110A` | merchant avatar fill |
| green | `#3ECF8E` | settled, cleared, granted |
| green-bg | `#0C1712` | green avatar/badge fill |
| red | `#FF6B6B` | ignore / destructive / error |
| red-bg | `#150F0F` | red circle button fill |
| red-text | `#FF9A9A` / `#8A6A6A` | error title / error body |
| blue | `#7CC4FF` | Claude suggestion accent (and link colour) |

Gradient (primary CTA + gauge fill): `linear-gradient(100deg, #FFB020, #FF8A3D)`;
gauge stroke gradient runs `#FF8A3D` → `#FFC94D` bottom-left to top-right.
CTA shadow: `0 8px 26px rgba(255,150,40,.22)`.

### Typography
Two families, loaded from Google Fonts:
- **Instrument Sans** (400/500/600/700) — all UI prose, labels, buttons.
- **JetBrains Mono** (400/500/700) — every number, reference code, timestamp, raw SMS, tab label, and
  any all-caps micro-label. All numeric displays use `font-variant-numeric: tabular-nums` so the
  balance does not jitter as it updates.

| Role | Spec |
|---|---|
| Screen title | Instrument Sans 700 · 19px · `-0.3px` |
| Big statement (onboarding) | Instrument Sans 700 · 27px/1.2 · `-0.8px` |
| Success headline | Instrument Sans 700 · 21px · `-0.4px` |
| Hero balance — integer | JetBrains Mono 700 · 37px · `-1.4px` · `#FFC94D` |
| Hero balance — ₹ / decimals | JB Mono 500 · 19px `#FFB020` / 16px `rgba(255,201,77,.5)` |
| Settle-sheet amount | JB Mono 700 · 42px (₹ 22px, decimals 18px) |
| Micro-label (all-caps) | JB Mono 500 · 10–10.5px · `+1.0–1.4px` · `#4E555F`–`#5C636D` |
| Stat value | JB Mono 700 · 17–18px |
| List primary | Instrument Sans 600 · 13.5px |
| List meta | JB Mono 400 · 10.5px · `#525963` |
| List amount | JB Mono 600 · 14px |
| Body copy | Instrument Sans 400 · 13.5px/1.65 or 11.5px/1.5 · `text-wrap: pretty` |
| Primary button | Instrument Sans 700 · 15.5px |
| Secondary button | Instrument Sans 600 · 14px |
| Tab label | JB Mono 600 · 9.5px |
| Raw SMS | JB Mono 400 · 14px/1.65 · `#D6DAE0` |

### Spacing, radius, shape
- Screen horizontal padding **26px** (30px on the onboarding screen).
- Vertical rhythm: 14 / 16 / 18 / 20 / 22 / 26 / 30–34px between blocks.
- Radii: cards **14–20px**, buttons **16px**, small chips **12–13px**, avatars **11px**,
  icon buttons **10px**, pills **99px**, review card **24px**.
- Icon buttons 34 × 34 (header) and 60 × 60 (review actions, circular).
- Tab bar: `border-top: 1px solid rgba(255,255,255,.06)`, padding `12px 26px 26px`.
- Icons are 1.8–2.4px stroke line icons (Lucide-equivalents), 15/17/19/22/28/30px.

---

## Screens / Views

### 1. Home — `#1a` (see `#2c` for the post-partial variant)
**Purpose:** see what you owe yourself and settle it in one tap.

Vertical stack:
1. **Header** — "CC Settle" (700/15px) over a mono status line `listening · 3 cards`; a 34px circular
   settings button on the right.
2. **Gauge** — a 330 × 234 SVG (`viewBox 0 0 200 200`) drawn as a **270° arc**: `<circle r=86`,
   `stroke-width 13`, `stroke-linecap round`, `stroke-dasharray "405 541"`, `transform="rotate(135 100 100)"`
   so the gap sits at the bottom. Track `#16191E`; fill is the same circle with
   `stroke-dasharray "<405 × pct> 541"` and the amber gradient. Absolutely-positioned centre stack:
   micro-label `PENDING TO SETTLE`, the balance, then `of ₹68,000 daily UPI cap`.
   The gauge's *denominator is the bank's daily UPI cap*, not an arbitrary max — that's the number that
   makes a spend "too big to settle in one go". Keep the balance type at 37px: it must clear the inner
   arc diameter (~260px) even at 7 digits (₹1,04,300).
3. **Three stat cards** in a `gap:9px` row — TODAY (spend), LAST SETTLE (relative time, green),
   REVIEW (unparsed count, `#FF8A3D`).
4. **Primary CTA** — full-width, 17px padding, amber gradient, arrow icon, label
   `Settle ₹42,380 now` (the amount is in the label, always live).
5. **Recent activity** — section header + "SEE ALL"; rows of 36px avatar (2-letter merchant monogram on
   `#17110A`, or a green refund arrow on `#0C1712`) / merchant + `BANK ·last4 · time` / signed amount.
   Debits are `+₹642` in white (they *add* to what you owe); refunds and settles are `−` in green.
6. **Tab bar** — HOME / REVIEW / HISTORY / MORE, active in amber, REVIEW carries a 7px `#FF8A3D` dot
   when the queue is non-empty.

### 2. Settle — `#1b` (superseded for amount entry by `#2a`)
Back header; hero amount with a live pill (`live · updates as you spend`); a 3-row detail card
(To / Reference / Note) using the `tr=` ref from the spec (`CCS20260801`); an "OPEN IN" row of four
equal tiles (GPay, PhonePe, Paytm, QR) — the app icons are **placeholder 34px squares**, substitute real
marks; an amber caution panel restating the daily-cap split and that PIN entry happens in the UPI app;
full-width CTA `Pay ₹42,380 →`.

### 3. Waiting for credit SMS — `#1c`
Post-handoff state. Centred 150px pulse target: two concentric rings animating
`@keyframes ccpulse { 0%,100% { opacity:.35; transform:scale(1) } 50% { opacity:.9; transform:scale(1.35) } }`
over 2.6s ease-in-out, second ring delayed 0.5s, around a 74px green SMS glyph. Headline
"Waiting for the credit SMS" + explanation. A 3-step checklist (link generated / handed off / awaiting
credit) — done steps get a filled green tick circle, the pending step a dashed `#4E555F` ring.
Pinned to the bottom: `no SMS after 5 min?` and a secondary "Mark as settled manually".

### 4. Review queue — `#1d`
Tinder-style classification of `UNPARSED` SMS. Title row with `1 / 4`, a 4-segment progress bar
(active amber, rest `#1B1E24`). A 400px card stack: two dead cards behind at `scale(.97)/top:8px` and
`scale(.94)/top:16px`, the live card `rotate(-1.6deg)` with `0 22px 50px rgba(0,0,0,.55)`.
Card contents: sender ID + timestamp, the **raw SMS verbatim** in a mono sunken block, a blue
Claude-suggestion strip (`Claude suggests: Ignore · pre-auth hold · confidence 0.91` — from the nightly
batch classifier in §6 of the spec), and the detected amount. A rotated `IGNORE` stamp (`-9deg`,
positioned *above* the card's top edge so it never covers live text) previews the swipe-left result.
Below: three 60px circular buttons — IGNORE (red ✕) / REFUND (green return-arrow) / DEBIT (amber +) —
with mono captions, and the hint "Swipe left to ignore · right to count it".

### 5. History — `#1e` (superseded by `#2d`)
Two stat cards (SETTLED · JULY, STREAK), a 12-bar sparkline of daily settle amounts (bars `#1E2229`,
today's bar amber-gradient, `flex:1` each with `gap:5px`, heights 30–88%), then a list of settle
events: status dot (amber = awaiting, green = cleared, red = needed manual match), ref code, a mono
sub-line describing how it resolved (`cleared in 2m 14s · ·8821`), and the amount.

### 6. Settings — `#1f`
A red alert card at the top when battery optimisation is on (the OEM-killer problem in §8 of the spec)
with a `FIX` affordance. Then grouped cards under mono all-caps headings:
- **SETTLE** — Your UPI ID, Digest time (22:00), Email digest to, "Split above daily cap" toggle.
- **CARDS TRACKED** — one row per card: bank code (amber if on), `·· last4`, toggle. Off rows go grey.
- **PARSER** — "Nightly Claude fallback" toggle, "Re-parse all stored SMS · 2,418 msgs →".

Toggle spec: 38 × 22 pill, on = `#FFB020` with an 18px `#14100A` knob right; off = `#22262D` with a
`#3B4149` knob left.

### 7. First run — `#1g`
`STEP 2 OF 4` eyebrow, 27px headline "Let it stay awake at 10 PM", explanation of why background jobs
die. Four permission rows: done (green tick + green-tinted border), **current** (amber ring, raised
surface, shadow, `ALLOW` action), and two upcoming (grey rings, dimmed text) — Autostart (MIUI) with the
literal system path, and "Test the 10 PM job". A dashed "WHAT WE'LL READ" panel states that only bank
senders are parsed and nothing is uploaded. CTA "Allow and continue" + a text "Skip — I'll settle
manually".

---

## Partial settle (turn 2) — the newer behaviour

The rule the UI must never break: **whatever you don't send stays owed, and the gauge does not reset.**

### 8. Settle, adjustable — `#2a`
Replaces `#1b`'s fixed amount. Hero shows the *amount being sent* (with a 2px caret bar suggesting
direct entry) and, below it, `of ₹42,380.00 pending`. A 6px track slider (0 → full pending) with a 22px
`#FFC94D` knob ringed by a 3px `#0A0B0D` border. Quick chips: `¼`, `½`, `CUSTOM` (active state =
`#17110A` fill, amber border and text), `FULL` (`flex:1.5`). Then the three-line ledger card:
Pending now / You send (− amber) / **Stays pending** on an amber-tinted, top-bordered final row in
`#FF8A3D` 700/15px. A dashed note explains the leftover keeps accruing and tonight's digest will ask for
it plus new spend. CTA label tracks the slider: `Pay ₹15,000 →`.

### 9. Partial receipt — `#2b`
Green tick, "₹15,000 credited to ·8821", "Matched your credit SMS in 38 seconds. This was a partial
settle." A **split bar**: 12px tall, `overflow:hidden`, green segment = settled share, amber-gradient
segment = remainder, with mirrored legends beneath (SETTLED green / STILL PENDING amber). Detail card:
Reference `CCS20260801-A` (partial settles suffix the day's ref with a letter), Type `PARTIAL · 35%`,
Next reminder. Two stacked CTAs: amber-outline "Send the remaining ₹27,380" and a neutral "Done".

### 10. Home after a partial — `#2c`
Same gauge geometry, now **three strokes**: track, then a green arc at `stroke-dasharray "143 541"` with
`stroke-opacity .28` for the settled portion, then the amber remainder at `stroke-dasharray "118 541"`
with `stroke-dashoffset "-146"` so it starts where green ends. Centre label becomes `STILL PENDING`
with a green sub-line `● ₹15,000 settled today`. LAST SETTLE reads `2m ago`. CTA becomes
"Settle remaining ₹27,380". Activity list gains a green "Partial settle" row.

### 11. History with partials — `#2d`
Second stat card becomes CARRIED OVER (amber). Partial events render as taller cards with a 7px split
bar and a right-hand two-line amount (sent in green, `₹27,380 left` in amber below). A partial that was
later topped up shows both segments green (the later one at 35% opacity) and the note
"partial → topped up next day". Full settles keep the compact dot row.

---

## Interactions & Behavior
- **Settle CTA (Home)** → Settle sheet, pre-filled with the live pending balance.
- **Slider / chips (`#2a`)** → update amount, ledger card and CTA label together on every frame.
  Clamp to `[0, pending]`; if the amount exceeds the configured daily UPI cap, split into multiple
  refs per the spec rather than emitting one link that will fail.
- **Pay** → build `upi://pay?pa=<vpa>&pn=Self&am=<amount>&cu=INR&tn=CC%20settle&tr=<ref>`, hand off to
  the chooser, then push the **waiting** state (`#1c`). Never attempt to automate PIN entry.
- **Waiting → cleared**: a credit SMS on the savings account matching `(amount, ±time window)` sets
  `cleared_at` and drops the balance. Full match → success; amount < settle amount → the partial
  receipt (`#2b`). Ambiguous → keep waiting; after 5 minutes offer manual confirmation.
- **Review swipe**: left = IGNORE, right = DEBIT, the middle button = REFUND. Card animates out with
  rotation; the stamp fades in proportionally to drag distance. Advance the `n / total` counter and
  progress bar. Accepting the Claude suggestion should be a single tap, not a re-decision.
- **Toggles** are optimistic; the battery/autostart rows deep-link to system intents and re-check on
  resume.
- **Gauge** animates its `stroke-dasharray` on balance change (~450ms, ease-out). On settle, the green
  segment grows from the arc start while the amber segment shrinks toward the end — they should never
  cross-fade, they should slide.
- No hover states matter on mobile; the mocks include `:hover` border-brightening on tappable cards for
  desktop preview only. Provide pressed states instead (scale 0.98 / surface lighten).

## State Management
- `pendingPaise` — derived, never stored: `Σ DEBIT − Σ REFUND − Σ matched SELF_TRANSFER`.
- `todaySpendPaise`, `unreviewedCount`, `lastSettleAt`, `streakDays`.
- `settleDraftPaise` — the slider value on `#2a`, default = `pendingPaise`.
- `activeSettleEvent` — `{ ref, amountPaise, createdAt, clearedAt? }`; drives the waiting screen.
- `dailyCapPaise` — from settings; the gauge denominator and the split threshold.
- `reviewQueue` — list of `UNPARSED` rows with optional `{ suggestedType, confidence }` from the
  nightly classifier.
- Settings: `vpa`, `digestTime`, `digestEmail`, `splitAboveCap`, `cardsEnabled[]`,
  `claudeFallbackEnabled`.
- All money is **paise in a Long** — never a float. Format for display with the Indian grouping
  (`₹1,04,300`), not Western thousands.

## Assets
- **Fonts**: Instrument Sans and JetBrains Mono, Google Fonts. Bundle them for an offline app.
- **Icons**: generic line icons (home, message-square, clock, menu, settings, arrow-right, check, x,
  corner-up-left, plus, star, alert-triangle, info, qr) — swap for the codebase's icon set at the same
  stroke weights.
- **UPI app marks** (GPay / PhonePe / Paytm on `#2a`/`#1b`) are **grey placeholder squares**. Real
  marks must come from each provider's brand kit; alternatively drop the tiles and use the system
  UPI chooser.
- No photography or illustration is used anywhere.

## Files
- `CC Settle.dc.html` — all 11 screens.
- `cc-settle-app-design.md` — the original product/architecture spec (parser rules, Room schema,
  settle loop, edge cases). The UI assumes it.
