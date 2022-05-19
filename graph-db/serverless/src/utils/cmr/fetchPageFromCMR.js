import 'array-foreach-async'

import axios from 'axios'

import { SQSClient, SendMessageBatchCommand } from '@aws-sdk/client-sqs'

import { chunkArray } from '../chunkArray'

let sqs

/**
 * Fetch a page of collections from CMR search endpoint and initiate or continue scroll request
 * @param {Object} param0 The parameter object
 * @param {String} param0.scrollId An optional scroll-id given from the CMR
 * @param {String} param0.token An optional Echo Token
 * @param {Gremlin Traversal Object} param0.gremlinConnection connection to gremlin server
 * @param {String} param0.providerId CMR provider id whose collections to bootstrap, null means all providers.
 * @param {Integer} param0.scrollNum Scroll number parameter used for logging the current iteration of the CMR scroll
 * @returns {String} CMR scroll id if more results available
 */
export const fetchPageFromCMR = async ({
  scrollId,
  token,
  gremlinConnection,
  providerId,
  scrollNum = 0
}) => {
  const requestHeaders = {}

  console.log(`Fetch collections from CMR, scroll #${scrollNum}`)

  if (token) {
    requestHeaders['Echo-Token'] = token
  }

  if (scrollId) {
    requestHeaders['CMR-Scroll-Id'] = scrollId
  }

  let fetchUrl = `${process.env.CMR_ROOT}/search/collections.json?page_size=${process.env.PAGE_SIZE}&scroll=true`

  if (providerId !== null) {
    fetchUrl += `&provider=${providerId}`
  }

  try {
    if (sqs == null) {
      sqs = new SQSClient()
    }

    const cmrCollections = await axios({
      url: fetchUrl,
      method: 'GET',
      headers: requestHeaders
    })

    const { data, headers } = cmrCollections
    const { 'cmr-scroll-id': cmrScrollId } = headers

    const { feed = {} } = data
    const { entry = [] } = feed

    const chunkedItems = chunkArray(entry, 10)

    if (chunkedItems.length > 0) {
      await chunkedItems.forEachAsync(async (chunk) => {
        const sqsEntries = []

        chunk.forEach((collection) => {
          const { id: conceptId } = collection

          sqsEntries.push({
            Id: conceptId,
            MessageBody: JSON.stringify({
              action: 'concept-update',
              'concept-id': conceptId
            })
          })
        })

        const command = new SendMessageBatchCommand({
          QueueUrl: process.env.COLLECTION_INDEXING_QUEUE_URL,
          Entries: sqsEntries
        })

        await sqs.send(command)
      })
    }

    // If we have an active scrollId and there are more results
    if (cmrScrollId && entry.length === parseInt(process.env.PAGE_SIZE, 10)) {
      await fetchPageFromCMR({
        scrollId: cmrScrollId,
        token,
        gremlinConnection,
        providerId,
        scrollNum: (scrollNum + 1)
      })
    }

    return cmrScrollId
  } catch (e) {
    console.log(`Could not complete request due to error: ${e}`)
    return null
  }
}
