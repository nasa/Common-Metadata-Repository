import nock from 'nock'

import { clearScrollSession } from '../clearScrollSession'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('clearScrollSession', () => {
  test('Blank scroll session', async () => {
    const consoleOutput = jest.spyOn(console, 'warn')

    nock(/local-cmr/)
      .post(/search/)
      .reply(204, {})

    const response = await clearScrollSession()

    expect(consoleOutput).toBeCalledTimes(0)
    expect(response).toBe(null)
  })

  test('Valid scroll id', async () => {
    nock(/local-cmr/)
      .post(/clear-scroll/, JSON.stringify({ scroll_id: 196827907 }))
      .reply(204)

    const response = await clearScrollSession('196827907')

    expect(response.status).toEqual(204)
  })

  test('logs an error when the request fails', async () => {
    const consoleMock = jest.spyOn(console, 'log').mockImplementation(() => {})

    nock(/local-cmr/)
      .post(/clear-scroll/)
      .reply(500, new Error('Fail'))

    const sessionId = '196827907'

    await clearScrollSession(sessionId)

    expect(consoleMock).toHaveBeenCalledTimes(2)
    expect(consoleMock.mock.calls[0][0]).toEqual(`Clearing scroll session with '${sessionId}'...`)
    expect(consoleMock.mock.calls[1][0]).toContain(`Could not clear scroll session [${sessionId}] due to error`)
  })
})
