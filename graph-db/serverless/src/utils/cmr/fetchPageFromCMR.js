import AWS from 'aws-sdk'
import fetch from 'node-fetch'

// AWS SQS adapter
let sqs

/**
 * Fetch a page of collections from CMR search endpoint and initiate or continue scroll request
 * @param {String} scrollId An optional scroll-id given from the CMR
 * @param {String} token An optional Echo Token
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @param {String} providerId CMR provider id whose collections to bootstrap, null means all providers.
 * @returns [{JSON}] An array of UMM JSON collection results
 */
export const fetchPageFromCMR = async (scrollId, token, gremlinConnection, providerId, scrollNum = 0) => {
  const requestHeaders = {}

  if (sqs == null) {
    sqs = new AWS.SQS(getSqsConfig())
  }

  console.log(`Fetch collections from CMR, scroll #${scrollNum}`)

  if (token) {
    requestHeaders['Echo-Token'] = token
  }

  if (scrollId) {
    requestHeaders['CMR-Scroll-Id'] = scrollId
  }

  let fetchUrl = `${process.env.CMR_ROOT}/search/collections.umm_json?page_size=${process.env.PAGE_SIZE}&scroll=true`

  if (providerId !== null) {
    fetchUrl += `&provider=${providerId}`
  }

  try {
    const cmrCollections = await fetch(fetchUrl, {
      method: 'GET',
      headers: requestHeaders
    })

    const { 'cmr-scroll-id': cmrScrollId } = cmrCollections.headers.raw()

    const collectionsJson = await cmrCollections.json()

    const { items = [] } = collectionsJson

    items.forEachAsync((collection) => {
      const { meta } = collection
      const { 'concept-id': conceptId, 'revision-id': revisionId } = meta

      await sqs.sendMessage({
        QueueUrl: process.env.COLLECTION_INDEXING_QUEUE_URL,
        MessageBody: JSON.stringify({
          action: 'concept-update',
          'concept-id': conceptId,
          'revision-id': revisionId
        })
      }).promise()
    })

    // If we have an active scrollId and there are more results
    if (cmrScrollId && items.length === parseInt(process.env.PAGE_SIZE, 10)) {
      await fetchPageFromCMR(cmrScrollId, token, gremlinConnection, providerId, (scrollNum + 1))
    }
  } catch (e) {
    console.error(`Could not complete request due to error: ${e}`)
  }

  return scrollId
}
