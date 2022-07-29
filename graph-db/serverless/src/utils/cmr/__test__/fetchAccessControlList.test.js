import nock from 'nock'

import { fetchAccessControlList } from '../fetchAccessControlList'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('fetch an acl record from CMR', () => {
  test('returns an acl record', async () => {
    const mockedBody = {
      mock: 'body'
    }

    nock(/local-cmr/)
      .get(/acls/)
      .reply(200, mockedBody)
    // This wil likely result in an error without a token since acls are private
    // Since we are mocking the result of the API for this test it is acceptable
    const result = await fetchAccessControlList('ACL1200000027-CMR')

    const { data } = result

    expect(data).toEqual(mockedBody)
  })

  test('returns collection metadata with a token', async () => {
    const mockedBody = {
      mock: 'body'
    }

    nock(/local-cmr/)
      .matchHeader('Echo-Token', 'mock_token')
      .get(/acls/)
      .reply(200, mockedBody)

    const result = await fetchAccessControlList('ACL1200000027-CMR', 'mock_token')

    const { data } = result

    expect(data).toEqual(mockedBody)
  })

  test('logs an error', async () => {
    const consoleMock = jest.spyOn(console, 'log').mockImplementation(() => {})
    // Could error if the HTTP request has an issue
    nock(/local-cmr/)
      .get(/acls/)
      .reply(500)

    const result = await fetchAccessControlList('ACL1200000027-CMR')
    expect(result).toBe(null)

    expect(consoleMock).toHaveBeenCalledTimes(1)
  })
})
