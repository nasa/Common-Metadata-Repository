import nock from 'nock'

import { clearScrollSession } from '../clearScrollSession'

const OLD_ENV = process.env

beforeEach(() => {
  // Manage resetting ENV variables
  jest.resetModules()
  process.env = { ...OLD_ENV }
  delete process.env.NODE_ENV

  nock.cleanAll()
})

afterEach(() => {
  // Restore any ENV variables overwritten in tests
  process.env = OLD_ENV
})

describe('clearScrollSession', () => {
  beforeEach(() => {
    process.env.CMR_ROOT = 'http://localhost'
  })

  test('Blank scroll session', async () => {
    nock(/localhost/).post(/search/).reply(204, {})

    const consoleOutput = jest.spyOn(console, 'warn')
    const response = await clearScrollSession()

    expect(consoleOutput).toBeCalledTimes(0)
    expect(response).toBe(null)
  })

  test('Valid scroll id', async () => {
    nock(/localhost/).post(/search/).reply(204, {})

    await clearScrollSession('196827907')
      .then((res) => expect(res).toEqual(204))
  })
})
