# Competitive Notes: hcare vs. AxisCare (and others)

Running comparison tracked during brainstorming. Updated as decisions are made.

---

## Target Market

| Dimension | AxisCare | hcare |
|---|---|---|
| Agency size | Mid-market to enterprise (sweet spot: 25–200+ caregivers) | Small agencies (1–25 caregivers) — intentional beachhead |
| Geography | US + 6 other countries | TBD |
| Care type | Primarily non-medical personal care | Both non-medical and skilled nursing/medical |

**Insight:** AxisCare doesn't have a strong small-agency self-serve play. hcare targets the underserved end of the market.

---

## Monetization

| Dimension | AxisCare | hcare |
|---|---|---|
| Model | Per-active-client tiers, custom quotes | Flat tiers with free trial / freemium entry point |
| Starting price | ~$200/month, requires sales contact | TBD — transparent, self-serve |
| Self-serve signup | No | Yes — a differentiator |

**Insight:** No sales call required to start is itself a UX win and a positioning statement for small agencies.

---

## Product Scope

| Module | AxisCare | hcare MVP (P1) | hcare P2 |
|---|---|---|---|
| Scheduling | Yes | Yes | — |
| Caregiver mobile app | Yes | Yes | — |
| Agency admin back-office | Yes | Yes | — |
| Billing & claims / EVV | Yes (included in base) | Designed for, not built | Yes |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Core backend API | Spring Boot 3.x latest GA + Java 25 |
| Mobile BFF | Spring Boot 3.x latest GA + Java 25 (lightweight) |
| Web admin frontend | React (TypeScript) |
| Caregiver mobile | React Native |
| Database | H2 in-memory (dev/MVP) → PostgreSQL (production, P2) |
| Auth | Spring Security + JWT |

---

## UX / Experience

| Dimension | AxisCare | hcare |
|---|---|---|
| Reputation | Best UX in market per user | Goal: match or exceed |
| Onboarding | Sales-led | Self-serve, fast time-to-value |

---

## Delighter Competitive Matrix

Legend: ✅ Has it (differentiator) | ⚠️ Partial / table stakes | ❌ No / manual only | 🔓 Open — nobody owns it

| # | Delighter | WellSky | AlayaCare | AxisCare | hcare opportunity |
|---|---|---|---|---|---|
| 1 | Instant shift-fill broadcast | ⚠️ Self-serve board, not proactive push | ✅ Named feature: push/SMS/email + one-tap accept | ⚠️ Has it, solid but less marketed | Match AlayaCare; table stakes for MVP |
| 2 | Caregiver wellness & recognition | ✅ TeamEngage/Zingage add-on (points, gift cards, predictive turnover) | ⚠️ Caribou Rewards 3rd-party integration | ⚠️ Caribou Rewards 3rd-party integration | 🔓 **Native built-in = true differentiator** |
| 3 | Offline-first mobile | ⚠️ Mature for skilled; weak for personal care | ✅ Mature, well-marketed for rural | ⚠️ Newer but functional | Must-have; match competitors |
| 4 | Smart mileage tracking (auto GPS) | ❌ Manual entry only | ❌ GPS only for EVV clock-in/out | ❌ Manual entry only | 🔓 **Nobody has this — wide open** |
| 5 | Real-time family portal | ⚠️ "Family Room" — mature but aging UX | ✅ Most developed: live status, notes, vitals, change requests | ⚠️ Functional, less polished | Match AlayaCare with better UX |
| 6 | Caregiver profile & family matching | ❌ Agency-side only, families can't browse | ⚠️ Care team visible + post-visit reviews; no self-select | ❌ Agency-side AI only | 🔓 **Consumer-grade caregiver browsing = differentiator** |
| 7 | AI scheduling suggestions | ❌ Rule-based filtering only | ✅ Real ML + autonomous Vacant Visit Agent | ⚠️ "AxisCare Intelligence" — weighted constraint matching | Match AxisCare level for small agencies |
| 8 | One-page agency dashboard | ❌ Report-heavy, scattered | ⚠️ Closest: Operations Dashboard with live KPIs | ⚠️ Analytics-skewed, not operational | 🔓 **True single command-center screen = open** |
| 9 | Instant EVV compliance status | ❌ Reports only; had compliance failures late 2025 | ⚠️ Per-visit exception flagging (workflow-gated) | ⚠️ Reporting module only | 🔓 **Ambient red/yellow/green per-visit = open** |
| 10 | Referral & growth tools | ⚠️ Enterprise Referral Manager (separate product, premium) | ⚠️ Built-in intake module, not a growth CRM | ✅ Built-in CRM with lead conversion tracking — best for SMB | AxisCare owns this for SMB; compete or defer |

### hcare Chosen Delighters
- **#7 AI scheduling suggestions** — smart caregiver match recommendations (skills, distance, availability, preferences). AxisCare has weighted constraint matching; hcare will match and frame it as a small-agency-friendly UX.
- **#9 Instant EVV compliance status** — ambient red/yellow/green per-visit indicator. Nobody owns this cleanly. Turns audit anxiety into a daily confidence feature.

### Key Takeaways
- **Wide-open differentiators (nobody owns):** Smart mileage tracking (#4), one-page command-center dashboard (#8), ambient EVV compliance status (#9), native caregiver wellness (#2), consumer-grade caregiver profiles (#6)
- **Table stakes (must match):** Offline mobile (#3), shift-fill broadcast (#1), family portal (#5)
- **AxisCare is the benchmark:** Their UX and SMB-focused CRM (#10) and AI scheduling (#7) are what you're directly competing against
- **AlayaCare over-engineers for small agencies:** Their AI agents and ML depth are enterprise-grade — not what a 10-caregiver agency needs

---

*Last updated: 2026-04-04 — brainstorming session*
