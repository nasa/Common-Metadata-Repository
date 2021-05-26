const { bootstrap } = require('../handler')
const bootstrapGremilinServer = require('../../utils/bootstrapGremlinServer')

beforeEach(() => {
  jest.clearAllMocks()
})

describe('bootstrapGremlinServer handler', () => {
  test('test service response', async () => {
    jest.spyOn(bootstrapGremilinServer, 'bootstrapGremilinServer').mockImplementation(() => true)

    const response = await bootstrap()
    expect(response).toEqual({ statusCode: 200, body: 'Indexing completed' })
  })
})
