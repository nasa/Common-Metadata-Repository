import nock from 'nock'

import { indexCmrCollection } from '../indexCmrCollection'
// import { initializeGremlinConnection } from '../../gremlin/initializeGremlinConnection'

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
      const mockAclResponse = {
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
        .reply(200, mockAclResponse)

      // Provide `null` for the gremlin connection to throw an error
      // const result = await indexCmrCollection(collectionObj, [], null)

      // expect(result).toBeFalsy()
      await expect(
        indexCmrCollection(collectionObj, [], null)
      ).rejects.toThrow('throwing error in indexCmrCollection maximum attempts reached')
      // expect(result.toThrow(Error))
      // await expect(indexCmrCollection(collectionObj, [], null)).rejects.toThrow('some error')
      // expect(() => { indexCmrCollection(collectionObj, [], null) }).toThrow(TypeError)
      // Retry policy in place. Called 5 times using three different logs, so 12 total calls TODO use this after test, for now leave it off for 2
      expect(consoleMock).toBeCalledTimes(11)

      // Error message logged because deleteCmrCollection failed because of null gremlinConnection
      expect(consoleMock.mock.calls[0][0]).toEqual(`Error deleting project vertices only linked to collection [${conceptId}]: Cannot read properties of null (reading 'V')`)

      // Error message logged because addV failed because of null gremlinConnection
      expect(consoleMock.mock.calls[1][0]).toEqual(`Error indexing collection into graph database [${conceptId}]: Cannot read properties of null (reading 'addV'), retrying attempt #[1]`)
      // Function is being called recursively
      // expect(consoleMock.mock.calls[2][0]).toEqual(`Retrying the lambda function to index the graph database for [${conceptId}] attempt #1`)
      // expect(result.toThrow(Error))
    })
  })
  // Keep retry retry policy out for now
  // describe('When an exception is thrown and retrying begins', () => {
  //   test('test retry to index a collection if there is a failure such as a Concurrent Modification Error', async () => {
  //     const gremlinConnection = initializeGremlinConnection()
  //     const collectionObj = {
  //       meta: {
  //         'concept-id': 'C1000000-CMR'
  //       },
  //       umm: {
  //         EntryTitle: 'entryTitle',
  //         DOI: {
  //           DOI: 'doiDescription'
  //         },
  //         Projects: 'projects',
  //         Platforms: 'platforms',
  //         RelatedUrls: 'relatedUrls',
  //         ShortName: 'shortName'
  //       }
  //     }
  //     // depth defaults to 0
  //     const result = await indexCmrCollection(collectionObj, ['guest'], gremlinConnection)
  //   })
  // })
})
