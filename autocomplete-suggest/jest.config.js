// jest.config.js

module.exports = {
  clearMocks: true,
  coverageDirectory: 'coverage',
  reporters: ['default', ['jest-junit', { suiteName: 'jest tests' }]],
  testEnvironment: 'node',
  setupFiles: [
    './test/jest.setup.js',
  ],
};
