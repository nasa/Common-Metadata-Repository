import fetch from 'node-fetch'

export const fetchCmrCollection = async (conceptId, token) => {
  const requestHeaders = {}

  if (token) {
    requestHeaders['Echo-Token'] = token
  }

  const response = await fetch(`${process.env.CMR_ROOT}/search/collections.umm_json?concept_id=${conceptId}`, {
    method: 'GET',
    headers: requestHeaders
  }).then((request) => request.json())
    .catch((error) => {
      console.log(`Could not complete request due to error: ${error}`)
      return null
    })

  return response
}
