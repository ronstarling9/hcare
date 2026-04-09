# hcare Integration Landscape: Competitive Intelligence & Architecture Guide

> Research date: 2026-04-08. Four parallel research agents covering 13 competitors, 5 clearinghouses, 6 EVV aggregators, 50 states, and the full open-source tooling ecosystem.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Competitor Integration Ecosystems](#2-competitor-integration-ecosystems)
3. [Clearinghouse & Billing Integrations](#3-clearinghouse--billing-integrations)
4. [EVV Aggregator Integrations](#4-evv-aggregator-integrations)
5. [EHR / Hospital Referral Integrations](#5-ehr--hospital-referral-integrations)
6. [Payroll & HR Integrations](#6-payroll--hr-integrations)
7. [Other Integration Categories](#7-other-integration-categories)
8. [Integration Architecture for hcare](#8-integration-architecture-for-hcare)
9. [Open-Source Tool Survey](#9-open-source-tool-survey)
10. [Strategic Priorities](#10-strategic-priorities)

---

## 1. Executive Summary

Homecare agency management platforms compete on integration breadth as much as on core features. The integration surface splits into four compliance-critical categories — EVV aggregators, clearinghouse billing, prior authorization, and EHR referral intake — plus a secondary tier of payroll, training, and background-check integrations.

**The table-stakes integrations** (present in virtually every competitor) are:
- EVV aggregator connectivity (HHAeXchange REST + Sandata REST, covering ~45 of 50 states)
- X12 EDI 837 claim generation via a clearinghouse (Change Healthcare / Optum or Availity)
- Payroll file export to ADP, Paychex, QuickBooks, and natively to Viventium

**The mid-tier differentiators** (present in 50–75% of competitors) are:
- Training/eLearning (Nevvon, CareAcademy, Relias)
- Background checks (Checkr)
- EHR interoperability (FHIR R4, CommonWell, Carequality)
- Earned wage access (DailyPay, Tapcheck, Viventium On-Demand)

**The genuine differentiation opportunity** for hcare is the family portal. No competitor prominently advertises family portal connectivity as a named integration category. The platform already has the feature; exposing it via a FHIR-compatible API would be industry-leading.

**Recommended open-source stack** for building the integration layer: Apache Camel (routing + HL7), HAPI FHIR (FHIR R4 client and server), StAEDI (X12 EDI parsing), and Temporal.io (durable workflow orchestration), with RabbitMQ as the internal event bus.

---

## 2. Competitor Integration Ecosystems

### 2.1 Integration Architecture Patterns by Vendor

| Vendor | Public API | EVV States | Clearinghouses | Payroll (native API) | Key Differentiator |
|---|---|---|---|---|---|
| HHAeXchange | REST + V5 Flat File (SFTP) | 40+ (is the aggregator) | Availity, Inovalon | ADP | Dual-role: platform + aggregator |
| WellSky Personal Care | FHIR R4 REST (`apidocs.clearcareonline.com`) | Multi-aggregator | Change Healthcare | — | TEFCA, CommonWell, DirectTrust |
| Sandata | REST JSON API | 20+ (is the aggregator) | — | — | Dual-role: platform + aggregator |
| Brightree (ResMed/MatrixCare) | Request-gated | Via CellTrak bridge | Change Healthcare | — | ResMed data integration |
| MatrixCare | FHIR R4, HL7 v2, CCD | Via partners | Inovalon | — | CommonWell, Carequality, 125+ partners |
| AlayaCare | REST (SwaggerHub), SQS events | 32+ states | — | Viventium (native) | Two-way SQS streaming, open docs |
| AxisCare | Open REST API | HHAeXchange, Sandata, AuthentiCare, CareBridge, Netsmart | — | Paylocity (native), Viventium | 30+ marketplace integrations |
| CareSmartz360 | Open API | 45+ states, 7 aggregators | Inovalon, Availity, Change Healthcare | Viventium, ADP, Paychex, Gusto | Broadest EVV aggregator coverage |
| Rosemark | Contact-gated | Sandata, HHAeXchange, Netsmart | — | Paychex, ADP (file export) | 35-year track record |
| HCHB | Enterprise-gated | 62 interfaces, 47 states (EVVLink) | — | Custom export | Surescripts/Carequality via CIE |
| KanTime | HL7/FHIR hub, 125+ partners | Sandata, Tellus, CareBridge, HHAeXchange | Change Healthcare, Inovalon, Availity, Office Ally | ADP, BambooHR | Internal PaaS payroll module |
| Axxess | REST (`engage.axxess.com/api.html`), FHIR R4 BulkFHIR certified | Sandata, HHAeXchange, Tellus | — | ADP | ONC-certified, Kno2/Carequality |

### 2.2 Partner Categories Present Across All Competitors

Ranked by prevalence:

1. **EVV aggregators** — 100% of competitors (table stakes for Medicaid business)
2. **Clearinghouse / claims** (Change Healthcare, Inovalon, Availity) — ~85%
3. **Payroll** (file export at minimum; native API for Viventium, ADP, Paylocity) — 100%
4. **Training/eLearning** (Nevvon, Relias, CareAcademy) — ~65%
5. **Background checks** (Checkr) — ~50%
6. **EHR interoperability** (CommonWell, Carequality, Surescripts) — ~45% (enterprise tier)
7. **Earned wage access** (DailyPay, Tapcheck) — ~30%
8. **Care coordination** (Forcura, CarePort) — ~30%
9. **Analytics** (SHP, Trella, Acclivity, Medalogix) — ~35%
10. **Family portal** — 0% of competitors as a named integration category

### 2.3 Developer Portal Availability

| Vendor | URL |
|---|---|
| HHAeXchange EDI | `knowledge.hhaexchange.com/edi` |
| HHAeXchange File Validation | `edi.hhaexchange.com` |
| WellSky Personal Care API | `apidocs.clearcareonline.com` |
| WellSky FHIR R4 | `wellsky.dynamicfhir.com/wellsky/basepractice/r4/Home/ApiDocumentation` |
| AlayaCare External Docs | `alayacare.github.io/external-integration-docs/` |
| AlayaCare AlayaMarket API | `github.com/AlayaCare/alayamarket-external-docs` |
| Axxess API Specs | `engage.axxess.com/api.html` |
| Axxess 2025 Real World Testing | `axxess.com/assets/pdf/Real-World-Testing-Report-2025.pdf` |
| Sandata Alt EVV (Indiana) | `in.gov/medicaid/providers/files/alternate-evv-specification-user-guide.pdf.pdf` |
| Sandata Alt EVV (DC) | `dhcf.dc.gov/sites/default/files/u23/OpenEVV-altEVV - v7.5 FINAL.pdf` |
| Sandata Alt EVV (NC) | `medicaid.ncdhhs.gov/documents/providers/.../openevv-altevv-v7-10-final/download` |

---

## 3. Clearinghouse & Billing Integrations

### 3.1 The Clearinghouse Landscape

| Clearinghouse | Connection Methods | REST API | Prevalence in Homecare |
|---|---|---|---|
| **Change Healthcare / Optum** | SFTP batch, REST, raw X12 API | Yes — `developer.optum.com` | Very high (largest network; suffered Feb 2024 cyberattack) |
| **Waystar** | SFTP batch, REST, SOAP | Yes — OAuth2 or HMAC-SHA256 | High (enterprise alternative to Optum) |
| **Availity** | SFTP batch, REST, portal | Yes — `developer.availity.com` (OAuth 2.0 PKCE) | Moderate-high (payer-owned; strongest for Humana, BCBS) |
| **TriZetto (Cognizant)** | SFTP batch, REST, SOAP | Yes — Interoperability API Gateway | Moderate (health system home health) |
| **Office Ally** | SFTP, portal | No public REST API | Very high — small agencies (free tier, zero cost) |
| **Claim.MD** | SFTP, REST | Yes | Low-moderate (developer-friendly) |
| **Stedi** | REST only (API-native) | Yes — `stedi.com/healthcare` (JSON-native, Change Healthcare API-compatible) | Low but growing (modern platforms) |

**For hcare's target segment (1–25 caregivers):** Office Ally (SFTP, zero cost) is the realistic entry point. Stedi is the best fit for a modern REST-native implementation — it accepts JSON, has the simplest developer onboarding, and is API-compatible with Change Healthcare, giving access to Optum's payer reach without legacy complexity.

### 3.2 X12 EDI Standards Reference

The following ANSI X12 5010 transactions are relevant to homecare:

| Transaction | Name | Use Case |
|---|---|---|
| **837I** | Institutional Claim | Primary billing format for Medicare-certified HHAs and Medicaid HHCS (maps to UB-04) |
| **837P** | Professional Claim | Individual practitioners, consumer-directed personal care billing under individual NPI (maps to CMS-1500) |
| **835** | Electronic Remittance Advice | Payer returns payment explanation after adjudication |
| **270/271** | Eligibility Inquiry / Response | Pre-visit coverage verification; check authorized hours, cost-sharing |
| **276/277** | Claim Status Inquiry / Response | Track claim adjudication status |
| **278** | Prior Authorization | Home health service authorization (low electronic adoption ~35%; Da Vinci PAS is the FHIR path) |
| **999** | Implementation Acknowledgment | Replaces legacy 997; confirms transaction-set syntax validity |
| **TA1** | Interchange Acknowledgment | Confirms envelope-level receipt; 106-char fixed-width ISA critical |

**Key 837I fields for homecare:**
- `NM1*85` — Billing Provider (agency NPI)
- `NM1*IL` — Subscriber/Patient
- `NM1*PR` — Payer
- `CLM` — Claim header (total charges, frequency code)
- Revenue codes (NUBC): e.g., `0571` for skilled nursing home visits
- `HI` — ICD-10 diagnosis codes
- `DTP*472` — Date of service (`CCYYMMDD`)
- Rendering provider NPI at service-line level = caregiver's NPI (required for EVV compliance linkage)

**835 remittance parsing:**
- `BPR02` — total payment amount
- `CLP01–04` — claim account, status, billed, paid
- `CAS` — adjustment reason codes: `CO` (contractual write-off, e.g., CO45), `PR` (patient responsibility)
- `TRN` — reassociation trace → match EFT deposit in bank account
- Equation: `Billed = Paid + Σ CAS Adjustments`

### 3.3 End-to-End Medicaid Claim Data Flow

```
Caregiver clocks out → EVV record created (6 federal elements captured)
         ↓
Agency platform validates EVV elements; attaches caregiver NPI
         ↓
Generates 837I batch (nightly or on-demand)
 · Rendering NPI = caregiver NPI
 · Billing NPI = agency NPI
 · Revenue codes + units + ICD-10 diagnoses
 · EVV data embedded per state companion guide
         ↓
SFTP → clearinghouse  –OR–  POST /claims via REST API
         ↓
Clearinghouse scrubs: TA1 (envelope) → 999 (transaction syntax) → 277CA (claim-level accept/reject)
         ↓
Clearinghouse routes accepted claims to payer (state MMIS, Medicaid MCO, or Medicare MAC)
         ↓
Payer adjudicates (checks authorization, EVV compliance, fee schedule)
         ↓
Payer returns 835 ERA to clearinghouse → EFT to agency bank account
         ↓
Platform polls SFTP outbound –OR– GET /remittances → auto-post payments
 · Match TRN trace number to EFT deposit
 · Parse CAS for write-offs vs patient responsibility
 · Route denials to denial management queue
```

### 3.4 Medicare-Specific: HETS for Eligibility

CMS's HIPAA Eligibility Transaction System (HETS) provides real-time 270/271 eligibility for Medicare. Key constraints:
- Real-time only — batch 270s not accepted
- Requires signed CMS Trading Partner Agreement + HETS submitter ID
- Connection: TCP/IP over CMS extranet, SOAP/WSDL, or HTTP/MIME over public internet
- Alternative: use any enrolled clearinghouse that has HETS access (simplest path for hcare)

**CMS documentation:** `cms.gov/data-research/cms-information-technology/hipaa-eligibility-transaction-system`

### 3.5 Prior Authorization: 278 EDI vs. Da Vinci PAS (FHIR)

Legacy 278 EDI has only ~35% electronic adoption as of 2024. The HL7 Da Vinci Prior Authorization Support (PAS) IG v2.1.0 defines the FHIR-native path:

- Homecare is a first-class use case: `Bundle/HomecareAuthorizationBundleExample` is in the published spec
- Maps X12 278 semantics to FHIR `Claim` + `ClaimResponse` resources
- Availity has already deployed FHIR-based end-to-end prior auth using Da Vinci PAS
- Companion IGs: Coverage Requirements Discovery (CRD) and Documentation Templates and Rules (DTR)
- CMS test kit available for both client (HHA) and server (payer) conformance testing

**FHIR ↔ X12 equivalence table:**

| X12 Transaction | FHIR Resource |
|---|---|
| 837 Claim | `Claim` |
| 835 Remittance | `ExplanationOfBenefit` |
| 270/271 Eligibility | `CoverageEligibilityRequest` / `CoverageEligibilityResponse` |
| 276/277 Claim Status | `Task` / `ClaimResponse` |
| 278 Prior Auth | `Claim` (PAS profile) / `ClaimResponse` |

---

## 4. EVV Aggregator Integrations

### 4.1 Regulatory Foundation

The 21st Century Cures Act (§12006) mandated EVV for:
- Medicaid personal care services (PCS): deadline January 1, 2020
- Home health care services (HHCS): deadline January 1, 2023

FMAP penalty for non-compliance: 0.25 pp (year 1) escalating to 1.0 pp (year 4+). CMS does not mandate a uniform data format — each state and aggregator defines its own schema.

**The Six Required EVV Data Elements:**

| # | Element |
|---|---|
| 1 | Type of service (procedure code) |
| 2 | Individual receiving service (member identity) |
| 3 | Date of service |
| 4 | Location of service (GPS or address) |
| 5 | Individual providing service (caregiver identity) |
| 6 | Start and end time |

### 4.2 The Five CMS EVV Implementation Models

| Model | Integration Requirement for hcare |
|---|---|
| Provider Choice | Must meet state certification; connect to designated state aggregator |
| Managed Care Plan (MCP) Choice | Multiple integration targets possible per payer mix |
| State-Mandated In-House | Direct API/portal to that state system only |
| State-Mandated External Vendor (Closed) | Closed — must use that vendor directly; no alternate path |
| Open Model | hcare captures visit data → transmits to designated state aggregator |

The **open model** is the dominant scenario for a homecare SaaS. Most states run open models where any compliant EVV system can submit to the designated aggregator.

### 4.3 Aggregator Technical Specifications

#### Sandata (OpenEVV / altEVV)

- **Market position:** ~20–25 states; the largest single aggregator
- **Protocol:** REST over HTTPS
- **Format:** JSON (case-sensitive field names)
- **Auth:** HTTP Basic Auth — Base64(`username:password`) in `Authorization` header per request
- **Batch size:** 1–5,000 records per POST
- **Entities:** Three independent objects — `Employee` (caregiver), `Client` (member), `Visit`
- **Key visit fields:** service type (HCPCS + up to 4 modifiers), member identifier (qualifier + ID pair), caregiver identifier, start/end time (UTC ISO 8601 `YYYY-MM-DDThh:mm`), location (GPS lat/lon or address), payer ID (Sandata-assigned)
- **State addenda:** Each state publishes a spec addendum with required/optional field overrides on top of the base OpenEVV spec
- **Vendor onboarding:** `evv-registration.sandata.com`

**States:** Arizona (pre-Oct 2025), California, Colorado, Connecticut, Delaware, DC, Hawaii, Idaho, Indiana, Maine, Massachusetts, Missouri, Montana, Nevada, North Carolina (FFS), North Dakota, Ohio, Pennsylvania (FFS), Rhode Island, Tennessee (agency-directed), Vermont, Wisconsin

**Published state specs:**
- Indiana: `in.gov/medicaid/providers/files/alternate-evv-specification-user-guide.pdf.pdf`
- Rhode Island: `eohhs.ri.gov/sites/g/files/xkgbur226/files/2024-02/RI Alt EVV Specifications 4.1.pdf`
- DC: `dhcf.dc.gov/sites/default/files/u23/OpenEVV-altEVV - v7.5 FINAL.pdf`
- North Carolina: `medicaid.ncdhhs.gov/.../openevv-altevv-v7-10-final/download`

#### HHAeXchange (EVV Aggregator Role)

- **Market position:** ~10–15 states as designated aggregator; also a full agency management platform
- **Protocol:** REST over HTTPS
- **Format:** JSON (`application/json`)
- **HTTP methods:** POST (create), PUT (update), DELETE (void), GET (status)
- **Batch size:** Maximum 100 EVV records per request
- **Auth:** Three credentials per call — `App Name` + `App Secret` + `App Key` (all registered with HHAeXchange in advance)
- **Rate limit:** 500 calls per method per provider per minute
- **Key fields:**

```
Required:
  providerTaxID, Office.qualifier/identifier
  Member.qualifier ("MedicaidID") / Member.identifier
  Caregiver.qualifier ("ExternalID") / Caregiver.identifier
  payerID (HHAeXchange-assigned)
  externalVisitID (30 chars, your system's visit ID)
  procedureCode (HCPCS), timezone
  scheduleStartTime / scheduleEndTime (UTC: YYYY-MM-DDThh:mm)

EVV clock-in/out (Evv.clockIn / Evv.clockOut):
  callDateTime (UTC), callType ("Telephony" | "Mobile" | "FOB")
  callLatitude / callLongitude (6 decimal places)
  serviceAddress (addressLine1, city, state, zipcode)
  originatingPhoneNumber (telephony only)

Returns: EVVMSID (unique state visit ID — retain for PUT/DELETE)
```

**States:** Alabama, Florida (FFS, Oct 2024+), Illinois, Michigan, Minnesota, Mississippi, New Jersey, Oklahoma (shared), Pennsylvania (MCO), Texas (MCO/TMHP), West Virginia

**Published state API docs (all at `knowledge.hhaexchange.com/edi/`):**
- Illinois, Minnesota, Michigan, Mississippi, Texas, Pennsylvania, West Virginia, Oklahoma, New Jersey

**Notable state rules:**
- Texas: Hard-edit enforcement (auto-denial) activated early 2026
- Illinois: Overnight visits crossing midnight must be split into two records at midnight (effective July 1, 2025)

#### AuthentiCare (Netsmart)

- **Protocol:** REST API or SFTP managed file transfer
- **Format:** JSON (REST) or SFTP flat file
- **Batch size:** 1–50 records per submission
- **Auth:** Provisioned credentials — requires pre-approval application before specs are released
- **Onboarding timeline:** 6–8 weeks from initial contact to production approval
- **Closed states:** Kansas, New Mexico, South Carolina — no alternate EVV path permitted

**States:** Arkansas (PCS), Kansas (closed), Nevada, New Hampshire, New Mexico (closed), Oklahoma (shared), South Carolina (closed)

#### Netsmart / Tellus EVV

- **Protocol:** SFTP (primary) or HTTPS (secondary)
- **Format:** Pipe-delimited (`|`) text files via SFTP; XML reject files returned
- **File naming:** `<SourceID>_<FileType>_<DateTime>_<ProviderEIN>_<ProviderNPI>.<ext>`
- **Batch limit:** 1,200 visits per file (XML 4MB limit)
- **Response:** Vendor must poll SFTP for XML reject files
- **HTTPS path:** Asynchronous, Basic Auth

**States:** Florida (some MCOs), Georgia, Kentucky, Montana, Nebraska, Virginia

**Implementation guide:** `mobilecaregiverplus.com/wp-content/uploads/2023/06/Netsmart-Alternate-EVV-Vendor-Implementation-Guide-MT-20230622.pdf`

#### CareBridge

- **Protocol:** SFTP only (no published REST API for third-party vendors)
- **Format:** Pipe-delimited CSV (comma-delimited files fail validation)
- **Auth:** SSH public key in OpenSSH format → `evvintegration@carebridgehealth.com`
- **Workflow:** Place file in SFTP → CareBridge processes → CareBridge places response in SFTP → vendor processes rejections

**States:** Iowa, North Carolina (Healthy Blue MCO), New York (one of several), Tennessee (PPL/self-directed), Wyoming

#### State-Built Systems (Bespoke Integration Required)

| State | System | Notes |
|---|---|---|
| Maryland | ISAS | Fully closed — no third-party tools accepted |
| Oregon | eXPRS Mobile-EVV | State-built, integrated into eXPRS billing |
| Arizona | AHCCCS In-House | Replaced Sandata, October 2025 |
| Washington | ProviderOne | State MMIS as hub |
| New York | eMedNY + HHAeXchange + CareBridge | Multi-aggregator by MCO plan |

### 4.4 Aggregator Comparison

| Aggregator | Format | Protocol | Auth | Batch Limit | Mode |
|---|---|---|---|---|---|
| Sandata | JSON | REST/HTTPS | Basic Auth (Base64) | 1–5,000 | Real-time or batch |
| HHAeXchange | JSON | REST/HTTPS | App Name + Secret + Key | 100 max | Real-time preferred |
| AuthentiCare | JSON or flat file | REST or SFTP | Provisioned creds | 1–50 | Either |
| Netsmart/Tellus | Pipe-delimited TXT | SFTP or HTTPS | Basic Auth (HTTPS) | 1,200 per file | Batch (SFTP primary) |
| CareBridge | Pipe-delimited CSV | SFTP only | SSH key | File-based | Batch only |

### 4.5 State-by-State Aggregator Reference

| State | Aggregator | Model |
|---|---|---|
| Alabama | HHAeXchange | Open |
| Alaska | Therap | Open |
| Arizona | AHCCCS In-House (post Oct 2025) | Open |
| Arkansas | AuthentiCare (PCS) / Sandata (HHCS) | Open, dual |
| California | Sandata (CalEVV) | Open |
| Colorado | Sandata | Open |
| Connecticut | Sandata | Hybrid |
| Delaware | Sandata | Open |
| DC | Sandata | Open |
| Florida | HHAeXchange (FFS) / Netsmart (some MCOs) | Open, payer-split |
| Georgia | Netsmart | Open |
| Hawaii | Sandata | Open |
| Idaho | Sandata | Provider Choice |
| Illinois | HHAeXchange | Open |
| Indiana | Sandata | Open |
| Iowa | CareBridge | Open |
| Kansas | AuthentiCare | Closed (mandated) |
| Kentucky | Therap | Open |
| Louisiana | LaSRS (state system) | Open |
| Maine | Sandata | Open |
| Maryland | ISAS (state-built) | Closed (fully closed) |
| Massachusetts | Sandata | Open |
| Michigan | HHAeXchange | Open |
| Minnesota | HHAeXchange | Open |
| Mississippi | HHAeXchange | Open |
| Missouri | Sandata | Open |
| Montana | Netsmart | Open |
| Nebraska | Netsmart | Open |
| Nevada | Sandata | Open |
| New Hampshire | AuthentiCare | Open |
| New Jersey | HHAeXchange | Open |
| New Mexico | AuthentiCare | Closed (mandated) |
| New York | eMedNY / HHAeXchange / CareBridge (by MCO) | Provider Choice, multi-aggregator |
| North Carolina | Sandata (FFS) / HHAeXchange (MCOs) / CareBridge (Healthy Blue) | Open, multi-aggregator |
| North Dakota | Sandata | Open |
| Ohio | Sandata | Open |
| Oklahoma | AuthentiCare / HHAeXchange (by service type) | Open |
| Oregon | eXPRS (state-built) | Provider Choice |
| Pennsylvania | Sandata (FFS) / HHAeXchange (MCOs) | Open |
| Rhode Island | Sandata | Open |
| South Carolina | AuthentiCare | Closed (mandated) |
| South Dakota | Therap | Open |
| Tennessee | Sandata (agency) / PPL (self-directed) | Open |
| Texas | HHAeXchange (MCOs/TMHP) | Hybrid |
| Utah | DHHS In-House | Provider Choice |
| Vermont | Sandata | Open |
| Virginia | Approved vendor list | Provider Choice |
| Washington | ProviderOne | Provider Choice |
| West Virginia | HHAeXchange | Open |
| Wisconsin | Sandata | Open |
| Wyoming | CareBridge | Open |

### 4.6 Routing Logic for Multi-Aggregator States

North Carolina, Florida, Pennsylvania, New York, and Oklahoma have multiple active aggregators simultaneously. The correct submission target depends on the visit's payer (FFS vs. specific MCO). Routing to the wrong aggregator results in rejection and potential claim denial. This logic must live in hcare's `EvvStateConfig` table — routing rules per `(state, payerId)` tuple, not just per state.

---

## 5. EHR / Hospital Referral Integrations

### 5.1 Three Coexisting Standards

**1. HL7 v2 ADT Messages (legacy, highest message volume)**
- Trigger: `ADT A03` (discharge), `ADT A08` (patient update) from hospital EMRs
- Transport: MLLP (Minimal Lower Layer Protocol) — TCP socket wrapper, not HTTP
- Still ~50%+ of message volume because Epic and Cerner have been sending HL7 v2 for 20+ years
- Reception requires an MLLP listener (Apache Camel `camel-hl7` covers this natively)

**2. C-CDA Documents (structured XML, HL7 CDA R2.1)**
- Key document types: Discharge Summary (templateId `2.16.840.1.113883.10.20.22.1.8`), Referral Note
- Carries: ICD-10 diagnoses, medications, allergies, care instructions
- Transmission paths: Direct Secure Messaging (encrypted SMTP), HIE query/retrieve (IHE XDS.b), FHIR `DocumentReference` wrapper

**3. FHIR R4 (emerging, now regulatory-mandated)**
- CMS Post-Acute FHIR Orders IG defines: `ServiceRequest` for home health orders, `DeviceRequest` for DME, `MedicationRequest` for associated medications
- RESTful push model: ordering provider creates FHIR `ServiceRequest` → posts to PAO-compliant endpoint → hcare receives and acknowledges
- PACIO Project FHIR IGs define functional status and care plan exchange for post-acute settings — directly applicable to care plan import from discharging hospitals

### 5.2 Epic and Cerner Integration

**Epic FHIR R4 (`fhir.epic.com`):**
- US Core R4 profiles; SMART on FHIR for in-workflow apps
- Backend service (non-patient-facing): register app → JWT-authenticated backend token → query `Patient`, `Encounter`, `Condition`, `MedicationRequest`, `CarePlan`

**Cerner / Oracle Health:**
- R4-only endpoints (dropped DSTU2 late 2025)
- Same OAuth 2.0 backend credentials pattern as Epic

**TEFCA (Trusted Exchange Framework):** Both Epic and Cerner participate. FHIR queries through a TEFCA-connected QHIN can reach either system without bilateral agreements — significantly lowers the barrier for smaller homecare platforms.

### 5.3 Interoperability Networks

| Network | Who Participates | How It Works |
|---|---|---|
| **CommonWell Health Alliance** | WellSky (13,000 post-acute providers), MatrixCare, many hospitals | Patient record sharing across 22,000+ providers |
| **Carequality** | MatrixCare, WellSky (via CommonWell bridge), HCHB (via Surescripts), Axxess (via Kno2), 600,000+ providers | Standards-based document exchange (IHE XDS.b + FHIR) |
| **Surescripts** | MatrixCare, HCHB, KanTime | Medication list reconciliation, C-CDA reconciliation |
| **TEFCA** | WellSky, Axxess, most major EHRs | Nationwide FHIR exchange via QHINs |
| **DirectTrust** | WellSky I/O | Secure encrypted messaging between providers |
| **Kno2** | Axxess | Single connection to multiple interoperability networks |

### 5.4 Da Vinci Project Relevance

- **PAS (Prior Authorization Support):** FHIR-based prior auth — replaces X12 278. Homecare is explicitly included (`Bundle/HomecareAuthorizationBundleExample`)
- **CRD (Coverage Requirements Discovery):** Real-time payer rules in the ordering workflow
- **DTR (Documentation Templates and Rules):** Automated documentation collection for prior auth

---

## 6. Payroll & HR Integrations

### 6.1 The Two Integration Tiers

**Tier 1 — Native API Integration (higher value, more effort):**

| Vendor | Why It Matters | Technical Mechanism |
|---|---|---|
| **Viventium** | Purpose-built for homecare, hospice, SNF; handles 8/80 overtime, blended dual-client rates, mileage, caregiver cost-center splits | REST API: `POST /v1/time-attendance/import/companies/{companyCode}/divisions/{divisionCode}/payrolls/{checkDate}/{runNumber}/pay-batches/{batchCode}`; cookie-based auth; XML/JSON payloads. Employee allocation and division payroll endpoints separate. Docs: `hcm.viventium.com/API/Integration/help` |
| **QuickBooks Online** | Highest prevalence among small agencies | Intuit OAuth 2.0; bidirectional sync via official API; payroll journal entries + payables posted automatically |
| **ADP Workforce Now** | Required by larger agencies | ADP Marketplace partner program; time and attendance data pushed as pay-batch records |
| **Paylocity** | Growing in mid-market homecare | REST API; AxisCare offers the first fully native Paylocity integration in the sector |
| **Gusto** | Smaller agencies | REST API; employee records, pay rates, payroll runs synchronized |

**Tier 2 — Preformatted CSV/File Export (universal fallback):**
- Every competitor supports this for long-tail payroll vendors
- Fields specific to homecare: visit-level mileage reimbursement, travel time between clients, FLSA 8/80 overtime, blended rates for dual-client visits, caregiver-client cost center splits

### 6.2 HR Platform Integrations

- **BambooHR:** REST API (JSON, API key auth). Employee records, time-off, credential expiry. KanTime has a named BambooHR partner. Typically built via n8n/Zapier middleware rather than native integration
- **Workday:** SOAP + REST (RAAS reporting). Only relevant when selling upmarket to health systems
- **Google Calendar / Outlook:** No native homecare platform offers bidirectional sync. Standard: iCal (`.ics`) feed for caregivers' personal calendars — universally importable, no API agreement required

---

## 7. Other Integration Categories

### 7.1 Training / eLearning

| Vendor | Integration Depth |
|---|---|
| **Nevvon** | Named by HHAeXchange and AlayaCare; bidirectional caregiver sync + compliance reporting |
| **CareAcademy** | Named by AxisCare; DCWs access training via AxisCare mobile app |
| **Relias** | Named by AlayaCare; bidirectional caregiver sync |
| **Showd.me** | Named by HHAeXchange |

### 7.2 Background Checks

- **Checkr** — named by WellSky Personal Care, CareSmartz360, AxisCare. REST API, OAuth 2.0. Webhook callbacks on completion.

### 7.3 Earned Wage Access

- **DailyPay** — named by HHAeXchange
- **Tapcheck** — named by WellSky, CareSmartz360
- **Keeper** — named by HHAeXchange
- **Viventium On-Demand Pay** — part of Viventium package

### 7.4 Care Coordination / Document Workflow

- **Forcura** — named by AlayaCare, Axxess, KanTime. Workflow automation for documentation, scheduling, billing — focused on post-acute referral paperwork
- **CarePort** (powered by WellSky) — care coordination across settings; named by WellSky, Axxess
- **Kno2** — Axxess's single connection to Carequality, DirectTrust, and other networks

### 7.5 Analytics

- **SHP (Strategic Healthcare Programs)** — named by Brightree, Axxess, MatrixCare. Outcomes benchmarking.
- **Trella Health** — named by Axxess. Referral source analytics.
- **Acclivity Health** — named by MatrixCare, KanTime. Predictive clinical analytics.
- **Medalogix** — named by MatrixCare. Episode management + high-risk patient identification.
- **Inovalon** — named by MatrixCare, CareSmartz360, KanTime. Claims + RCM analytics.

### 7.6 AI / Automation

- **Element5** — named by AlayaCare and HCHB. Agentic workflow automation and RPA operating across the homecare platform and connected systems
- **Tennr** — named by KanTime. AI-powered document intake and post-acute operations

---

## 8. Integration Architecture for hcare

### 8.1 Recommended Architecture: Strategy + Adapter Pattern

```
IntegrationConnector (interface)
├── EvvAggregatorConnector
│   ├── SandataConnector           REST, JSON, Basic Auth, 1–5000 batch
│   ├── HhaExchangeConnector       REST, JSON, App triple-key, 100 batch
│   ├── AuthentiCareConnector      REST or SFTP, 1–50 batch
│   ├── NetsmarTellusConnector     SFTP pipe-delimited, XML rejects
│   └── CareBridgeConnector        SFTP CSV, SSH key
│
├── ClearinghouseConnector
│   ├── StediConnector             REST, JSON-native, API key
│   ├── OfficeAllyConnector        SFTP, raw X12 837
│   └── WaystarConnector           REST/SFTP, OAuth2
│
├── PayrollConnector
│   ├── ViventiumConnector         REST, XML/JSON payloads
│   ├── QuickBooksConnector        OAuth2 REST, bidirectional
│   ├── AdpConnector               Marketplace API, pay-batch records
│   └── CsvExportConnector         Configurable field map — universal fallback
│
└── EhrConnector
    ├── FhirR4Connector            HAPI FHIR client, US Core R4
    ├── Hl7V2MllpConnector         Apache Camel camel-hl7 MLLP listener
    └── CCdaDocumentConnector      C-CDA Discharge Summary parse/generate
```

Each connector is a Spring-managed bean. The correct connector is selected at runtime via a `ConnectorFactory` that reads `AgencyIntegrationConfig` by `agencyId`. Credentials are AES-256 encrypted at rest.

### 8.2 Tenant-Scoped Integration Configuration

```sql
-- New entity (aligns with existing agencyId multi-tenancy)
CREATE TABLE agency_integration_config (
    id UUID PRIMARY KEY,
    agency_id UUID NOT NULL REFERENCES agency(id),
    integration_type VARCHAR(50) NOT NULL,  -- EVV_AGGREGATOR, CLEARINGHOUSE, PAYROLL, EHR
    connector_class VARCHAR(100) NOT NULL,  -- e.g. SandataConnector
    state_code CHAR(2),                     -- null = applies to all states
    payer_id VARCHAR(50),                   -- for multi-aggregator state routing
    endpoint_url VARCHAR(500),
    credentials_encrypted TEXT,             -- AES-256, KMS key
    config_json JSONB,                      -- connector-specific field mappings
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

Multi-aggregator states (NC, FL, PA, NY, OK) need `(state_code, payer_id)` routing — a single state entry is insufficient.

### 8.3 EVV Submission Pipeline

The existing `EVVRecord` entity captures all 6 required federal elements. The outbound pipeline:

```
ShiftCompletedEvent (already published by scoring module pattern)
        ↓
EvvSubmissionService.onShiftCompleted()
        ↓
Lookup AgencyIntegrationConfig for (agencyId, state, payerId)
        ↓
ConnectorFactory.getEvvConnector(config)
        ↓
connector.submit(evvRecord)  → returns aggregatorVisitId (EVVMSID / Sandata ref)
        ↓
Store aggregatorVisitId on EVVRecord for void/update workflows
        ↓
On failure: publish to RabbitMQ dead-letter queue → retry with exponential backoff
```

**Real-time vs. batch per aggregator:**
- Sandata + HHAeXchange: submit real-time on `ShiftCompletedEvent`
- Netsmart/Tellus + CareBridge: accumulate and submit nightly SFTP batch

### 8.4 Claims Pipeline

```
AuthorizationUtilizationService (already tracks authorized hours)
        ↓
BillingService.generateClaims(agencyId, billingPeriod)
  · Selects completed, EVV-verified shifts with no prior claim
  · Constructs 837I per shift (caregiver NPI = rendering, agency NPI = billing)
  · Embeds revenue code, ICD-10, units
        ↓
ClearinghouseConnector.submitClaims(x12Batch or JsonPayload)
  → StediConnector (REST, JSON) preferred for greenfield
  → OfficeAllyConnector (SFTP) for agencies using free-tier clearinghouse
        ↓
Poll for 999 / 277CA → update claim status
Poll for 835 → auto-post payments, route denials
```

### 8.5 Inbound EHR Referral Pipeline

Deploy as a dedicated `integration-service` Spring Boot module (separate from `/backend` and `/bff`):

```
MLLP port (HL7 v2 ADT) → Apache Camel camel-hl7 → parse ADT A03
         OR
FHIR R4 REST endpoint (HAPI FHIR JPA) → receive ServiceRequest (referral)
         ↓
Integration service translates to internal domain event
         ↓
POST to Core API /api/v1/clients/intake (preserving API-first principle)
         ↓
Core API creates Client + CarePlan, notifies scheduler
```

### 8.6 Webhook vs. Polling Decision Framework

| Scenario | Recommended Pattern |
|---|---|
| ADP/QBO payroll events | Webhook (both support webhooks) |
| FHIR EHR updates | FHIR Subscription resource (push) |
| Clearinghouse 277CA / 835 | Polling (SFTP) or GET /remittances (REST) |
| EVV aggregator reject responses | Netsmart/CareBridge: poll SFTP; Sandata/HHAeXchange: synchronous HTTP error |
| Background check completion | Checkr webhook callback |

For systems without webhook support: synthetic webhook pattern — internal scheduler polls on configurable interval, emits internal domain events. Store idempotency key (MD5 of content + timestamp) in `integration_processed_events` to prevent duplicates.

### 8.7 Integration Deployment Topology

```
┌─────────────────────────────────────────────────────────────┐
│                    hcare platform                           │
│                                                             │
│  ┌──────────┐   ┌──────────┐   ┌─────────────────────┐     │
│  │ /backend │   │   /bff   │   │ /integration-service│     │
│  │(Core API)│   │(Mobile)  │   │  Apache Camel       │     │
│  │          │   │          │   │  HAPI FHIR JPA      │     │
│  │  Spring  │   │  Spring  │   │  StAEDI (X12)       │     │
│  │  Boot    │   │  Boot    │   │  Temporal.io        │     │
│  └────┬─────┘   └──────────┘   └──────────┬──────────┘     │
│       │              RabbitMQ              │                 │
│       └──────────────────────────────────-┘                 │
└─────────────────────────────────────────────────────────────┘
          │                          │
    ┌─────┴──────┐            ┌──────┴──────┐
    │ EVV        │            │ EHR /       │
    │ Aggregators│            │ Hospitals   │
    │ (Sandata,  │            │ (Epic,      │
    │  HHAX,     │            │  Cerner,    │
    │  Netsmart, │            │  HL7 v2)    │
    │  Carebridge│            └─────────────┘
    └────────────┘
          │
    ┌─────┴──────┐
    │ Clearinghouses│
    │ (Stedi,    │
    │  Office Ally│
    │  Waystar)  │
    └────────────┘
```

---

## 9. Open-Source Tool Survey

### 9.1 Integration Routing — Apache Camel

**Recommendation: First choice for hcare.** Java-native, embeds in Spring Boot, has healthcare-specific components.

| Component | Purpose |
|---|---|
| `camel-hl7` | MLLP codec + HAPI HL7 v2 parsing (`HL7MLLPNettyDecoder/Encoder`) |
| `camel-fhir` | Wraps HAPI FHIR client; FHIR R4 CRUD + search + `$operations` |
| `camel-sftp` | SFTP file exchange for EDI batches and EVV SFTP aggregators |
| `camel-http` | REST calls to payroll APIs, clearinghouse REST APIs |
| `camel-kafka` / `camel-rabbitmq` | Internal event streaming |
| `camel-spring-boot` | Auto-configuration; routes as Spring beans or Java DSL |

License: Apache 2.0. Weakness: EDI X12 support requires StAEDI (separate library) — combine them.

### 9.2 FHIR R4 — HAPI FHIR

Two usage modes:

**Client** (`ca.uhn.hapi.fhir:hapi-fhir-client`): consume Epic, Cerner, payer FHIR APIs. Use for EHR referral intake and Da Vinci PAS prior auth.

**JPA Server** (`hapi-fhir-jpaserver-starter`): complete FHIR R4 server backed by PostgreSQL. Use to expose hcare data as a FHIR endpoint — enables family portal FHIR access, TEFCA participation, SMART on FHIR apps. Handles resource storage, search parameters, terminology validation, `$everything` operation.

License: Apache 2.0.

### 9.3 X12 EDI Parsing — StAEDI

**Recommendation: First choice for X12 parsing.**

- Streaming "pull" API modeled after Java StAX — memory-efficient for large 837 files
- Supports X12 and EDIFACT
- Schema-based HIPAA validation (plug in HIPAA schemas)
- Pure Java, no external dependencies
- Apache 2.0 license
- GitHub: `xlate/staedi`

**Alternatives:**
- `BerryWorksSoftware/edireader` — GPL3 (Community Edition); production-hardened since 2004; SAX + DOM style parsing
- `imsweb/x12-parser` — Apache 2.0; simpler API; less validation capability; lightweight option

### 9.4 HL7 Integration Engine — Open Integration Engine (Mirth Fork)

Mirth Connect 4.6 was made proprietary by NextGen. Two community forks exist:
- `OpenIntegrationEngine/engine`
- `SagaHealthcareIT/open-integration-engine`

License: MPL 2.0. Best deployed as a **sidecar service** (not embedded in Spring Boot) for handling messy HL7 v2 MLLP ingestion from hospital systems. Architecture: channel-based (source → filter → transformer → destination). Supported formats: HL7 v2.x, HL7 v3/CDA, FHIR, DICOM, X12 EDI, JSON, XML.

Alternatively, Apache Camel `camel-hl7` handles the same use case inline without a separate runtime.

### 9.5 Workflow Orchestration — Temporal.io

**Not an integration platform — a durable execution engine.** Ideal for orchestrating multi-step integration workflows:

```
Receive referral → validate → create intake → notify scheduler →
await authorization → activate care plan → trigger EVV setup
```

Handles retries, timeouts, and compensating transactions automatically. Java SDK: `io.temporal:temporal-sdk`. License: MIT.

### 9.6 Internal Event Bus — RabbitMQ

At 1–25 caregiver agency scale, RabbitMQ is the appropriate choice over Kafka:
- Simpler operations
- AMQP semantics with message acknowledgment and dead-letter queues
- Native Spring AMQP support (`spring-boot-starter-amqp`)
- Dead-letter queues for EVV submission failures and claim submission retries
- Kafka only becomes relevant when aggregating data across hundreds of agencies for analytics

The `@TransactionalEventListener` pattern already used in the scoring module (per CLAUDE.md) is the right boundary — emit domain events within the transaction, dispatch to RabbitMQ for external delivery with retry.

### 9.7 Other Integration Infrastructure

| Need | Tool | License | Notes |
|---|---|---|---|
| HR/calendar automation | n8n (self-hosted) | Fair-code | BambooHR ↔ Google Calendar, Checkr → BambooHR |
| iPaaS (full platform) | WSO2 Integration Platform | Apache 2.0 | Better as standalone service than embedded; heavier footprint |
| Commercial iPaaS | MuleSoft / Anypoint | Commercial | ~$50K/yr; only relevant for health system upmarket |

### 9.8 Tool Selection Summary

| Need | Recommended Tool | License |
|---|---|---|
| HL7 v2 MLLP ingestion | Apache Camel `camel-hl7` | Apache 2.0 |
| FHIR R4 client (consume EHRs) | HAPI FHIR Client | Apache 2.0 |
| FHIR R4 server facade | HAPI FHIR JPA Server | Apache 2.0 |
| X12 EDI parsing (837/835) | StAEDI | Apache 2.0 |
| HL7 interface engine (sidecar) | Open Integration Engine (Mirth fork) | MPL 2.0 |
| Workflow orchestration | Temporal.io | MIT |
| Integration routing (Java) | Apache Camel | Apache 2.0 |
| Internal event bus | RabbitMQ via Spring AMQP | Apache 2.0 |
| HR/calendar automation | n8n (self-hosted) | Fair-code |

---

## 10. Strategic Priorities

### Priority 1 — EVV Aggregator Connectivity (Compliance-Critical)

Without this, the platform cannot serve Medicaid-funded agencies in most states.

**Build order:**
1. **Sandata/OpenEVV module** — REST/JSON, Basic Auth, base spec + state addenda. Covers ~20 states immediately.
2. **HHAeXchange aggregator module** — REST/JSON, App triple-key auth. Covers ~15 more states.
3. **Netsmart/Tellus module** — SFTP pipe-delimited, XML reject file parsing. ~5 states.
4. **CareBridge module** — SFTP CSV, SSH key. ~4 states.
5. **AuthentiCare module** — requires 6–8 week pre-approval process; start early.

The existing `EvvStateConfig` table design is correct — extend it to support `(state, payerId)` routing for multi-aggregator states.

### Priority 2 — Claims/Billing (Revenue-Critical)

**Build order:**
1. **837I generator** using StAEDI — produces HIPAA 5010 institutional claim files. The EVV `EVVRecord` data feeds directly into the claim (caregiver NPI → rendering provider field).
2. **Stedi connector** (REST, JSON-native) as the primary clearinghouse — simplest developer onboarding, Change Healthcare API-compatible, 3,400+ payers.
3. **Office Ally SFTP connector** as fallback for agencies that want zero-cost clearinghouse.
4. **835 remittance parser** using StAEDI — auto-post payments, route denials.
5. **270/271 eligibility** via clearinghouse REST API — automate at visit creation time.

### Priority 3 — Payroll Export

**Build order:**
1. **Viventium REST API connector** — homecare-native, documented API, first-mover advantage in the small agency segment.
2. **QuickBooks Online OAuth2 connector** — highest prevalence in target market.
3. **Configurable CSV export** — universal fallback covering ADP, Paychex, Gusto, and all long-tail vendors. This is what every competitor offers and eliminates one-off requests.
4. ADP native API only if enterprise agencies specifically request it.

### Priority 4 — EHR Inbound Referral (Differentiator)

**Build order:**
1. **HAPI FHIR R4 client** — consume Epic/Cerner discharge summaries and ServiceRequest resources. Enables referral intake from hospital systems.
2. **Apache Camel MLLP listener** — receive HL7 v2 ADT A03 (discharge) messages from hospitals that haven't yet moved to FHIR. Still >50% of hospital-to-homecare referral volume.
3. **C-CDA Discharge Summary parser** — extract care plan data (diagnoses, medications) from the structured XML.
4. **Da Vinci PAS** FHIR prior auth — reduces authorization lag, directly improves cash flow. Medium-term priority as payer adoption grows.

### Priority 5 — Family Portal FHIR Exposure (Strategic Differentiator)

The hcare family portal exists and no competitor lists it as a named integration. Expose it via a HAPI FHIR JPA Server endpoint:
- Register with a TEFCA-connected QHIN
- Families can access visit data through standard FHIR Patient Access APIs mandated by the 21st Century Cures Act
- No MyChart-specific integration needed — standard patient-access flows reach any FHIR-registered provider

This is the single integration initiative with the highest competitive differentiation potential relative to effort.

---

## Key Reference Documentation

### EVV Aggregator APIs
- HHAeXchange EVV Aggregator Docs (all states): `knowledge.hhaexchange.com/edi/Content/Documentation/EDI/API.htm`
- Indiana Sandata Alternate EVV Spec: `in.gov/medicaid/providers/files/alternate-evv-specification-user-guide.pdf.pdf`
- Rhode Island Alt EVV Spec v4.1: `eohhs.ri.gov/sites/g/files/xkgbur226/files/2024-02/RI Alt EVV Specifications 4.1.pdf`
- CareBridge Integration FAQs: `support.carebridgehealth.com/hc/en-us/articles/1500000864922`
- Netsmart Alt EVV Vendor Guide (MT): `mobilecaregiverplus.com/wp-content/uploads/2023/06/Netsmart-Alternate-EVV-Vendor-Implementation-Guide-MT-20230622.pdf`
- New York eMedNY EVV: `emedny.org/evv/`
- HHAeXchange State EVV Status Map: `hhaexchange.com/state-evv-status`

### Clearinghouse APIs
- Optum Developer Portal: `developer.optum.com`
- Optum Professional Claims V3: `developer.optum.com/eligibilityandclaims/docs/professional-claims-v3-getting-started`
- Availity HIPAA Transaction APIs: `developer.availity.com/blog/2025/3/25/hipaa-transactions`
- Availity API Guide: `developer.availity.com/partner/api_guide`
- Stedi Healthcare APIs: `stedi.com/healthcare`
- Stedi 837 Reference: `stedi.com/edi/x12/transaction-set/837`
- CMS HETS (Medicare Eligibility): `cms.gov/data-research/cms-information-technology/hipaa-eligibility-transaction-system`

### Standards & Regulations
- Da Vinci PAS FHIR IG v2.1.0: `hl7.org/fhir/us/davinci-pas/`
- Da Vinci PAS Homecare Example: `build.fhir.org/ig/HL7/davinci-pas/Bundle-HomecareAuthorizationUpdateBundleExample.html`
- CMS Post-Acute FHIR Orders: `cms.gov/data-research/computer-data-systems/emdi/post-acute-fhir-order-and-referrals/`
- PACIO Project: `pacioproject.org`
- Epic FHIR Sandbox: `fhir.epic.com`

### Competitor Developer Portals
- HHAeXchange EDI: `knowledge.hhaexchange.com/edi`
- WellSky Personal Care API: `apidocs.clearcareonline.com`
- WellSky FHIR R4: `wellsky.dynamicfhir.com/wellsky/basepractice/r4/Home/ApiDocumentation`
- AlayaCare External Docs: `alayacare.github.io/external-integration-docs/`
- AlayaCare AlayaMarket API: `github.com/AlayaCare/alayamarket-external-docs`
- Axxess API Specs: `engage.axxess.com/api.html`
- Viventium Integration API: `hcm.viventium.com/API/Integration/help`

### Open-Source Libraries
- Apache Camel HL7: `camel.apache.org/components/4.18.x/dataformats/hl7-dataformat.html`
- HAPI FHIR: `hapifhir.io`
- StAEDI (X12 Java parser): `github.com/xlate/staedi`
- EDIReader: `github.com/BerryWorksSoftware/edireader`
- Open Integration Engine (Mirth fork): `github.com/OpenIntegrationEngine/engine`
- Temporal.io Java SDK: `docs.temporal.io`
