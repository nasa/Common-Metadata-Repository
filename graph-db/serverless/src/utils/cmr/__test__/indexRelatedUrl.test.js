import { updateCollection } from '../../../testUtil/indexCollection'
import { verifyRelatedUrlExistInGraphDb } from '../../../testUtil/verifyRelatedUrl'
import { indexRelatedUrl } from '../indexRelatedUrl'

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

      verifyRelatedUrlExistInGraphDb('Vulputate Mollis Commodo', 'https://example.com/test.json')
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

      verifyRelatedUrlExistInGraphDb('Vulputate Mollis Commodo', 'https://example.com/test.json')
    })
  })

  describe('when an exceptionn is thrown', () => {
    test('it is caught and a new one is thrown', async () => {
      let errorMessage
      const consoleError = jest.spyOn(console, 'error')
      const relatedUrl = {
        Type: 'VIEW RELATED INFORMATION',
        Description: 'Nulla vitae elit libero, a pharetra augue.',
        URL: 'https://example.com/test.json'
      }

      try {
        await indexRelatedUrl(relatedUrl, null, {}, 'C1000000-CMR')
      } catch (error) {
        errorMessage = error.message
      }

      expect(consoleError).toBeCalledTimes(1)
      expect(errorMessage).toEqual("Cannot read property 'addV' of null")
    })
  })
})
