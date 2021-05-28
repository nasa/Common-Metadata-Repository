jest.mock('../getSecureParam')
const { getEchoToken } = require('../getEchoToken')

beforeEach(() => {
  jest.clearAllMocks()
})

afterEach(() => {
  jest.clearAllMocks()
})

describe('getEchoToken', () => {
  test('fetches ECHO token from AWS', async () => {
    process.env.IS_LOCAL = false

    const response = await getEchoToken()

    expect(response).toEqual('1234-very-good-token')
  })
  test('IS_LOCAL = true', async () => {
    process.env.IS_LOCAL = true

    const token = await getEchoToken()
    expect(token).toEqual(null)
  })
})
