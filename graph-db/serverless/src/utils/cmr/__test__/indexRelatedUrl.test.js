import { indexRelatedUrl } from '../indexRelatedUrl'
import { updateCollection } from '../../../testUtil/indexCollection'
import { verifyRelatedUrlExistInGraphDb } from '../../../testUtil/verifyRelatedUrl'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('indexRelatedUrl', () => {
  describe('when the provided relatedUrl is correct', () => {
    test('it indexes the relatedUrl', async () => {
      const relatedUrl = {
        Type: 'VIEW RELATED INFORMATION',
        Subtype: 'PublicationUrl',
        Description: 'Nulla vitae elit libero, a pharetra augue.',
        URL: 'https://example.com/test.json'
      }

      await updateCollection(
        'C100000-CMR',
        'Vulputate Mollis Commodo',
        {
          relatedUrls: [relatedUrl.URL]
        }
      )

      await verifyRelatedUrlExistInGraphDb('Vulputate Mollis Commodo', 'https://example.com/test.json')
    })
  })

  describe('when the provided relatedUrl is missing a SubType', () => {
    test('it indexes the relatedUrl', async () => {
      const relatedUrl = {
        Type: 'VIEW RELATED INFORMATION',
        Description: 'Nulla vitae elit libero, a pharetra augue.',
        URL: 'https://example.com/test.json'
      }

      await updateCollection(
        'C100000-CMR',
        'Vulputate Mollis Commodo',
        {
          relatedUrls: [relatedUrl.URL]
        }
      )

      await verifyRelatedUrlExistInGraphDb('Vulputate Mollis Commodo', 'https://example.com/test.json')
    })
  })

  describe('when an exception is thrown', () => {
    test('it is caught and a new one is thrown', async () => {
      const consoleMock = jest.spyOn(console, 'log')

      const conceptId = 'C1000000-CMR'

      const relatedUrl = {
        Type: 'VIEW RELATED INFORMATION',
        Description: 'Nulla vitae elit libero, a pharetra augue.',
        URL: 'https://example.com/test.json'
      }

      // Provide `null` for the gremlin connection to throw an error
      await expect(
        indexRelatedUrl(relatedUrl, null, {}, conceptId)
      ).rejects.toThrow('Cannot read properties of null (reading \'addV\')')

      expect(consoleMock).toBeCalledTimes(2)
      expect(consoleMock.mock.calls[0][0]).toEqual(`Failed to index RelatedUrl for concept [${conceptId}] ${JSON.stringify(relatedUrl)}`)
      expect(consoleMock.mock.calls[1][0]).toEqual(Error('Cannot read properties of null (reading \'addV\')'))
    })
  })
})
