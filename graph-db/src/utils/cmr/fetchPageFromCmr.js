const fetch = require('node-fetch')
const { getEchoToken } = require('./getEchoToken')

/**
 * fetchPageFromCMR: Fetch a page of collections from CMR
 * search endpoint and initiate or continue scroll request
 * @param scrollId {String} An optional scroll-id given from the CMR
 * @returns [{JSON}] An array of UMM JSON collection results
 */
exports.fetchPageFromCMR = async (scrollId) => {
  const token = await getEchoToken()
  const requestHeaders = {}

  if (token) {
    requestHeaders['Echo-Token'] = token
  }

  if (scrollId) {
    requestHeaders['CMR-Scroll-Id'] = scrollId
  }

  const response = await fetch(`${process.env.CMR_ROOT}/search/collections.umm_json?page_size=${process.env.PAGE_SIZE}&scroll=true`, {
    method: 'GET',
    headers: requestHeaders
  }).catch((error) => {
    console.log(`Could not complete request due to error: ${error}`)
    return null
  })

  return response
}
