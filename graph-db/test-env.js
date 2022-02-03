import nock from 'nock'

import { closeGremlinConnection, initializeGremlinConnection } from './serverless/src/utils/gremlin/initializeGremlinConnection'

process.env.PAGE_SIZE = 1
process.env.IS_LOCAL = true
process.env.CMR_ROOT = 'http://local-cmr'
process.env.GREMLIN_URL = 'ws://localhost:8182/gremlin'
process.env.COLLECTION_INDEXING_QUEUE_URL = 'http://example.com/collectionIndexQueue'

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
