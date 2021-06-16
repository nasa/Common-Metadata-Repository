import nock from 'nock'

import { fetchPageFromCMR } from '../fetchPageFromCMR'

import * as indexPageOfCmrResults from '../indexPageOfCmrResults'

describe('fetchPageFromCMR', () => {
  // beforeAll(() => {
  //   gremlinConnection = initializeGremlinConnection()
  // })

  test('Empty page', async () => {
    const mockedBody = {
      hits: 16996,
      took: 5,
      items: []
    }

    nock(/local-cmr/).get(/search/).reply(200, mockedBody)
    nock(/local-cmr/).get(/search/).reply(200, {
      hits: 0,
      took: 5,
      items: []
    })

    const pageOfCmrResultsMock = jest.spyOn(indexPageOfCmrResults, 'indexPageOfCmrResults')

    await fetchPageFromCMR(null, null, global.testGremlinConnection)

    expect(pageOfCmrResultsMock).toBeCalledTimes(1)
  })

  test('Single result', async () => {
    const mockedBody = {
      hits: 16996,
      took: 5,
      items: [{
        meta: {
          'concept-id': 'C1237293909-TESTPROV',
          'provider-id': 'TESTPROV'
        },
        umm: {
          RelatedUrls: [
            {
              URLContentType: 'PublicationURL',
              Type: 'VIEW RELATED INFORMATION',
              Subtype: 'GENERAL DOCUMENTATION',
              URL: 'https://en.wikipedia.org/wiki/Buffalo_buffalo_Buffalo_buffalo_buffalo_buffalo_Buffalo_buffalo'
            }
          ],
          DOI: {
            DOI: 'doi:10.16904/envidat.166'
          },
          ShortName: 'latent-reserves-in-the-swiss-nfi',
          EntryTitle: '\'Latent reserves\' within the Swiss NFI'
        }
      }]
    }

    nock(/local-cmr/).get(/search/).reply(200, mockedBody)
    nock(/local-cmr/).get(/search/).reply(200, {
      hits: 0,
      took: 5,
      items: []
    })

    const pageOfCmrResultsMock = jest.spyOn(indexPageOfCmrResults, 'indexPageOfCmrResults')

    await fetchPageFromCMR(null, null, global.testGremlinConnection)

    expect(pageOfCmrResultsMock).toBeCalledTimes(1)
  })

  test('Single result with mocked ECHO token', async () => {
    const mockedBody = {
      hits: 16996,
      took: 5,
      items: [{
        meta: {
          'concept-id': 'C1237293909-TESTPROV',
          'provider-id': 'TESTPROV'
        },
        umm: {
          RelatedUrls: [
            {
              URLContentType: 'PublicationURL',
              Type: 'VIEW RELATED INFORMATION',
              Subtype: 'GENERAL DOCUMENTATION',
              URL: 'https://en.wikipedia.org/wiki/Buffalo_buffalo_Buffalo_buffalo_buffalo_buffalo_Buffalo_buffalo'
            }
          ],
          DOI: {
            DOI: 'doi:10.16904/envidat.166'
          },
          ShortName: 'latent-reserves-in-the-swiss-nfi',
          EntryTitle: '\'Latent reserves\' within the Swiss NFI'
        }
      }]
    }

    nock(/local-cmr/).get(/search/).reply(200, mockedBody)
    nock(/local-cmr/).get(/search/).reply(200, {
      hits: 0,
      took: 5,
      items: []
    })

    const pageOfCmrResultsMock = jest.spyOn(indexPageOfCmrResults, 'indexPageOfCmrResults')

    await fetchPageFromCMR('fake-scroll-id', 'SUPER-SECRET-TOKEN', global.testGremlinConnection)

    expect(pageOfCmrResultsMock).toBeCalledTimes(1)
  })

  test('Invalid concept-id', async () => {
    const mockedBody = {
      errors: ["Invalid concept_id [C1234-PROV1]! I can't believe you've done this"]
    }

    nock(/local-cmr/).get(/search/).reply(400, mockedBody)

    const pageOfCmrResultsMock = jest.spyOn(indexPageOfCmrResults, 'indexPageOfCmrResults')

    await fetchPageFromCMR(null, null, global.testGremlinConnection)

    expect(pageOfCmrResultsMock).toBeCalledTimes(1)
  })
})
