const { fetchPageFromCMR } = require('../fetchPageFromCmr')
const nock = require('nock')

// This must be set so '../getEchoToken doesn't
// attempt to use '../../getSecureParam'
process.env.IS_LOCAL = true
process.env.CMR_ROOT = 'http://localhost'

describe('fetchPageFromCMR', () => {
    test('Empty page', async () => {
        process.env.PAGE_SIZE = 0
        const mockedBody = {
            'hits': 16996,
            'took': 5,
            'items': []
        }

        nock(/localhost/)
            .get(/search/)
            .reply(200, mockedBody)

        const response = await fetchPageFromCMR().then(res => res.json())
        expect(response).toEqual(mockedBody)
    })

    test('Single result', async () => {
        process.env.PAGE_SIZE = 1
        const mockedBody = {
            'hits': 16996,
            'took': 5,
            'items': [{
                'meta': {
                    'concept-id': 'C1237293909-TESTPROV',
                    'provider-id': 'TESTPROV'
                },
                'umm': {
                    'RelatedUrls': [
                        {
                            'URLContentType': 'PublicationURL',
                            'Type': 'VIEW RELATED INFORMATION',
                            'Subtype': 'GENERAL DOCUMENTATION',
                            'URL': 'https://en.wikipedia.org/wiki/Buffalo_buffalo_Buffalo_buffalo_buffalo_buffalo_Buffalo_buffalo'
                        }
                    ],
                    'DOI': {
                        'DOI': 'doi:10.16904/envidat.166'
                    },
                    'ShortName': 'latent-reserves-in-the-swiss-nfi',
                    'EntryTitle': '\'Latent reserves\' within the Swiss NFI',
                }}]
        }

        nock(/localhost/)
            .get(/search/)
            .reply(200, mockedBody)

        const response = await fetchPageFromCMR().then(res => res.json())
        expect(response).toEqual(mockedBody)
    })
})