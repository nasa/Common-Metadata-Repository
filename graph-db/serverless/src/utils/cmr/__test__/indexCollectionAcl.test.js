import { indexCollectionAcl } from '../indexCollectionAcl'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('utils#indexCollectionAcl', () => {
  describe('when an exception is thrown', () => {
    test('it is caught and logged', async () => {
    const consoleMock = jest.spyOn(console, 'log')
    
    // Name has to be explicity declared since it is passed into function and returned in the error output
    const name = 'aclCollectionName'

    const aclObj =  { catalog_item_identity: {
      name: name,
      provider_id: 'providerId',
      collection_identifier: 'collectionIdentifier'
    },
    group_permissions: 'groupPermissions',
    legacy_guid: 'legacyGuid' 
  }
    // Provide `null` for the gremlin connection to throw an error
    const result = await indexCollectionAcl(aclObj, null)

    expect(result).toBeFalsy() // The promise will be false

    // Error message logged because deleteCollectionAcl failed because of null gremlinConnection
    // [0][0] refers to the first error returned from the first function being called in indexCollectionAcl which was deleteCollectionAcl
    expect(consoleMock.mock.calls[0][0]).toEqual(`Error deleting collection acl with name: [${name}]: Cannot read properties of null (reading 'V')`)
    // Error message logged because addV failed because of null gremlinConnection
    // [1][0] refers to the second argument returned from calling the indexCollectionAcl function which is from trying to add the acl vertex
    expect(consoleMock.mock.calls[1][0]).toEqual(`Error inserting acl node [${name}]: Cannot read properties of null (reading 'addV')`)
    })
  })

  /*describe('If collectionAcls runs correctly', () => {
    test('CollectionAcl test runs with good input', async () => {
      const aclObj = {"group_permissions":[{
        "permissions":["read", "order", "update"],"group_id":"AG1386452130-CMR"},
        {"permissions":["read"],"group_id":"AG1907590288-CMR"}],
        "catalog_item_identity":{"name":"AQUARIUS Public Collections",
        "provider_id":"NSIDC_ECS",
        "granule_applicable":false,
        "collection_applicable":true,
        "collection_identifier":{"concept_ids":["C1567691449-NSIDC_ECS","C1543171968-NSIDC_ECS","C1529467648-NSIDC_ECS","C1567692285-NSIDC_ECS","C1529467467-NSIDC_ECS","C1567691895-NSIDC_ECS","C1529467866-NSIDC_ECS","C1529467762-NSIDC_ECS","C1529467710-NSIDC_ECS","C1529467499-NSIDC_ECS"]},
        "legacy_guid":"FBC44AB5-5E1A-C140-216C-5A4DD6D6F841"}}

        const { value = {} } = await indexCollectionAcl(aclObj, global.testGremlinConnection)
        const { id: aclId } = value
        console.log(aclId)    
    })
  })*/

})
