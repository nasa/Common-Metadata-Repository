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
  test('fetches ECHO token from AWS', async () => {
    jest.spyOn(getSecureParam, 'getSecureParam').mockResolvedValue('1234-abcd-5678-efgh')

    const response = await getEchoToken()

    expect(response).toEqual('1234-abcd-5678-efgh')
  })

  test('IS_LOCAL = true', async () => {
    process.env.IS_LOCAL = 'true'

    const token = await getEchoToken()

    expect(token).toEqual(null)
  })
})
