const { indexCmrCollection } = require('../handler')

jest.mock('../../../../commonUtils/cmr/getEchoToken')
jest.mock('../../../../commonUtils/gremlin/initializeGremlinConnection')
jest.mock('../../../../commonUtils/indexing/indexCmrCollection')

beforeEach(() => {
  jest.clearAllMocks()
})

describe('indexCmrCollection handler', () => {
  test('test index of single collection', async () => {
    const event = {
      body: {
        'concept-id': 'C1934541400-SCIOPS'
      }
    }

    const indexed = await indexCmrCollection(event)

    const { body, statusCode } = indexed
    expect(body).toBe('Collection [C1934541400-SCIOPS] indexed sucessfully: true')
    expect(statusCode).toBe(200)
  })
})