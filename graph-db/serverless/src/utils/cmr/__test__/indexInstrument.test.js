import { updateCollection } from '../../../testUtil/indexCollection'
import { indexInstrument } from '../indexInstrument'
import { verifyPlatformInstrumentsExistInGraphDb } from '../../../testUtil/verifyPlatformInstrument'

describe('indexInstrument', () => {
  describe('when the provided instrument is correct', () => {
    test('it indexes the instrument', async () => {
      const instrument = { ShortName: 'uke' }

      await updateCollection(
        'C100000-CMR',
        'Vulputate Mollis Commodo',
        {
          instruments: instrument.ShortName
        }
      )

      verifyPlatformInstrumentsExistInGraphDb('Vulputate Mollis Commodo', 'uke')
    })
  })

  describe('when the gremlin connection is broken', () => {
    test('it handles the thrown errors, and throws a new one to halt indexing', async () => {
      const consoleError = jest.spyOn(console, 'error')
      let instrumentError

      try {
        await indexInstrument({ ShortName: 'violin' }, null, 'platformium', 'C100000-CMR')
      } catch (error) {
        instrumentError = error.message
      }

      expect(consoleError).toHaveBeenCalledTimes(1)
      expect(instrumentError).toEqual("Cannot read property 'V' of null")
    })
  })
})
