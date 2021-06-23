import fetch from 'node-fetch'

/**
 * Fetch a single collection from CMR search
 * @param {String} conceptId Collection concept id from CMR
 * @param {String} token An optional Echo Token
 * @returns [{JSON}] An array of UMM JSON collection results
 */
export const fetchCmrCollection = async (conceptId, token) => {
  const requestHeaders = {}

  if (token) {
    requestHeaders['Echo-Token'] = token
  }

  const response = await fetch(`${process.env.CMR_ROOT}/search/collections.umm_json?concept_id=${conceptId}`, {
    method: 'GET',
    headers: requestHeaders
  })
    .then((request) => request.json())
    .catch((error) => {
      console.error(`Could not complete request due to error: ${error}`)
      return null
    })

  return response
}
