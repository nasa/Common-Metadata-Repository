import { indexCmrCollection } from '../indexCmrCollection'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('utils#indexCmrCollection', () => {
  describe('when an exception is thrown', () => {
    test('it is caught and logged', async () => {
      const consoleMock = jest.spyOn(console, 'log')

      const conceptId = 'C1000000-CMR'

      const collectionObj = {
        meta: {
          'concept-id': conceptId
        },
        umm: {
          EntryTitle: 'entryTitle',
          DOI: {
            DOI: 'doiDescription'
          },
          Projects: 'projects',
          Platforms: 'platforms',
          RelatedUrls: 'relatedUrls',
          ShortName: 'shortName'
        }
      }

      // Provide `null` for the gremlin connection to throw an error
      const result = await indexCmrCollection(collectionObj, null)

      expect(result).toBeFalsy()

      expect(consoleMock).toBeCalledTimes(2)

      // Error message logged because deleteCmrCollection failed because of null gremlinConnection
      expect(consoleMock.mock.calls[0][0]).toEqual(`Error deleting project vertices only linked to collection [${conceptId}]: Cannot read properties of null (reading 'V')`)
      // Error message logged because addV failed because of null gremlinConnection
      expect(consoleMock.mock.calls[1][0]).toEqual(`Error indexing collection [${conceptId}]: Cannot read properties of null (reading 'addV')`)
    })
  })
})
