// ── Enums ────────────────────────────────────────────────────────────────────

export type ShiftStatus =
  | 'OPEN'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'MISSED'

export type EvvComplianceStatus =
  | 'GREY'
  | 'EXEMPT'
  | 'GREEN'
  | 'YELLOW'
  | 'PORTAL_SUBMIT'
  | 'RED'

export type VerificationMethod =
  | 'GPS'
  | 'TELEPHONY_LANDLINE'
  | 'TELEPHONY_CELL'
  | 'FIXED_DEVICE'
  | 'FOB'
  | 'BIOMETRIC'
  | 'MANUAL'

export type ClientStatus = 'ACTIVE' | 'INACTIVE' | 'DISCHARGED'
export type CaregiverStatus = 'ACTIVE' | 'INACTIVE' | 'TERMINATED'
export type UserRole = 'ADMIN' | 'SCHEDULER'
export type PayerType =
  | 'MEDICAID'
  | 'PRIVATE_PAY'
  | 'LTC_INSURANCE'
  | 'VA'
  | 'MEDICARE'

export type DashboardAlertType =
  | 'CREDENTIAL_EXPIRY'
  | 'AUTHORIZATION_LOW'
  | 'BACKGROUND_CHECK_DUE'

export type DashboardAlertResourceType = 'CAREGIVER' | 'CLIENT'

// ── Auth ─────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  userId: string
  agencyId: string
  role: UserRole
}

// ── Spring Page wrapper ───────────────────────────────────────────────────────

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

// ── Shifts ────────────────────────────────────────────────────────────────────

export interface ShiftSummaryResponse {
  id: string
  agencyId: string
  clientId: string
  caregiverId: string | null
  serviceTypeId: string
  authorizationId: string | null
  sourcePatternId: string | null
  scheduledStart: string   // ISO-8601 LocalDateTime — no timezone
  scheduledEnd: string
  status: ShiftStatus
  notes: string | null
}

export interface EvvSummary {
  evvRecordId: string
  complianceStatus: EvvComplianceStatus
  timeIn: string | null
  timeOut: string | null
  verificationMethod: VerificationMethod | null
  capturedOffline: boolean
}

export interface ShiftDetailResponse extends ShiftSummaryResponse {
  evv: EvvSummary | null
}

export interface RankedCaregiverResponse {
  caregiverId: string
  score: number
  explanation: string
}

export interface CreateShiftRequest {
  clientId: string
  caregiverId?: string
  serviceTypeId: string
  authorizationId?: string
  scheduledStart: string
  scheduledEnd: string
  notes?: string
}

export interface UpdateShiftRequest {
  clientId?: string
  caregiverId?: string
  serviceTypeId?: string
  scheduledStart?: string
  scheduledEnd?: string
  notes?: string
}

export interface AssignCaregiverRequest {
  caregiverId: string
}

// ── Clients ───────────────────────────────────────────────────────────────────

export interface ClientResponse {
  id: string
  firstName: string
  lastName: string
  dateOfBirth: string
  address: string | null
  phone: string | null
  medicaidId: string | null
  serviceState: string | null
  preferredCaregiverGender: string | null
  preferredLanguages: string[] | null
  noPetCaregiver: boolean
  status: ClientStatus
  createdAt: string
}

export interface AuthorizationResponse {
  id: string
  clientId: string
  payerId: string
  serviceTypeId: string
  authNumber: string
  authorizedUnits: number
  usedUnits: number
  unitType: 'HOUR' | 'VISIT' | 'DAY'
  startDate: string
  endDate: string
  version: number
  createdAt: string
}

export interface CreateClientRequest {
  firstName: string
  lastName: string
  dateOfBirth: string               // YYYY-MM-DD — Jackson reads directly as LocalDate
  phone?: string
  address?: string
  serviceState?: string             // 2-letter abbreviation; omit (not "") when blank
  medicaidId?: string
  preferredCaregiverGender?: string // "FEMALE" | "MALE"; omit (not "") for no preference
  preferredLanguages?: string       // JSON array string e.g. '["English","Spanish"]'
  noPetCaregiver?: boolean
}

// ── Caregivers ────────────────────────────────────────────────────────────────

export interface CaregiverResponse {
  id: string
  firstName: string
  lastName: string
  email: string
  phone: string | null
  address: string | null
  hireDate: string | null
  hasPet: boolean
  status: CaregiverStatus
  createdAt: string
}

export interface CredentialResponse {
  id: string
  caregiverId: string
  agencyId: string
  credentialType: string
  issueDate: string | null
  expiryDate: string | null
  verified: boolean
  verifiedBy: string | null
  verifiedAt: string | null
  createdAt: string
}

export interface BackgroundCheckResponse {
  id: string
  caregiverId: string
  agencyId: string
  checkType: string
  result: 'PASS' | 'FAIL' | 'PENDING' | 'EXPIRED'
  checkedAt: string          // ISO-8601 LocalDate
  renewalDueDate: string | null
  createdAt: string
}

// ── Payers ────────────────────────────────────────────────────────────────────

export interface PayerResponse {
  id: string
  name: string
  payerType: PayerType
  state: string
  evvAggregator: string | null
  createdAt: string
}

// ── Service Types ─────────────────────────────────────────────────────────────

export interface ServiceTypeResponse {
  id: string
  name: string
  code: string
  requiresEvv: boolean
  requiredCredentials: string[]
}

// ── Dashboard ─────────────────────────────────────────────────────────────────

export interface DashboardVisitRow {
  shiftId: string
  clientFirstName: string
  clientLastName: string
  caregiverId: string | null
  caregiverFirstName: string | null
  caregiverLastName: string | null
  serviceTypeName: string
  scheduledStart: string
  scheduledEnd: string
  status: ShiftStatus
  evvStatus: EvvComplianceStatus
  evvStatusReason: string | null
}

export interface DashboardAlert {
  type: DashboardAlertType
  subject: string
  detail: string
  dueDate: string
  resourceId: string
  resourceType: DashboardAlertResourceType
}

export interface DashboardTodayResponse {
  redEvvCount: number
  yellowEvvCount: number
  uncoveredCount: number
  onTrackCount: number
  visits: DashboardVisitRow[]
  alerts: DashboardAlert[]
}

// ── EVV History ───────────────────────────────────────────────────────────────

export interface EvvHistoryRow {
  shiftId: string
  clientFirstName: string
  clientLastName: string
  caregiverFirstName: string | null
  caregiverLastName: string | null
  serviceTypeName: string | null
  scheduledStart: string // ISO-8601 LocalDateTime
  scheduledEnd: string
  evvStatus: EvvComplianceStatus
  evvStatusReason: string | null
  shiftStatus: string | null
  timeIn: string | null
  timeOut: string | null
  verificationMethod: VerificationMethod | null
}
