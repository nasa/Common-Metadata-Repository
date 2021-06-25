import nock from 'nock'

import { clearScrollSession } from '../clearScrollSession'

describe('clearScrollSession', () => {
  test('Blank scroll session', async () => {
    nock(/local-cmr/).post(/search/).reply(204, {})

    const consoleOutput = jest.spyOn(console, 'warn')
    const response = await clearScrollSession()

    expect(consoleOutput).toBeCalledTimes(0)
    expect(response).toBe(null)
  })

  test('Valid scroll id', async () => {
    nock(/local-cmr/)
      .post(/clear-scroll/, JSON.stringify({ scroll_id: 196827907 }))
      .reply(204)

    await clearScrollSession('196827907')
      .then((res) => expect(res).toEqual(204))
  })
})
