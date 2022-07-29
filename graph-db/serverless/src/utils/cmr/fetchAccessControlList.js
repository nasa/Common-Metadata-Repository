import axios from 'axios'
import indexAcl from '../../indexCollectionAcl/handler'
/**
 * Fetch a single acl from CMR access control
 * @param {String} conceptId acl concept id from CMR
 * @param {String} token An optional Echo Token
 * @returns [{JSON}] TODO What schema is this data in
 */
export const fetchAccessControlList = async (conceptId, token) => {
  const requestHeaders = {}

  if (token) {
    requestHeaders['Echo-Token'] = token
  }

  let response
  try {
    response = await axios({ // TODO url had to be altered for local dev path should include access-control for SIT
      url: `${process.env.CMR_ROOT}/acls/${conceptId}?include-full-acl=true`,
      method: 'GET',
      headers: requestHeaders,
      json: true
    })
    console.log(requestHeaders)
    console.log(`${process.env.CMR_ROOT}/acls/${conceptId}?include-full-acl=true`);
    const { data, headers } = response
    //await indexAcl(data) // Call the indexCollectionAcl handler and pass the data as an events
  
  } catch (error) {
    console.log(`Could not complete request due to error: ${error}`)
    return null
  }
  // Data is going to contain a specific record
  return response
}