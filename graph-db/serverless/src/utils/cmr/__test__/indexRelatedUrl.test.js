import { updateCollection } from '../../../testUtil/indexCollection'
import { verifyRelatedUrlExistInGraphDb } from '../../../testUtil/verifyRelatedUrl'

describe('indexRelatedUrl', () => {
  describe('when the provided relatedUrl is correct', () => {
    test('it indexes the relatedUrl', async () => {
      const relatedUrl = {
        Type: 'VIEW RELATED INFORMATION',
        SubType: 'PublicationUrl',
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
})
