import { updateCollection } from '../../../testUtil/indexCollection'
import { indexInstrument } from '../indexInstrument'
import { verifyPlatformInstrumentsExistInGraphDb, verifyPlatformInstrumentsNotExistInGraphDb } from '../../../testUtil/verifyPlatformInstrument'

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

  describe('when the provided instrument is missing a ShortName', () => {
    test('it does not index the instrument', async () => {
      const instrument = { LongName: 'ukulele' }

      await updateCollection(
        'C100000-CMR',
        'Vulputate Mollis Commodo',
        {
          instruments: instrument.LongName
        }
      )

      verifyPlatformInstrumentsNotExistInGraphDb('Vulpate Mollis Commodo')
    })
  })

  describe('when the gremlin connection is broken', () => {
    test('it handles the thrown errors', async () => {
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
