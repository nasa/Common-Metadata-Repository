import fetch from 'node-fetch'

import { indexPageOfCmrResults } from './indexPageOfCmrResults'

/**
 * Fetch a page of collections from CMR search endpoint and initiate or continue scroll request
 * @param {String} scrollId An optional scroll-id given from the CMR
 * @param {String} token An optional Echo Token
 * @returns [{JSON}] An array of UMM JSON collection results
 */
export const fetchPageFromCMR = async (scrollId, token, gremlineConnection) => {
  const requestHeaders = {}

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

    const { headers = {} } = cmrCollections;

    // eslint-disable-next-line no-param-reassign
    ({ 'CMR-Scroll-Id': scrollId } = headers)

    const collectionsJson = await cmrCollections.json()

    const { items = [] } = collectionsJson

    await indexPageOfCmrResults(items, gremlineConnection)

    // If we have an active scrollId and there are more results
    if (scrollId && items.length === parseInt(process.env.PAGE_SIZE, 10)) {
      fetchPageFromCMR(scrollId, token, gremlineConnection)
    }
  } catch (e) {
    console.log(`Could not complete request due to error: ${e}`)
  }

  return scrollId
}
