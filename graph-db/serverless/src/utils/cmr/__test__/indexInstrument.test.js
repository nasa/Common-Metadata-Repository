import { indexInstrument } from '../indexInstrument'
import { updateCollection } from '../../../testUtil/indexCollection'
import { verifyPlatformInstrumentsExistInGraphDb } from '../../../testUtil/verifyPlatformInstrument'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('indexInstrument', () => {
  describe('when the provided instrument is correct', () => {
    test('it indexes the instrument', async () => {
      const platform1 = 'Platform One'
      const instrument1 = 'Instrument One'

      await updateCollection(
        'C100000-CMR',
        'Vulputate Mollis Commodo',
        {
          platforms: [{
            platform: platform1,
            instruments: [instrument1]
          }]
        }
      )

      await verifyPlatformInstrumentsExistInGraphDb('Vulputate Mollis Commodo',
        {
          platform: platform1,
          instruments: [instrument1]
        })
    })
  })

  describe('when the gremlin connection is broken', () => {
    test('it handles the thrown errors, and throws a new one to halt indexing', async () => {
      const consoleMock = jest.spyOn(console, 'log')

      const instrument = { ShortName: 'violin' }

      const conceptId = 'C100000-CMR'

      // Provide `null` for the gremlin connection to throw an error
      await expect(
        indexInstrument(instrument, null, 'GPS', conceptId)
      ).rejects.toThrow('Cannot read properties of null (reading \'V\')')

      expect(consoleMock).toBeCalledTimes(2)
      expect(consoleMock.mock.calls[0][0]).toEqual(`Failed to index Instrument for platform [GPS] ${JSON.stringify(instrument)}`)
      expect(consoleMock.mock.calls[1][0]).toEqual(Error('Cannot read properties of null (reading \'V\')'))
    })
  })
})
