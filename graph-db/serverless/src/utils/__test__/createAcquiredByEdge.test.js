import { createAcquiredByEdge } from '../cmr/createAcquiredByEdge'

beforeEach(() => {
  jest.clearAllMocks()
})
describe('util#createAcquiredByEdge', () => {
  test('catches errors', async () => {
    const consoleError = jest.spyOn(console, 'error')
    const errorMessage = await createAcquiredByEdge('basoon', 'this is not a gremlin connection', 'C123000001-CMR')

    expect(errorMessage).toEqual({})
    expect(consoleError).toHaveBeenCalledTimes(1)
  })
})
