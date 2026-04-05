# EVV State-by-State Reference

Source: research conducted April 2026. States update EVV rules frequently — verify before implementing any specific state connector.

Legend — Model: **Open** = provider choice vendor, **Closed** = state-mandated system only, **Hybrid** = open with caveats

---

## Aggregator Quick Reference

| Aggregator | States |
|---|---|
| **Sandata** | CT, MA, RI, VT, ME, DC, PA, OH, IN, WI, ND, CO, ID, NV, CA (agency/non-IHSS), NC (FFS), TN (FFS), AR (HHCS) |
| **HHAeXchange** | NJ, DE, IL, MI, MN, TX, OK (hybrid), FL (FFS), WV, AL, MS, NC (MCO), AR (PASSE) |
| **AuthentiCare** | NH, SC (closed), KS (closed), NM (verify—transition underway), AR (PCS) |
| **CareBridge** | WY, IA, TN (Amerigroup/UHC MCOs), AR (PASSE) |
| **Netsmart/Tellus** | GA, KY, MT, NE, FL (some MCOs), VA (some MCOs) |
| **Therap** | AK, ND (free option), SD (closed), KY (some programs, post-Jan 2025) |
| **State-built** | OR (eXPRS—closed), WA (ProviderOne), MD (LTSSMaryland—closed), AZ (AHCCCS EVV 2.0), MO (EAS), LA (LaSRS) |
| **CA IHSS (CDSS)** | California IHSS only — no third-party vendor path |
| **NY multi-aggregator** | NY: eMedNY + HHAeXchange + CareBridge — MCO/payer determines which |

---

## Closed Systems (no third-party vendor path)
- **MD** — LTSSMaryland app mandatory; GPS required; telephony retired
- **SC** — AuthentiCare mandatory
- **OR** — eXPRS mandatory
- **KS** — AuthentiCare mandatory
- **SD** — Therap mandatory
- **CA IHSS** — CDSS portal only; GPS not required; live-in exemption via SOC 2298
- **TN FFS** — Sandata/Therap mandated for direct TennCare (MCO is open)

---

## Published GPS Tolerances (rare — most states unpublished)
| State | Tolerance |
|---|---|
| Nebraska | 0.25 mi urban / 0.5 mi rural (tightened Feb 2025) |
| Kentucky | 0.5 mi from service address |
| Arkansas | 1/8 mi (660 ft) |
| Indiana | GPS mismatch never denies claims |
| Wisconsin | GPS informational only — never blocks payment |

---

## Extra Data Elements Beyond Federal 6
| State | Extra Requirement |
|---|---|
| Missouri | Tasks completed during the visit |
| New Jersey | Real-time push required (no batch) |
| New York | Near-real-time submission required |
| Hawaii | 15% manual entry cap (hard enforcement threshold) |
| Pennsylvania | Cell phone telephony excluded (landline only counts) |

---

## Co-Resident / Live-In Exemptions
| State | Rule |
|---|---|
| California IHSS | SOC 2298 co-residency self-certification — submit daily hours only, no clock-in/out |
| Washington | Individual Providers living with client are exempt |
| Alaska | Live-in exemption documented |
| Oklahoma | Opposite — live-in workers explicitly IN scope (unusual) |

---

## Multi-Aggregator States (payer/MCO determines aggregator)
| State | Routing Logic |
|---|---|
| New York | MCO/payer determines: eMedNY, HHAeXchange, or CareBridge |
| Florida | FFS → HHAeXchange; some MCOs → Tellus/Netsmart |
| North Carolina | FFS → Sandata; MCO → HHAeXchange (which then pushes to Sandata) |
| Virginia | No single aggregator; MCO mandates vary (HHAeXchange, Sandata, Tellus) |
| Tennessee | FFS → Sandata/Therap; Amerigroup/UHC MCOs → CareBridge |
| Arkansas | PCS → AuthentiCare; HHCS → Sandata; PASSE → HHAeXchange/CareBridge |

---

## Notable Quirks by State

| State | Quirk |
|---|---|
| AZ | EVV 2.0 (Oct 2025): AHCCCS-built aggregator replaced Sandata; providers who used Sandata as vendor must now contract directly |
| AR | Most fragmented aggregator landscape: 3–4 aggregators depending on program type |
| CA | IHSS is a completely separate integration — do not conflate with CalEVV/Sandata |
| DE | Transitioned from Sandata to HHAeXchange — stale docs may reference Sandata |
| FL | Must support both HHAeXchange (FFS) and Tellus (some MCOs) |
| HI | 15% manual entry hard cap — exceeding triggers claim scrutiny |
| KS | "Learn Mode" GPS — first 20 visits calibrate location baseline |
| MD | Closed, no telephony, GPS mandatory; biggest abstraction challenge in Northeast |
| MO | Tasks-completed is an extra required data element (only state with this) |
| NE | Tightened GPS radius Feb 2025 (from 2 mi to 0.25/0.5 mi) — rural provider pain point |
| NY | Payer-level aggregator routing: one agency may route to 3 different aggregators |
| OK | Live-in caregivers explicitly in EVV scope (most states exempt them) |
| OR | eXPRS is also claims system — EVV and billing tightly coupled |
| PA | Cell phone IVR calls excluded from compliance rate; 85% threshold enforced |
| TX | HHAeXchange is state portal (free) but hard EVV edits — claims deny on mismatch |
| WA | Two state agencies: DSHS/ALTSA (PCS) and HCA (home health) |

---

*Last updated: 2026-04-04*
