# Ledge Cred — CC Settle

Personal Android app that reads bank SMS alerts, aggregates credit-card spend into a running "pending to settle" balance, and nudges you to self-transfer that amount over UPI into the account your card bill is paid from.

## Why

No bank API is available to individual developers in India. SMS alerts are the only real-time, individually-accessible data source. The app parses them, keeps a running ledger, and handles the UPI daily-cap split, partial settles, and the credit-SMS matching loop.

## Docs

- [`design_handoff_cc_settle/cc-settle-app-design.md`](design_handoff_cc_settle/cc-settle-app-design.md) — architecture spec: SMS classifier, Room schema, WorkManager digest job, settle loop, edge cases.
- [`design_handoff_cc_settle/README.md`](design_handoff_cc_settle/README.md) — UI design handoff: design tokens, screen-by-screen breakdown, interactions, state model.
- [`design_handoff_cc_settle/CC Settle.dc.html`](design_handoff_cc_settle/CC%20Settle.dc.html) — high-fidelity screen mocks (open in browser).

## Stack

Kotlin · Jetpack Compose · Room · WorkManager · BroadcastReceiver (SMS_RECEIVED) · DataStore · EncryptedSharedPreferences

## Status

Design phase. See build phases in the architecture doc.
