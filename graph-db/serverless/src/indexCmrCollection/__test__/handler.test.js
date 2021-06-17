import nock from 'nock'

import indexCmrCollection from '../handler'

import { verifyGraphDb } from '../../testUtil/verifyGraphDb'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('indexCmrCollection handler', () => {
  test('test index of single collection', async () => {
    nock(/local-cmr/)
      .get(/search/)
      .reply(200,
        {
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
        })

    const event = { Records: [{ body: '{"concept-id": "C1237293909-TESTPROV", "action": "concept-update" }' }] }

    const indexed = await indexCmrCollection(event)

    const { body, statusCode } = indexed

    expect(body).toBe('Successfully indexed 1 collection(s)')
    expect(statusCode).toBe(200)

    await verifyGraphDb("'Latent reserves' within the Swiss NFI", 'https://en.wikipedia.org/wiki/Buffalo_buffalo_Buffalo_buffalo_buffalo_buffalo_Buffalo_buffalo')
  })

  test('test unsupported event type', async () => {
    const event = { Records: [{ body: '{"concept-id": "C1237293909-TESTPROV", "action": "concept-delete" }' }] }

    const consoleMock = jest.spyOn(console, 'log')

    const indexed = await indexCmrCollection(event)

    expect(consoleMock).toBeCalledTimes(1)
    expect(consoleMock).toBeCalledWith('Action [concept-delete] was unsupported for concept [C1237293909-TESTPROV]')

    const { body, statusCode } = indexed

    expect(body).toBe('Successfully indexed 0 collection(s)')
    expect(statusCode).toBe(200)
  })

  test('test unsupported concept type', async () => {
    const event = { Records: [{ body: '{"concept-id": "G1237293909-TESTPROV", "action": "concept-update" }' }] }

    const consoleMock = jest.spyOn(console, 'log')

    const indexed = await indexCmrCollection(event)

    expect(consoleMock).toBeCalledTimes(1)
    expect(consoleMock).toBeCalledWith('Concept [G1237293909-TESTPROV] was not a collection and will not be indexed')

    const { body, statusCode } = indexed

    expect(body).toBe('Successfully indexed 0 collection(s)')
    expect(statusCode).toBe(200)
  })
})
