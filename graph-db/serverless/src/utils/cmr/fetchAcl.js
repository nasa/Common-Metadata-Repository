import axios from 'axios'

/**
 * Fetch a single acl from CMR access control
 * @param {String} conceptId acl concept id from CMR
 * @param {String} token An optional Echo Token
 * @returns [{JSON}] TODO What schema is this data in
 */
export const fetchAcl = async (conceptId, token) => {
  const requestHeaders = {}

  if (token) {
    requestHeaders['Echo-Token'] = token
  }

  let response
  try {
    response = await axios({
      url: `${process.env.CMR_ROOT}/access-control/acls/${conceptId}`,
      method: 'GET',
      headers: requestHeaders,
      json: true
    })
  } catch (error) {
    console.log(`Could not complete request due to error: ${error}`)

    return null
  }

  return response
}
