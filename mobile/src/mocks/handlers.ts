// src/mocks/handlers.ts
// handlers.ts accepts an optional apiClient parameter. When omitted (e.g. in
// tests that call setupMocks() directly), it falls back to the shared instance
// imported from client.ts.
//
// Circular-import safety: client.ts uses a *dynamic* import() for handlers.ts,
// so this static import only resolves after client.ts has fully evaluated and
// apiClient is defined. The module cache ensures no re-evaluation occurs.
// See: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Modules#dynamic_imports
import MockAdapter from 'axios-mock-adapter';
import type { AxiosInstance } from 'axios';
import { apiClient as defaultClient } from '@/api/client';
import {
  mockAuthResponse, mockTodayShifts, mockWeekShifts,
  mockCarePlan, mockOpenShifts, mockThreads, mockMessages,
  mockProfile, mockProfileStats, MOCK_VISIT_ID, MOCK_THREAD_ID_1,
} from './data';

let mock: MockAdapter | null = null;

export function setupMocks(apiClient: AxiosInstance = defaultClient) {
  if (mock) return;
  mock = new MockAdapter(apiClient, { delayResponse: 350 });

  mock.onPost('/mobile/auth/exchange').reply(200, mockAuthResponse);
  mock.onPost('/mobile/auth/refresh').reply(200, { accessToken: 'mock-refreshed-token' });
  mock.onPost('/mobile/auth/send-link').reply(200, { message: "If that email matches your account, a link has been sent." });

  mock.onPost('/mobile/devices/push-token').reply(200);

  mock.onGet('/mobile/visits/today').reply(200, { shifts: mockTodayShifts });
  mock.onGet('/mobile/visits/week').reply(200, { shifts: mockWeekShifts });

  mock.onPost(new RegExp('/mobile/visits/.+/clock-in')).reply(200, {
    visitId: MOCK_VISIT_ID,
    clockInTime: new Date().toISOString(),
  });
  mock.onDelete(new RegExp('/mobile/visits/.+/clock-in')).reply(200);
  mock.onPost(new RegExp('/mobile/visits/.+/clock-out')).reply(200, {
    visitId: MOCK_VISIT_ID,
    clockOutTime: new Date().toISOString(),
    evvStatus: 'GREEN',
  });

  mock.onPost(new RegExp('/mobile/visits/.+/tasks/.+/complete')).reply(200);
  mock.onDelete(new RegExp('/mobile/visits/.+/tasks/.+/complete')).reply(200);
  mock.onPut(new RegExp('/mobile/visits/.+/notes')).reply(200);

  mock.onPost('/sync/visits').reply(200, {
    results: [{ visitId: MOCK_VISIT_ID, result: 'OK' }],
  });

  mock.onGet('/mobile/shifts/open').reply(200, { shifts: mockOpenShifts });
  mock.onPost(new RegExp('/mobile/shifts/.+/accept')).reply(200);
  mock.onPost(new RegExp('/mobile/shifts/.+/decline')).reply(200);

  mock.onGet('/mobile/messages').reply(200, { threads: mockThreads });
  mock.onGet(new RegExp('/mobile/messages/.+')).reply((config) => {
    const id = config.url?.split('/').pop();
    const thread = mockThreads.find(t => t.id === id);
    if (!thread) return [404, { error: 'Not found' }];
    return [200, { thread, messages: mockMessages.filter(m => m.threadId === id) }];
  });
  mock.onPost(new RegExp('/mobile/messages/.+/reply')).reply(200, {
    message: {
      id: `msg-${Date.now()}`,
      threadId: MOCK_THREAD_ID_1,
      body: 'Acknowledged, thank you.',
      sentAt: new Date().toISOString(),
      senderType: 'CAREGIVER',
    },
  });

  mock.onGet('/mobile/profile').reply(200, mockProfile);
  mock.onGet('/mobile/profile/stats').reply(200, mockProfileStats);
  mock.onGet(new RegExp('/mobile/careplan/.+')).reply(200, { carePlan: mockCarePlan });
}

export function teardownMocks() {
  mock?.restore();
  mock = null;
}
