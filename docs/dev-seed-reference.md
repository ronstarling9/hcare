# Dev Seed Reference

Seeded on every `dev` profile startup by `DevDataSeeder`. Skips if data already exists.

All accounts use password: `Admin1234!`

## Agency 1: Sunrise Home Care (TX)

| | |
|--|--|
| **Admin** | admin@sunrise.dev |
| **Scheduler** | scheduler@sunrise.dev |
| **AI scheduling** | off |

**Payers:** Texas Medicaid, Medicare Advantage TX, Bright Futures Private Pay

**Clients (Baby Boomers):**
| Name | DOB |
|------|-----|
| Dorothy Henderson | 1945-03-14 |
| Harold Mitchell | 1942-11-08 |
| Patricia Gallagher | 1953-07-22 |
| Gerald Kowalski | 1948-04-30 |
| Betty Thornton | 1938-12-01 |

**Caregivers (Millennials):**
| Name | Email |
|------|-------|
| Ashley Rodriguez | ashley.rodriguez@hcare.dev |
| Tyler Brooks | tyler.brooks@hcare.dev |
| Jessica Nguyen | jessica.nguyen@hcare.dev |
| Brandon Washington | brandon.washington@hcare.dev |
| Kayla Martinez | kayla.martinez@hcare.dev |

---

## Agency 2: Golden Years Care (FL)

| | |
|--|--|
| **Admin** | admin@goldenyears.dev |
| **Scheduler** | scheduler@goldenyears.dev |
| **AI scheduling** | off |

**Payers:** Florida Medicaid, Medicare Plan FL, Prestige Private Pay, Veterans Affairs FL

**Clients (Baby Boomers):**
| Name | DOB |
|------|-----|
| Walter Freeman | 1944-06-15 |
| Barbara Novak | 1950-02-28 |
| Dennis Crawford | 1956-09-18 |
| Sandra Okafor | 1947-01-05 |
| Roger Kimball | 1941-08-22 |

**Caregivers (Millennials):**
| Name | Email |
|------|-------|
| Megan Torres | megan.torres@hcare.dev |
| Jordan Taylor | jordan.taylor@hcare.dev |
| Samantha Patel | samantha.patel@hcare.dev |
| Austin Williams | austin.williams@hcare.dev |
| Lauren Chen | lauren.chen@hcare.dev |

---

## Agency 3: Harmony Home Health (CA)

| | |
|--|--|
| **Admin** | admin@harmony.dev |
| **Scheduler** | scheduler@harmony.dev |
| **AI scheduling** | **on** |

**Payers:** Medi-Cal, SeniorCare LTC, Pacific Private Pay

**Clients (Baby Boomers):**
| Name | DOB |
|------|-----|
| Carol Stevenson | 1952-05-10 |
| Kenneth Yamamoto | 1949-12-07 |
| Donna Burke | 1957-08-25 |
| Frank Delgado | 1945-03-19 |
| Judith Schreiber | 1960-11-14 |

**Caregivers (Millennials):**
| Name | Email |
|------|-------|
| Alyssa Ramirez | alyssa.ramirez@hcare.dev |
| Darius Peterson | darius.peterson@hcare.dev |
| Brittany Kim | brittany.kim@hcare.dev |
| Carlos Rivera | carlos.rivera@hcare.dev |
| Kaitlyn O'Brien | kaitlyn.obrien@hcare.dev |

---

## Dashboard Alerts Pre-wired

| Alert type | Trigger |
|------------|---------|
| `CREDENTIAL_EXPIRING` | First caregiver at each agency — CPR expiring in 20 days |
| `BACKGROUND_CHECK_DUE` | Third caregiver at each agency — FBI check due in 25 days |
| `AUTHORIZATION_LOW` | First Medicaid client at each agency — 85% authorization utilization (170 / 200 hrs) |

## Shifts Seeded (per agency, first Medicaid client)

| Shifts | Status | EVV |
|--------|--------|-----|
| Past 7 days (9:00–13:00) | COMPLETED | GPS EVV record |
| Today 8:00–12:00 | COMPLETED | GPS EVV record |
| Today 13:00–17:00 | IN_PROGRESS | — |
| Today 18:00–22:00 | OPEN (unassigned) | — |
| Next 3 weekdays (9:00–13:00) | ASSIGNED | — |
