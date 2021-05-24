const nock = require('nock')

const { fetchPageFromCMR } = require('../fetchPageFromCmr')

const OLD_ENV = process.env

beforeEach(() => {
  // Manage resetting ENV variables
  jest.resetModules()
  process.env = { ...OLD_ENV }
  delete process.env.NODE_ENV

  nock.cleanAll()
})

afterEach(() => {
  // Restore any ENV variables overwritten in tests
  process.env = OLD_ENV
})

describe('fetchPageFromCMR', () => {
  test('Empty page', async () => {
    process.env.PAGE_SIZE = 0
    process.env.IS_LOCAL = true
    process.env.CMR_ROOT = 'http://localhost'
    const mockedBody = {
      hits: 16996,
      took: 5,
      items: []
    }

    nock(/localhost/).get(/search/).reply(200, mockedBody)

    const response = await fetchPageFromCMR(null, null).then((res) => res.json())
    expect(response).toEqual(mockedBody)
  })

  test('Single result', async () => {
    process.env.PAGE_SIZE = 1
    process.env.IS_LOCAL = true
    process.env.CMR_ROOT = 'http://localhost'
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

    nock(/localhost/).get(/search/).reply(200, mockedBody)

    const response = await fetchPageFromCMR(null, null).then((res) => res.json())
    expect(response).toEqual(mockedBody)
  })

  test('Single result with mocked ECHO token', async () => {
    process.env.IS_LOCAL = false
    process.env.CMR_ROOT = 'http://localhost'

    process.env.PAGE_SIZE = 1
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

    nock(/localhost/).get(/search/).reply(200, mockedBody)

    const response = await fetchPageFromCMR('fake-scroll-id', 'SUPER-SECRET-TOKEN').then((res) => res.json())
    expect(response).toEqual(mockedBody)
  })

  test('Invalid concept-id', async () => {
    process.env.PAGE_SIZE = 2
    process.env.IS_LOCAL = true
    process.env.CMR_ROOT = 'http://localhost'
    const mockedBody = {
      errors: ["Invalid concept_id [C1234-PROV1]! I can't believe you've done this"]
    }

    nock(/localhost/).get(/search/).reply(400, mockedBody)

    const response = await fetchPageFromCMR(null, null).then((res) => res.json())
    expect(response).toEqual(mockedBody)
  })
})
