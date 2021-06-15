import indexCmrCollection from '../handler'

// jest.mock('../../../../commonUtils/cmr/getEchoToken')
// jest.mock('../../../../commonUtils/cmr/fetchCmrCollection')
// jest.mock('../../../../commonUtils/gremlin/initializeGremlinConnection')
// jest.mock('../../../../commonUtils/indexing/indexCmrCollection')

beforeEach(() => {
  jest.clearAllMocks()
})

describe('indexCmrCollection handler', () => {
  test('test index of single collection', async () => {
    const event = { Records: [{ body: '{"concept-id": "C1237293909-SCIOPS", "action": "concept-update" }' }] }

    const indexed = await indexCmrCollection(event)

    const { body, statusCode } = indexed
    expect(body).toBe('Collection [C1237293909-SCIOPS] indexed sucessfully: true')
    expect(statusCode).toBe(200)
  })

  test('test unsupported event type', async () => {
    const event = { Records: [{ body: '{"concept-id": "C1237293909-SCIOPS", "action": "concept-delete" }' }] }

    const indexed = await indexCmrCollection(event)

    const { body, statusCode } = indexed
    expect(body).toBe('Action [concept-delete] was unsupported for concept [C1237293909-SCIOPS]')
    expect(statusCode).toBe(200)
  })

  test('test unsupported concept type', async () => {
    const event = { Records: [{ body: '{"concept-id": "G1237293909-SCIOPS", "action": "concept-update" }' }] }

    const indexed = await indexCmrCollection(event)

    const { body, statusCode } = indexed
    expect(body).toBe('Concept [G1237293909-SCIOPS] was not a collection and will not be indexed')
    expect(statusCode).toBe(200)
  })
})
