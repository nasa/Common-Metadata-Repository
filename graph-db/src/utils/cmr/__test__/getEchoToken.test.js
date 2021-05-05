const AWS = require('aws-sdk')
const { getEchoToken } = require('../getEchoToken')
const { getSecureParam } = require('../../getSecureParam')

beforeEach(() => {
  jest.clearAllMocks()

  jest.spyOn(getSecureParam, 'getSecureParam').mockImplementation(() => jest.fn('SUPER-SECRET-TOKEN'))
})

describe('getEchoToken', () => {
  test('calls getSecureParam', async () => {
    //   const mock = jest.spyOn(getSecureParam, 'getSecureParam').mockImplementationOnce(() => jest.fn())
      await getEchoToken()

      expect(mock).toBeCalledTimes(1)
      expect(mock).toBeCalledWith(null)
  })
  test('fetches ECHO token from AWS', async () => {
    const secretsManagerData = {
      promise: jest.fn().mockResolvedValue({
        Parameter: { Value: 'SUPER-SECRET-TOKEN' }
      })
    }

    AWS.SSM = jest.fn(() => ({
        getParameter: jest.fn().mockImplementationOnce(() => (secretsManagerData))
    }))

    const response = await getEchoToken()

    expect(response).toEqual('SUPER-SECRET-TOKEN')
    expect(mock).toBeCalledTimes(1)
  })
})