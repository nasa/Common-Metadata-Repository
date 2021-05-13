const AWS = require('aws-sdk')
const { getEchoToken } = require('../getEchoToken')
let { getSecureParam } = require('../../getSecureParam')

beforeEach(() => {
  jest.clearAllMocks()
})

describe('getEchoToken', () => {
  test('fetches ECHO token from AWS', async () => {
    const secretsManagerData = {
      promise: jest.fn().mockResolvedValue({
        Parameter: { Value: 'SUPER-SECRET-TOKEN' }
      })
    }

    AWS.SSM = jest.fn(() => ({
      getParameter: jest.fn().mockImplementationOnce(() => (secretsManagerData))
    }))
    getSecureParam = jest.fn(() => 'SUPER-SECRET-TOKEN')

    const response = await getEchoToken()

    expect(response).toEqual('SUPER-SECRET-TOKEN')
    expect(secretsManagerData.promise).toBeCalledTimes(1)
  })
  test('IS_LOCAL = true', async () => {
    process.env.IS_LOCAL = true

    const token = await getEchoToken()
    expect(token).toEqual(null)
  })
})
