import 'array-foreach-async'

import { indexPlatform } from '../indexPlatform'
import * as indexInstrument from '../indexInstrument'

describe('utils#indexPlatform', () => {
  test('function handles errors', async () => {
    let indexError
    const consoleError = jest.spyOn(console, 'error')

    jest.spyOn(indexInstrument, 'indexInstrument').mockImplementationOnce(async () => {
      throw new Error('Ouch!')
    })

    try {
      await indexPlatform({ ShortName: 'nme', Instruments: ['viola', 'cello', 'electric triangle'] })
    } catch (error) {
      indexError = error.message
    }

    expect(indexError).toEqual('Ouch!')
    expect(consoleError).toBeCalledTimes(1)
  })
})
