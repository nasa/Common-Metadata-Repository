jest.mock('../../utils/bootstrapGremlinServer')

const { bootstrap } = require('../handler')

beforeEach(() => {
  jest.clearAllMocks()
})

describe('bootstrapGremlinServer handler', () => {
  test('test service response', async () => {
    const response = await bootstrap()

    const { body, statusCode } = response
    expect(body).toBe('Indexing completed')
    expect(statusCode).toBe(200)
  })
})
