import axios from 'axios'
import {fetchAccessControlList} from './fetchAccessControlList'
import indexAcl from '../../indexCollectionAcl/handler'

/**
 * Fetch acls from the access control application
 * @param {String} token An optional Echo Token
 * @returns [{JSON}] An array of UMM JSON collection results
 */
export const fetchCollectionAcls = async (token) => 
{
  const requestHeaders = {}

  if (token) {
    requestHeaders['Echo-Token'] = token
  }

  let response
  try { //TODO the url had to be altered to point to local CMR will need to add back in access-control
    response = await axios({
      url: `${process.env.CMR_ROOT}/acls?identity_type=catalog_item`,
      method: 'GET',
      headers: requestHeaders,
    })
  } catch (error) {
    console.log(`${process.env.CMR_ROOT}/acls?identity_type=catalog_item`)
    console.log(requestHeaders)
    console.log(`Could not complete request due to error: ${error}`)

    return null
  }
  const { data, headers } = response
  const { items = {} } = data
  let aclId
  let revId
  // For each of the acl concept_ids pass it as an event to the indexAcl handler function
  await items.forEachAsync(async (item) => {
    aclId = item.concept_id
    console.log('new run with', aclId);
    revId = item.revision_id
    console.log(revId);
    //const acl = await fetchAccessControlList(aclId, token)
    // const { data:aclData } = acl
    // console.log(aclData);
    await indexAcl({ 'concept-id': aclId, 'revision-id': revId, 'action':'concept-update'}) //What are these args?
    // We need to pass this off to the handler as an event so it can index it
    // for that we need to create an event that will do that
    //const indAcl = await indexCollectionAcl(aclId,aclData,token)

  })
  return response
}
