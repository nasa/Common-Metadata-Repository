import { createAcquiredByEdge } from '../cmr/createAcquiredByEdge'

beforeEach(() => {
  jest.clearAllMocks()
})
describe('util#createAcquiredByEdge', () => {
  test('catches errors', async () => {
    let errorMessage
    const consoleError = jest.spyOn(console, 'error')
    try {
      await createAcquiredByEdge('basoon', 'this is not a gremlin connection', 'C123000001-CMR')
    } catch (error) {
      errorMessage = error.message
    }

    expect(errorMessage).toEqual("Cannot read property 'value' of undefined")
    expect(consoleError).toHaveBeenCalledTimes(1)
  })
})
