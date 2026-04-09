// mobile/jest.config.js
module.exports = {
  preset: 'jest-expo',
  // NOTE: In Jest 24–27 this key is `setupFilesAfterFramework`; in Jest 28+ it was aliased.
  // Verify this matches your installed jest-expo version — if matchers fail to load, this key is the first thing to check.
  setupFilesAfterFramework: ['@testing-library/jest-native/extend-expect'],
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?)|expo(nent)?|@expo(nent)?/.*|@expo-google-fonts/.*|react-navigation|@react-navigation/.*|@unimodules/.*|unimodules|sentry-expo|native-base|react-native-svg)',
  ],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
  },
};
