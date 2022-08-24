import nock from 'nock'

import { indexCmrCollection } from '../indexCmrCollection'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('utils#indexCmrCollection', () => {
  describe('when an exception is thrown', () => {
    test('it is caught and logged', async () => {
      const consoleMock = jest.spyOn(console, 'log')

      const conceptId = 'C1000000-CMR'

      const collectionObj = {
        meta: {
          'concept-id': conceptId
        },
        umm: {
          EntryTitle: 'entryTitle',
          DOI: {
            DOI: 'doiDescription'
          },
          Projects: 'projects',
          Platforms: 'platforms',
          RelatedUrls: 'relatedUrls',
          ShortName: 'shortName'
        }
      }
      const mockedBody = {
        items: [
          {
            concept_id: 'ACL1376510432-CMR',
            revision_id: 9,
            identity_type: 'Catalog Item',
            acl: {
              group_permissions: [],
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
              group_permissions: [],
              catalog_item_identity: [],
              legacy_guid: '78B9267A-8876-34B0-020D-2B62ED010C39'
            },
            name: 'IceBridge Public Collection',
            location: 'https://cmr.earthdata.nasa.gov:443/access-control/acls/ACL1374052769-CMR'
          }
        ]
      }
      nock(/local-cmr/)
        .get(/acls/)
        .reply(200, mockedBody)

      // Provide `null` for the gremlin connection to throw an error
      const result = await indexCmrCollection(collectionObj, [], null)

      expect(result).toBeFalsy()

      expect(consoleMock).toBeCalledTimes(2)

      // Error message logged because deleteCmrCollection failed because of null gremlinConnection
      expect(consoleMock.mock.calls[0][0]).toEqual(`Error deleting project vertices only linked to collection [${conceptId}]: Cannot read properties of null (reading 'V')`)

      // Error message logged because addV failed because of null gremlinConnection
      expect(consoleMock.mock.calls[1][0]).toEqual(`Error indexing collection [${conceptId}]: Cannot read properties of null (reading 'addV')`)
    })
  })
})
