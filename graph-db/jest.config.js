module.exports = {
  clearMocks: true,
  collectCoverage: true,
  collectCoverageFrom: [
    'serverless/src/**/*.js'
  ],
  setupFilesAfterEnv: ['<rootDir>/test-env.js'],
  globals: {
    testGremlinConnection: null
  }
}
