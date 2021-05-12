const nock = require('nock')
const { harvestCmrCollections } = require('../harvestCmrCollections')
let { fetchPageFromCMR } = require('../fetchPageFromCmr')
const { expect } = require('@jest/globals')

process.env.IS_LOCAL = true
process.env.CMR_ROOT = 'http://localhost'

describe('harvestCmrCollections', () => {
    test('Empty page', async () => {
        process.env.PAGE_SIZE = 1
        const mockedBody = {
            'hits': 16996,
            'took': 5,
            'items': []
        }

        nock(/localhost/)
            .get(/search/)
            .reply(200, mockedBody)

        const response = await harvestCmrCollections()

        // no search results, and no scroll id
        expect(response).toEqual([[], null])
    })

    test('Errors', async () => {
        process.env.PAGE_SIZE = 2
        const mockedBody = {
            "errors": ["Invalid concept_id [C1234-PROV1]! I can't believe you've done this >:( ."]
        }

        nock(/localhost/).get(/search/).reply(400, mockedBody)

        const response = await harvestCmrCollections()
        expect(response).toEqual([undefined, null])
    })
})