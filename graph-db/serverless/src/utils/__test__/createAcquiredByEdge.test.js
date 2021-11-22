import { createAcquiredByEdge } from '../cmr/createAcquiredByEdge'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('util#createAcquiredByEdge', () => {
  test('catches errors', async () => {
    const consoleMock = jest.spyOn(console, 'log')

    await createAcquiredByEdge('basoon', 'this is not a gremlin connection', 'C123000001-CMR')

    expect(consoleMock).toHaveBeenCalledWith('ERROR creating acquiredBy edge: TypeError: gremlinConnection.V is not a function')
    expect(consoleMock).toHaveBeenCalledTimes(1)
  })
})
