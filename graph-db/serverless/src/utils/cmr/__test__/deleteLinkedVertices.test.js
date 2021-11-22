import { deleteLinkedVertices } from '../deleteLinkedVertices'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('cmr#deleteLinkedVerticies', () => {
  test('catches errors', async () => {
    const consoleMock = jest.spyOn(console, 'log')

    const errorMessage = await deleteLinkedVertices('C123000001-CMR', null, null, null)

    expect(errorMessage).toEqual(false)
    expect(consoleMock).toHaveBeenCalledTimes(1)
  })
})
