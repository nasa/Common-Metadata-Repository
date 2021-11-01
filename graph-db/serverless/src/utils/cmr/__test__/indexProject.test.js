import { indexProject } from '../indexProject'

describe('utils#indexProject', () => {
  test('function handles errors', async () => {
    let indexError
    const consoleError = jest.spyOn(console, 'error')

    try {
      await indexProject({ ShortName: 'nme' }, null, {}, 'C1000000234-PROV1')
    } catch (error) {
      indexError = error.message
    }

    expect(indexError).toEqual("Cannot read property 'V' of null")
    expect(consoleError).toBeCalledTimes(1)
  })
})
