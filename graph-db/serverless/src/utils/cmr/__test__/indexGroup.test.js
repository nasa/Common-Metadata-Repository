import { updateCollection } from '../../../testUtil/indexCollection'
import { indexGroup } from '../indexGroup'
import { updateAcl } from '../../../testUtil/indexAcl'
// import { verifyCollectionPropertiesInGraphDb } from '../../testUtil/'
beforeEach(() => {
  jest.clearAllMocks()
})

describe('utils#indexGroup', () => {
  describe('when the group is indexed correctly into graphDB', () => {
    
  //   test('index the group', async () => {
  //     // Send a mock aclObj with relevent data into the index collection function
  //     // the index collection function executes indexGroup.js when there are groups
  //     const aclObj = {"group_permissions":[{
  //       "permissions":["read", "order", "update"],"group_id":"AG1386452130-CMR"},
  //       {"permissions":["read"],"group_id":"AG1907590288-CMR"}],
  //       "catalog_item_identity":{"name":"AQUARIUS Public Collections",
  //       "provider_id":"NSIDC_ECS",
  //       "granule_applicable":false,
  //       "collection_applicable":true,
  //       "collection_identifier":{"concept_ids":["C1567691449-NSIDC_ECS","C1543171968-NSIDC_ECS","C1529467648-NSIDC_ECS","C1567692285-NSIDC_ECS","C1529467467-NSIDC_ECS","C1567691895-NSIDC_ECS","C1529467866-NSIDC_ECS","C1529467762-NSIDC_ECS","C1529467710-NSIDC_ECS","C1529467499-NSIDC_ECS"]},
  //       "legacy_guid":"FBC44AB5-5E1A-C140-216C-5A4DD6D6F841"}}

  //       const { value = {} } = await indexCollectionAcl(aclObj, global.testGremlinConnection)
  //       const { id: aclId } = value
  //       console.log(aclId)
  //       //await indexGroup()
  //     /*const aclObj = { catalog_item_identity: {
  //       name:'name',
  //       provider_id: 'providerId',
  //       legacy_guid: 'legacyGuid',
  //       collection_identifier: 'collectionIdentifier'
  //     }
  //   }
  //   // Call the index collection to create an acl so we can retrieve the id to test if the group is connected to it
  //   const { value = {} } = await indexCollectionAcl(aclObj,global.testGremlinConnection)
  //   const { id: aclId } = value 
  //   console.log('This is the acl id in the indexGroup test', aclId);
  //   const groupPermissions = {permissions:["read", "order", "update"],group_id:"AG1386452130-CMR"}
  //   await indexGroup(groupPermissions,global.testGremlinConnection,aclId)
  //   })
  // })
  test('index the group but, groups are user_type', async () => {
    // Send a mock aclObj with relevent data into the index collection function
    // the index collection function executes indexGroup.js when there are groups
    let aclId = 1;
    const groupPerm = {"group_permissions":[{
      "permissions":["read", "order", "update"],"user_type":"AG1386452130-CMR"},
      {"permissions":["read"],"user_type":"AG1907590288-CMR"}]}
      //const group_permissions = {{"permissions":["read", "order", "update"],"group_id":"AG1386452130-CMR"},{"permissions":["read"],"group_id":"AG1907590288-CMR"}}
      const response = await indexGroup(groupPerm, global.testGremlinConnection, aclId)
      expect(response).toEqual(false)
  })
})
// I have an issue here because the Acl isn't actually in here
describe('When the group is indexed correctly', () => {
  test('index the group correctly', async () => {
    // Send a mock aclObj with relevent data into the index collection function
    // the index collection function executes indexGroup.js when there are groups
    let aclId = 'ACL1234' //First we have to create an 'acl' like structure to teset if the group actually connected
    const retaclId = await updateAcl(aclId)
    console.log(retaclId);
    const event = { group_id: 'AG1200000000-CMR', permissions: [ 'read', 'order' ]}
    //const grp = {[{"permissions":["read", "order", "update"],"group_id":"AG1386452130-CMR"},{"permissions":["read"],"group_id":"AG1907590288-CMR"}]}
    //const {'group_permissions': groupPermissions} = event
    
    // Call index acl to put in a n acl and get the value
    /*const groupPerm = {"group_permissions":[{
      "permissions":["read", "order", "update"],"group_id":"AG1386452130-CMR"},
      {"permissions":["read"],"group_id":"AG1907590288-CMR"}]}*/
    const response = await indexGroup(event, global.testGremlinConnection, aclId)
    const succcessfulRun = 'successfuly indexed group'
    expect(response).toEqual(succcessfulRun) // The return result from the function if the group indexed correctly
    })
  })
  describe('indexGroup has error due to bad graphDb connection', () => {
    test('Erros out due to bad gremlin connection handles errors from bad queries', async () => {
      // Send a mock aclObj with relevent data into the index collection function
      // the index collection function executes indexGroup.js when there are groups
      let aclId = 1;
      const groupPerm = {"group_permissions":[{
        "permissions":["read", "order", "update"],"user_type":"AG1386452130-CMR"},
        {"permissions":["read"],"user_type":"AG1907590288-CMR"}]}
        //const group_permissions = {{"permissions":["read", "order", "update"],"group_id":"AG1386452130-CMR"},{"permissions":["read"],"group_id":"AG1907590288-CMR"}}
        const response = await indexGroup(groupPerm, null, aclId)
        expect(response).toEqual(false)
    })
  })
})
