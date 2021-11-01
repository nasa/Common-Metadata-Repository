import * as d from '../deleteCmrCollection'
import * as dv from '../deleteLinkedVertices'

beforeEach(() => {
  jest.clearAllMocks()
})
describe('cmr#deleteLinkedVerticies', () => {
  test('catches errors', async () => {
    const consoleError = jest.spyOn(console, 'error')
    const errorMessage = await dv.deleteLinkedVertices('C123000001-CMR', null, null, null)

    expect(errorMessage).toEqual(false)
    expect(consoleError).toHaveBeenCalledTimes(1)
  })
})

describe('cmr#deleteCmrCollection', () => {
  const vertexDeleteMock = jest.spyOn(dv, 'deleteLinkedVertices')

  test('handles first unsucessful linked vertex deletion', async () => {
    vertexDeleteMock.mockResolvedValueOnce(false)

    const deleteSuccess = await d.deleteCmrCollection('C123000001-CMR', 'connection placeholder')

    expect(deleteSuccess).toEqual(false)
    expect(vertexDeleteMock).toHaveBeenCalledTimes(1)
  })

  test('handles second unsucessful linked vertex deletion', async () => {
    vertexDeleteMock.mockResolvedValueOnce(() => true)
      .mockResolvedValueOnce(() => false)

    const deleteSuccess2 = await d.deleteCmrCollection('C123000001-CMR', 'connection placeholder')

    expect(deleteSuccess2).toEqual(false)
    expect(vertexDeleteMock).toHaveBeenCalledTimes(3)
  })

  test('handles third unsucessful linked vertex deletion', async () => {
    vertexDeleteMock.mockResolvedValueOnce(() => true)
      .mockResolvedValueOnce(() => true)
      .mockResolvedValueOnce(() => false)

    const deleteSuccess3 = await d.deleteCmrCollection('C123000001-CMR', 'connection placeholder')

    expect(deleteSuccess3).toEqual(false)
    expect(vertexDeleteMock).toHaveBeenCalledTimes(3)
  })

  test('handles gremlin error after vertex deletions', async () => {
    vertexDeleteMock.mockResolvedValueOnce(() => true)
      .mockResolvedValueOnce(() => true)
      .mockResolvedValueOnce(() => true)

    const deleteSuccess = await d.deleteCmrCollection('C123000001-CMR', 'connection placeholder')

    expect(deleteSuccess).toEqual(false)
    expect(vertexDeleteMock).toHaveBeenCalledTimes(3)
  })
})
