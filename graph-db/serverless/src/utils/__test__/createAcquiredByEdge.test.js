import { createAcquiredByEdge } from '../cmr/createAcquiredByEdge'

beforeEach(() => {
  jest.clearAllMocks()
})
describe('util#createAcquiredByEdge', () => {
  test('catches errors', async () => {
    const consoleError = jest.spyOn(console, 'error')
    await createAcquiredByEdge('basoon', 'this is not a gremlin connection', 'C123000001-CMR')

    expect(consoleError).toHaveBeenCalledWith('ERROR creating acquiredBy edge: TypeError: gremlinConnection.V is not a function')
    expect(consoleError).toHaveBeenCalledTimes(1)
  })
})
