import axios from 'axios'

/**
 * Fetch a single collection from CMR search
 * @param {String} conceptId Collection concept id from CMR
 * @param {String} token An optional Echo Token
 * @returns [{JSON}] An array of UMM JSON collection results
 */
export const fetchCmrCollection = async (conceptId, token) => {
  const requestHeaders = {}

  if (token) {
    requestHeaders.Authorization = token
  }

  let response
  try {
    response = await axios({
      url: `${process.env.CMR_ROOT}/search/collections.umm_json?concept_id=${conceptId}`,
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
