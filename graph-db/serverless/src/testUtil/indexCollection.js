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
  URL: relatedUrl,
  Description: `Description for ${relatedUrl}`
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

  nock(/local-cmr/)
    .get(/acls/)
    .reply(200, {
      hits: 0,
      took: 6,
      items: [
        {
          concept_id: 'ACL1376510432-CMR',
          revision_id: 9,
          identity_type: 'Catalog Item',
          acl: {
            group_permissions: [{ permissions: ['read'], group_id: 'AG1337430038-NSIDC_ECS' },
              { permissions: ['read'], group_id: 'AG1337430039-NSIDC_ECS' },
              { permissions: ['read'], group_id: 'AG1337409405-CMR' },
              { permissions: ['read'], group_id: 'AG1386452130-CMR' },
              { permissions: ['read'], group_id: 'AG1337409406-CMR' },
              { permissions: ['read'], user_type: 'registered' },
              { permissions: ['read'], group_id: 'AG1337409407-CMR' }],
            catalog_item_identity: [],
            legacy_guid: '26B6710B-0562-953D-CCE7-E185B36A9545'
          },
          name: 'All Collections',
          location: 'https://cmr.earthdata.nasa.gov:443/access-control/acls/ACL1376510432-CMR'
        },
        {
          concept_id: 'ACL1374052769-CMR',
          revision_id: 81,
          identity_type: 'Catalog Item',
          acl: {
            group_permissions: [
              { permissions: ['read', 'order'], group_id: 'AG1337430038-NSIDC_ECS' },
              { permissions: ['read', 'order'], group_id: 'AG1337409405-CMR' },
              { permissions: ['read', 'order'], group_id: 'AG1337409406-CMR' },
              { permissions: ['read', 'order'], group_id: 'AG1337409413-CMR' },
              { permissions: ['read', 'order'], user_type: 'guest' },
              { permissions: ['read'], user_type: 'guest' },
              { permissions: ['read', 'order'], group_id: 'AG1337409411-CMR' }],
            catalog_item_identity: [],
            legacy_guid: '78B9267A-8876-34B0-020D-2B62ED010C39'
          },
          name: 'IceBridge Public Collection',
          location: 'https://cmr.earthdata.nasa.gov:443/access-control/acls/ACL1374052769-CMR'
        }
      ]
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
