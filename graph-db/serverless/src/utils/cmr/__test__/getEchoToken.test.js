import { getEchoToken } from '../getEchoToken'

import * as getSecureParam from '../getSecureParam'

const OLD_ENV = process.env

beforeEach(() => {
  // Manage resetting ENV variables
  jest.resetModules()
  process.env = { ...OLD_ENV }
  delete process.env.NODE_ENV
})

afterEach(() => {
  // Restore any ENV variables overwritten in tests
  process.env = OLD_ENV
})

describe('getEchoToken', () => {
  test('Default setting, no CMR_TOKEN_KEY value is set', async () => {
    process.env.IS_LOCAL = 'false'
    process.env.CMR_TOKEN_KEY = null

    const response = await getEchoToken()

    expect(response).toEqual(null)
  })

  test('Configure an ECHO token to retrieve from AWS', async () => {
    process.env.IS_LOCAL = 'false'
    process.env.CMR_TOKEN_KEY = 'SIT_TOKEN'

    jest.spyOn(getSecureParam, 'getSecureParam').mockResolvedValue('1234-abcd-5678-efgh')

    const response = await getEchoToken()

    expect(response).toEqual('1234-abcd-5678-efgh')
  })

  test('IS_LOCAL = true', async () => {
    process.env.IS_LOCAL = 'true'
    process.env.TOKEN = null

    const token = await getEchoToken()

    expect(token).toEqual(null)
  })

  test('Error fetching ECHO Token', async () => {
    process.env.IS_LOCAL = 'false'
    process.env.CMR_TOKEN_KEY = 'SIT_TOKEN'

    const consoleMock = jest.spyOn(console, 'log')

    jest.spyOn(getSecureParam, 'getSecureParam').mockImplementationOnce(() => {
      throw new Error('Oh no! We couldn\'t get a token')
    })

    await getEchoToken()

    expect(consoleMock).toHaveBeenCalledTimes(1)
  })
})
