import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { apiClient } from './client'
import { listShifts, listOpenShifts, getShift, createShift, assignCaregiver, broadcastShift, getCandidates, clockIn } from './shifts'
import type { ShiftSummaryResponse, ShiftDetailResponse, RankedCaregiverResponse, PageResponse } from '../types/api'

vi.mock('../store/authStore', () => ({
  useAuthStore: { getState: vi.fn().mockReturnValue({ token: 'tok', logout: vi.fn() }) },
}))

describe('shifts API', () => {
  let mock: MockAdapter

  beforeEach(() => { mock = new MockAdapter(apiClient) })
  afterEach(() => { mock.restore() })

  const summary: ShiftSummaryResponse = {
    id: 's1', agencyId: 'a1', clientId: 'c1', caregiverId: null,
    serviceTypeId: 'st1', authorizationId: null, sourcePatternId: null,
    scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00',
    status: 'OPEN', notes: null,
  }

  const detail: ShiftDetailResponse = { ...summary, evv: null }

  it('listShifts calls GET /shifts with start/end params', async () => {
    const page: PageResponse<ShiftSummaryResponse> = {
      content: [summary], totalElements: 1, totalPages: 1, number: 0, size: 200, first: true, last: true,
    }
    mock.onGet('/shifts', { params: { start: '2026-04-06T00:00:00', end: '2026-04-13T00:00:00', size: 200, sort: 'scheduledStart' } }).reply(200, page)
    const result = await listShifts('2026-04-06T00:00:00', '2026-04-13T00:00:00')
    expect(result.content).toHaveLength(1)
    expect(result.content[0].id).toBe('s1')
  })

  it('listOpenShifts calls GET /shifts with status=OPEN', async () => {
    const page: PageResponse<ShiftSummaryResponse> = {
      content: [summary], totalElements: 1, totalPages: 1, number: 0, size: 200, first: true, last: true,
    }
    mock.onGet('/shifts', {
      params: { start: '2026-04-06T00:00:00', end: '2026-04-13T00:00:00', status: 'OPEN', size: 200, sort: 'scheduledStart' },
    }).reply(200, page)

    const result = await listOpenShifts('2026-04-06T00:00:00', '2026-04-13T00:00:00')
    expect(result).toHaveLength(1)
    expect(result[0].status).toBe('OPEN')
  })

  it('getShift calls GET /shifts/:id', async () => {
    mock.onGet('/shifts/s1').reply(200, detail)
    const result = await getShift('s1')
    expect(result.id).toBe('s1')
    expect(result.evv).toBeNull()
  })

  it('createShift posts to /shifts', async () => {
    mock.onPost('/shifts').reply(201, summary)
    const result = await createShift({ clientId: 'c1', serviceTypeId: 'st1', scheduledStart: '2026-04-07T09:00:00', scheduledEnd: '2026-04-07T13:00:00' })
    expect(result.clientId).toBe('c1')
  })

  it('assignCaregiver patches /shifts/:id/assign', async () => {
    mock.onPatch('/shifts/s1/assign').reply(200, { ...summary, caregiverId: 'g1' })
    const result = await assignCaregiver('s1', 'g1')
    expect(result.caregiverId).toBe('g1')
  })

  it('broadcastShift posts to /shifts/:id/broadcast', async () => {
    mock.onPost('/shifts/s1/broadcast').reply(200)
    await expect(broadcastShift('s1')).resolves.toBeUndefined()
  })

  it('getCandidates calls GET /shifts/:id/candidates', async () => {
    const candidates: RankedCaregiverResponse[] = [{ caregiverId: 'g1', score: 95, explanation: 'Great match' }]
    mock.onGet('/shifts/s1/candidates').reply(200, candidates)
    const result = await getCandidates('s1')
    expect(result[0].caregiverId).toBe('g1')
  })

  it('clockIn posts to /shifts/:id/clock-in', async () => {
    mock.onPost('/shifts/s1/clock-in').reply(200, detail)
    const result = await clockIn('s1', { locationLat: 30.2, locationLon: -97.7, verificationMethod: 'MANUAL', capturedOffline: false })
    expect(result.id).toBe('s1')
  })
})
