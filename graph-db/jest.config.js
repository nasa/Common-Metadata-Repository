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
  moduleNameMapper: {
    // Jest uses CommonJS to use axios version > 1.0 we must transpile the JavaScript module from ECMAScript type to CommonJS type
    // require.resolve, use(es) the internal require() machinery to look up the location of a module, but rather than loading the module, just return(s) the resolved filename.
    '^axios$': require.resolve('axios')
  }
}
