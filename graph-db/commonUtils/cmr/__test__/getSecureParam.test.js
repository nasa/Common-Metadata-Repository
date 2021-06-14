const AWS = require('aws-sdk')
const { getSecureParam } = require('../getSecureParam')

beforeEach(() => {
  jest.clearAllMocks()
})

describe('getSecureParam', () => {
  test('fetches urs credentials from AWS', async () => {
    const secretsManagerData = {
      promise: jest.fn().mockResolvedValue({
        Parameter: { Value: 'SUPER-SECRET-TOKEN' }
      })
    }

    AWS.SSM = jest.fn(() => ({
      getParameter: jest.fn().mockImplementationOnce(() => (secretsManagerData))
    }))

    const response = await getSecureParam(`/${process.env.ENVIRONMENT}/graph-db/CMR_ECHO_SYSTEM_TOKEN`)

    expect(response).toEqual('SUPER-SECRET-TOKEN')
    expect(secretsManagerData.promise).toBeCalledTimes(1)
  })
})
