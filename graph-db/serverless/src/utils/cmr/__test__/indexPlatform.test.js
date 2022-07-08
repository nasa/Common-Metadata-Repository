import 'array-foreach-async'

import { indexPlatform } from '../indexPlatform'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('utils#indexPlatform', () => {
  test('function handles errors', async () => {
    const consoleMock = jest.spyOn(console, 'log')

    const conceptId = 'C1000000-CMR'

    const shortName = 'nme'
    const instruments = ['viola', 'cello', 'electric triangle']
    const platform = { ShortName: shortName, Instruments: instruments }

    // Provide `null` for the gremlin connection to throw an error
    await expect(
      indexPlatform(platform, null, {}, conceptId)
    ).rejects.toThrow('Cannot read properties of null (reading \'V\')')

    expect(consoleMock).toBeCalledTimes(4)
    expect(consoleMock.mock.calls[0][0]).toEqual(`Failed to index Instrument for platform [${shortName}] "${instruments[0]}"`)
    expect(consoleMock.mock.calls[1][0]).toEqual(Error('Cannot read properties of null (reading \'V\')'))
  })

  describe('when an exception is thrown', () => {
    test('it is caught and a new one is thrown', async () => {
      const consoleMock = jest.spyOn(console, 'log')

      const conceptId = 'C1000000-CMR'
      const shortName = 'nme'
      const instruments = ['viola', 'cello', 'electric triangle']
      const platform = { ShortName: shortName, Instruments: instruments }

      // Provide `null` for the gremlin connection to throw an error
      await expect(
        indexPlatform(platform, null, {}, conceptId)
      ).rejects.toThrow('Cannot read properties of null (reading \'V\')')

      expect(consoleMock).toBeCalledTimes(4)
      expect(consoleMock.mock.calls[0][0]).toEqual(`Failed to index Instrument for platform [${shortName}] "${instruments[0]}"`)
      expect(consoleMock.mock.calls[1][0]).toEqual(Error('Cannot read properties of null (reading \'V\')'))
      expect(consoleMock.mock.calls[2][0]).toEqual(`Failed to index Platform for concept [${conceptId}] ${JSON.stringify(platform)}`)
      expect(consoleMock.mock.calls[3][0]).toEqual(Error('Cannot read properties of null (reading \'V\')'))
    })
  })
})
