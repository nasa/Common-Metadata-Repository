// For a detailed explanation regarding each configuration property, visit:
// https://jestjs.io/docs/en/configuration.html

export default {
  clearMocks: true,
  coverageDirectory: 'coverage',
  reporters: ['default', ['jest-junit', { suiteName: 'jest tests' }]],
  testEnvironment: 'node'
};
