import 'array-foreach-async'
import gremlin from 'gremlin'

import { getEchoToken } from '../utils/cmr/getEchoToken'
import { initializeGremlinConnection } from '../utils/gremlin/initializeGremlinConnection'
import { indexCollectionAcl } from '../utils/cmr/indexCollectionAcl'
import { deleteCollectionAcl } from '../utils/cmr/deleteCollectionAcl'
import { fetchAccessControlList } from '../utils/cmr/fetchAccessControlList'
const gremlinStatistics = gremlin.process.statics //Used for anonymous traversals commonly imported as __
let gremlinConnection
let token

const updateActionType = 'concept-update'
const deleteActionType = 'concept-delete'

const indexAcl = async (event) => {
    // Prevent creating more tokens than necessary
  if (token === undefined) {
      token = await getEchoToken()
  }
    // Prevent connecting to Gremlin more than necessary
  if (!gremlinConnection) {
      gremlinConnection = initializeGremlinConnection()
  }
  const { 'concept-id': conceptId, 'revision-id': revisionId, 'action':action } = event
  console.log(conceptId)
  console.log(revisionId)
  console.log(action)
  const acl =  await fetchAccessControlList(conceptId,token)

  // If the fetch response is null because the acl was not in cmr
  if (!acl) {
    console.log(`Skip indexing of acl [${conceptId}] as it is not found in CMR`)
    // skipCount += 1
    return false
  }
  const { data:aclData } = acl
  const { items } = aclData

  console.log('data in indexCollectionAcl', aclData);
  console.log('items in indexCollectionAcl',items);

  // if action was neither update and it was also not delete
  if (action !== updateActionType && action !== deleteActionType) {
    console.log(`Action [${action}] was unsupported for concept [${conceptId}]`)
    return false
  }

  if (action === updateActionType) {
    await indexCollectionAcl(conceptId, aclData, gremlinConnection)

  }
  else if(action === deleteActionType) {
    await deleteCollectionAcl(conceptId,token)
  }

  console.log("Completed an acl event");
  let body = `Successfully indexed ${conceptId} acls(s).`

   return {
    isBase64Encoded: false,
    statusCode: 200,
    body
  }
}
export default indexAcl