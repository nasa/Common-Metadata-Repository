// eslint-disable-next-line import/no-extraneous-dependencies
import nock from 'nock'

import indexCmrCollection from '../indexCmrCollection/handler'

// Helper to create a Project object simular to the one returned by CMR
const projectObj = (shortName) => ({
  ShortName: shortName
})

// Helper to create a Instrument object simular to the one returned by CMR
const instrumentObj = (shortName) => ({
  ShortName: shortName
})

// Helper to create a Platforms object simular to the one returned by CMR
// {platform: platformName, instruments: [instrumentName ...]}
const platformInstrumentsObj = (attributes) => {
  const { platform, instruments } = attributes

  const returnObj = {
    ShortName: platform
  }

  if (instruments) {
    returnObj.Instruments = instruments.map(instrumentObj)
  }

  return returnObj
}

// Helper to create a RelatedUrl object simular to the one returned by CMR
const relatedUrlObj = (relatedUrl) => ({
  URLContentType: 'PublicationURL',
  Type: 'VIEW RELATED INFORMATION',
  Subtype: 'GENERAL DOCUMENTATION',
  URL: relatedUrl
})

/**
 * create/update the collection with given concept id, collection title and relatedUrl name
 * @param {String} conceptId Collection concept id from CMR
 * @param {String} collectionTitle Entry Title of the collection which becomes the title of collection vertex
 * @param {JSON} attributes a map of field value pairs of attributes to update the collection
 * @returns null
 */
export const updateCollection = async (conceptId, collectionTitle, attributes) => {
  const { relatedUrls, projects, platforms } = attributes

  let projectsObjs
  let platformInstrumentObjs
  let relatedUrlsObjs

  if (projects) {
    projectsObjs = projects.map(projectObj)
  }

  if (platforms) {
    platformInstrumentObjs = platforms.map(platformInstrumentsObj)
  }

  if (relatedUrls) {
    relatedUrlsObjs = relatedUrls.map(relatedUrlObj)
  }

  nock(/local-cmr/)
    .get(/search/)
    .reply(200,
      {
        hits: 16996,
        took: 5,
        items: [{
          meta: {
            'concept-id': conceptId,
            'provider-id': 'TESTPROV'
          },
          umm: {
            Projects: projectsObjs,
            Platforms: platformInstrumentObjs,
            RelatedUrls: relatedUrlsObjs,
            DOI: {
              DOI: 'doi:10.16904/envidat.166'
            },
            ShortName: 'latent-reserves-in-the-swiss-nfi',
            EntryTitle: collectionTitle
          }
        }]
      })

  const event = { Records: [{ body: `{"concept-id": "${conceptId}", "action": "concept-update" }` }] }

  const indexed = await indexCmrCollection(event)

  const { body, statusCode } = indexed

  expect(body).toBe('Successfully indexed 1 collection(s).')
  expect(statusCode).toBe(200)
}

/**
 * delete the collection with given concept id, collection title and relatedUrl name
 * @param {String} conceptId Collection concept id from CMR
 * @returns null
 */
export const deleteCollection = async (conceptId) => {
  const event = { Records: [{ body: `{"concept-id": "${conceptId}", "action": "concept-delete" }` }] }

  const indexed = await indexCmrCollection(event)

  const { body, statusCode } = indexed

  expect(body).toBe('Successfully indexed 1 collection(s).')
  expect(statusCode).toBe(200)
}

export const deleteCollectionWithError = async () => {
  const event = { Records: [{ body: '{"concept-id": "console.log(\'i think i broke it\')", "action": "concept-delete" }' }] }

  const indexed = await indexCmrCollection(event)

  const { body, statusCode } = indexed

  expect(body).toBe('Successfully indexed 0 collection(s).')
  expect(statusCode).toBe(200)
}
