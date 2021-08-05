// eslint-disable-next-line import/no-extraneous-dependencies
import nock from 'nock'

import indexCmrCollection from '../indexCmrCollection/handler'

const campaign = (shortName) => ({
  ShortName: shortName
})

const instrument = (shortName) => ({
  ShortName: shortName
})

// Returns the UMM Platforms JSON for the given attributes in the format of
// {platform: platformName, instruments: [instrumentName ...]}
const platformInstruments = (attributes) => {
  const { platform, instruments } = attributes
  let instrumentObjs

  if (instruments) {
    instrumentObjs = instruments.map(instrument)
  }

  return {
    ShortName: platform,
    Instruments: instrumentObjs
  }
}

const relatedUrlObj = (relatedUrl) => ({
  URLContentType: 'PublicationURL',
  Type: 'VIEW RELATED INFORMATION',
  Subtype: 'GENERAL DOCUMENTATION',
  URL: relatedUrl
})

/**
 * create/update the collection with given concept id, dataset title and documentation name
 * @param {String} conceptId Collection concept id from CMR
 * @param {String} datasetTitle Entry Title of the collection which becomes the title of dataset vertex
 * @param {JSON} attributes a map of field value pairs of attributes to update the collection
 * @returns null
 */
export const updateCollection = async (conceptId, datasetTitle, attributes) => {
  const {
    relatedUrls, campaigns, platforms, doi
  } = attributes

  let projects
  let platformInstrumentObjs
  let relatedUrlObjs
  let doiValue = { MissingReason: 'Not Applicable' }

  if (campaigns) {
    projects = campaigns.map(campaign)
  }

  if (platforms) {
    platformInstrumentObjs = platforms.map(platformInstruments)
  }

  if (relatedUrls) {
    relatedUrlObjs = relatedUrls.map(relatedUrlObj)
  }

  if (doi) {
    doiValue = { DOI: doi }
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
            Projects: projects,
            Platforms: platformInstrumentObjs,
            RelatedUrls: relatedUrlObjs,
            DOI: doiValue,
            ShortName: 'latent-reserves-in-the-swiss-nfi',
            EntryTitle: datasetTitle
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
 * delete the collection with given concept id, dataset title and documentation name
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
