import { deleteCmrCollection } from '../deleteCmrCollection'

import * as deleteLinkedVertices from '../deleteLinkedVertices'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('cmr#deleteCmrCollection', () => {
  const vertexDeleteMock = jest.spyOn(deleteLinkedVertices, 'deleteLinkedVertices')

  test('handles first unsucessful linked vertex deletion', async () => {
    vertexDeleteMock.mockResolvedValueOnce(false)

    const deleteSuccess = await deleteCmrCollection('C123000001-CMR', global.testGremlinConnection)

    expect(deleteSuccess).toEqual(false)
    expect(vertexDeleteMock).toHaveBeenCalledTimes(1)
  })

  test('handles second unsucessful linked vertex deletion', async () => {
    vertexDeleteMock.mockResolvedValueOnce(true)
      .mockResolvedValueOnce(false)

    const deleteSuccess2 = await deleteCmrCollection('C123000001-CMR', global.testGremlinConnection)

    expect(deleteSuccess2).toEqual(false)
    expect(vertexDeleteMock).toHaveBeenCalledTimes(2)
  })

  test('handles third unsucessful linked vertex deletion', async () => {
    vertexDeleteMock.mockResolvedValueOnce(true)
      .mockResolvedValueOnce(true)
      .mockResolvedValueOnce(false)

    const deleteSuccess3 = await deleteCmrCollection('C123000001-CMR', global.testGremlinConnection)

    expect(deleteSuccess3).toEqual(false)
    expect(vertexDeleteMock).toHaveBeenCalledTimes(3)
  })

  test('handles gremlin error after vertex deletions', async () => {
    vertexDeleteMock.mockResolvedValueOnce(true)
      .mockResolvedValueOnce(true)
      .mockResolvedValueOnce(true)

    const deleteSuccess = await deleteCmrCollection('C123000001-CMR', null)

    expect(deleteSuccess).toEqual(false)
    expect(vertexDeleteMock).toHaveBeenCalledTimes(3)
  })
})
