import nock from 'nock'

import { closeGremlinConnection, initializeGremlinConnection } from './serverless/src/utils/gremlin/initializeGremlinConnection'

process.env.cmrRoot = 'http://local-cmr'
process.env.collectionIndexingQueueUrl = 'http://example.com/collectionIndexQueue'
process.env.gremlinUrl = 'ws://localhost:8182/gremlin'
process.env.IS_LOCAL = true
process.env.pageSize = 1

nock.cleanAll()
nock.disableNetConnect()
nock.enableNetConnect(/local/)

beforeAll(() => {
  global.testGremlinConnection = initializeGremlinConnection()
})

global.beforeEach(async () => {
  // Clear out the gremlin server before running each test
  await global.testGremlinConnection.V().drop().iterate()
})

afterAll(() => {
  // Closing the DB connection allows Jest to exit successfully.
  closeGremlinConnection()
})
