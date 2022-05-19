import { indexProject } from '../indexProject'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('utils#indexProject', () => {
  describe('when an exception is thrown', () => {
    test('it is caught and a new one is thrown', async () => {
      const consoleMock = jest.spyOn(console, 'log')

      const conceptId = 'C1000000-CMR'

      const project = { ShortName: 'nme' }

      // Provide `null` for the gremlin connection to throw an error
      await expect(
        indexProject(project, null, {}, conceptId)
      ).rejects.toThrow('Cannot read properties of null (reading \'V\')')

      expect(consoleMock).toBeCalledTimes(2)
      expect(consoleMock.mock.calls[0][0]).toEqual(`Failed to index Project for concept [${conceptId}] ${JSON.stringify(project)}`)
      expect(consoleMock.mock.calls[1][0]).toEqual(Error('Cannot read properties of null (reading \'V\')'))
    })
  })
})
