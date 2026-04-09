// jest.setup.js — Global Jest mock setup for the mobile app
// Provides stubs for native modules that have no runtime in the Jest/Node environment.

// Expo SDK 55 ships a ReadableStream polyfill that throws when axios probes
// the fetch adapter at module load. Remove it so axios falls back to the
// Node.js HTTP adapter, which works fine in Jest.
delete global.ReadableStream;

// ── react-native-safe-area-context ───────────────────────────────────────────
const INSETS = { top: 0, right: 0, bottom: 0, left: 0 };
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaProvider: ({ children }) => children,
  SafeAreaConsumer: ({ children }) => children(INSETS),
  SafeAreaView: ({ children }) => children,
  useSafeAreaInsets: () => INSETS,
  useSafeAreaFrame: () => ({ x: 0, y: 0, width: 390, height: 844 }),
  initialWindowMetrics: { insets: INSETS, frame: { x: 0, y: 0, width: 390, height: 844 } },
}));

// ── @react-native-async-storage/async-storage ────────────────────────────────
jest.mock('@react-native-async-storage/async-storage', () => {
  const store = {};
  return {
    getItem: jest.fn(key => Promise.resolve(store[key] ?? null)),
    setItem: jest.fn((key, value) => { store[key] = value; return Promise.resolve(); }),
    removeItem: jest.fn(key => { delete store[key]; return Promise.resolve(); }),
    clear: jest.fn(() => { Object.keys(store).forEach(k => delete store[k]); return Promise.resolve(); }),
    getAllKeys: jest.fn(() => Promise.resolve(Object.keys(store))),
    multiGet: jest.fn(keys => Promise.resolve(keys.map(k => [k, store[k] ?? null]))),
    multiSet: jest.fn(pairs => { pairs.forEach(([k, v]) => { store[k] = v; }); return Promise.resolve(); }),
    multiRemove: jest.fn(keys => { keys.forEach(k => delete store[k]); return Promise.resolve(); }),
  };
});

// ── @react-native-community/netinfo ──────────────────────────────────────────
jest.mock('@react-native-community/netinfo', () => ({
  addEventListener: jest.fn(() => jest.fn()),
  fetch: jest.fn(() => Promise.resolve({ isConnected: true, isInternetReachable: true, type: 'wifi' })),
  useNetInfo: jest.fn(() => ({ isConnected: true, isInternetReachable: true, type: 'wifi' })),
  NetInfoStateType: { wifi: 'wifi', cellular: 'cellular', none: 'none', unknown: 'unknown' },
}));

// ── expo-notifications ───────────────────────────────────────────────────────
jest.mock('expo-notifications', () => ({
  getPermissionsAsync: jest.fn(() => Promise.resolve({ granted: true, status: 'granted' })),
  requestPermissionsAsync: jest.fn(() => Promise.resolve({ granted: true, status: 'granted' })),
  setNotificationHandler: jest.fn(),
  scheduleNotificationAsync: jest.fn(() => Promise.resolve('mock-id')),
  cancelScheduledNotificationAsync: jest.fn(() => Promise.resolve()),
  addNotificationReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
  addNotificationResponseReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
}));

// ── expo-location ─────────────────────────────────────────────────────────────
jest.mock('expo-location', () => ({
  getForegroundPermissionsAsync: jest.fn(() => Promise.resolve({ status: 'granted', granted: true })),
  getBackgroundPermissionsAsync: jest.fn(() => Promise.resolve({ status: 'granted', granted: true })),
  requestForegroundPermissionsAsync: jest.fn(() => Promise.resolve({ status: 'granted', granted: true })),
  getCurrentPositionAsync: jest.fn(() => Promise.resolve({ coords: { latitude: 0, longitude: 0, accuracy: 5 } })),
  watchPositionAsync: jest.fn(() => Promise.resolve({ remove: jest.fn() })),
  Accuracy: { Balanced: 3, High: 4, Highest: 5 },
}));

// ── expo-sqlite ───────────────────────────────────────────────────────────────
// In-memory mock that faithfully implements the sync_events table operations
// used by src/db/events.ts so db/events.test.ts exercises real logic.
jest.mock('expo-sqlite', () => {
  const createDb = () => {
    let rows = [];
    let counter = 0;
    return {
      execAsync: jest.fn(async () => {}),
      runAsync: jest.fn(async (sql, params = []) => {
        if (/INSERT INTO sync_events/i.test(sql)) {
          const rowId = ++counter;
          const [type, visit_id, task_id, gps_lat, gps_lng, captured_offline, notes, occurred_at] = params;
          rows.push({ row_id: rowId, type, visit_id, task_id, gps_lat, gps_lng, captured_offline, notes, occurred_at, synced: 0 });
          return { lastInsertRowId: rowId };
        }
        if (/UPDATE sync_events SET synced/i.test(sql)) {
          const syncedVal = Number(/synced = (\d)/.exec(sql)?.[1] ?? '1');
          const ids = new Set(params.map(Number));
          rows.forEach(r => { if (ids.has(r.row_id)) r.synced = syncedVal; });
        }
        if (/DELETE FROM sync_events/i.test(sql)) {
          rows = rows.filter(r => r.visit_id !== params[0]);
        }
        return { lastInsertRowId: 0 };
      }),
      getAllAsync: jest.fn(async sql => {
        if (/WHERE synced = 0/i.test(sql)) return rows.filter(r => r.synced === 0);
        return rows;
      }),
    };
  };
  return { openDatabaseAsync: jest.fn(() => Promise.resolve(createDb())) };
});
