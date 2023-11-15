module.exports = {
  clearMocks: true,
  collectCoverage: true,
  collectCoverageFrom: [
    'serverless/src/**/{!(testUtil),}.js'
  ],
  coveragePathIgnorePatterns: [
    'serverless/src/testUtil/'
  ],
  setupFilesAfterEnv: ['<rootDir>/test-env.js'],
  globals: {
    testGremlinConnection: null
  },
  modulePathIgnorePatterns: [
    '.*__mocks__.*',
    'dist'
  ],
  transformIgnorePatterns: ['node_modules/(?!axios)']
}
