import nock from 'nock'

import { fetchCmrCollection } from '../fetchCmrCollection'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('fetchCmrCollection', () => {
  test('returns collection metadata', async () => {
    const mockedBody = {
      mock: 'body'
    }

    nock(/local-cmr/)
      .get(/collections/)
      .reply(200, mockedBody)

    const result = await fetchCmrCollection('C1216257563-LPDAAC_TS1')

    const { data } = result

    expect(data).toEqual(mockedBody)
  })

  test('returns collection metadata with a token', async () => {
    const mockedBody = {
      mock: 'body'
    }

    nock(/local-cmr/)
      .matchHeader('Authorization', 'mock_token')
      .get(/collections/)
      .reply(200, mockedBody)

    const result = await fetchCmrCollection('C1216257563-LPDAAC_TS1', 'mock_token')

    const { data } = result

    expect(data).toEqual(mockedBody)
  })

  test('logs an error', async () => {
    const consoleMock = jest.spyOn(console, 'log').mockImplementation(() => {})

    nock(/local-cmr/)
      .get(/collections/)
      .reply(500)

    const result = await fetchCmrCollection('C1216257563-LPDAAC_TS1')
    expect(result).toBe(null)

    expect(consoleMock).toHaveBeenCalledTimes(1)
  })
})
