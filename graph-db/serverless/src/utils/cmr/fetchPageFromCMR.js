import fetch from 'node-fetch'

import { indexPageOfCmrResults } from './indexPageOfCmrResults'

let scrollNum = 0

/**
 * Fetch a page of collections from CMR search endpoint and initiate or continue scroll request
 * @param {String} scrollId An optional scroll-id given from the CMR
 * @param {String} token An optional Echo Token
 * @returns [{JSON}] An array of UMM JSON collection results
 */
export const fetchPageFromCMR = async (scrollId, token, gremlinConnection) => {
  const requestHeaders = {}

  scrollNum += 1
  console.log(`Fetch collections from CMR, scroll #${scrollNum}`)

  if (token) {
    requestHeaders['Echo-Token'] = token
  }

  if (scrollId) {
    requestHeaders['CMR-Scroll-Id'] = scrollId
  }

  try {
    const cmrCollections = await fetch(`${process.env.CMR_ROOT}/search/collections.umm_json?page_size=${process.env.PAGE_SIZE}&scroll=true`, {
      method: 'GET',
      headers: requestHeaders
    })

    const { 'cmr-scroll-id': cmrScrollId } = cmrCollections.headers.raw()

    const collectionsJson = await cmrCollections.json()

    const { items = [] } = collectionsJson

    await indexPageOfCmrResults(items, gremlinConnection)

    // If we have an active scrollId and there are more results
    if (cmrScrollId && items.length === parseInt(process.env.PAGE_SIZE, 10)) {
      await fetchPageFromCMR(cmrScrollId, token, gremlinConnection)
    }
  } catch (e) {
    console.error(`Could not complete request due to error: ${e}`)
  }

  return scrollId
}
