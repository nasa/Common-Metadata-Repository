import 'array-foreach-async'

import axios from 'axios'

import { SQSClient, SendMessageBatchCommand } from '@aws-sdk/client-sqs'

import { chunkArray } from '../chunkArray'
import indexCmrCollections from '../../indexCmrCollection/handler'

let sqs

/**
 * Fetch a page of collections from CMR search endpoint and initiate or continue search-after request
 * @param {Object} param0 The parameter object
 * @param {String} param0.searchAfter An optional search-after given from the CMR
 * @param {String} param0.token An optional Echo Token
 * @param {Gremlin Traversal Object} param0.gremlinConnection connection to gremlin server
 * @param {String} param0.providerId CMR provider id whose collections to bootstrap, null means all providers.
 * @param {Integer} param0.searchAfterNum searchAfter number parameter used for logging the current iteration of the CMR searchAfter
 * @returns null
 */
export const fetchPageFromCMR = async ({
  searchAfter,
  token,
  gremlinConnection,
  providerId,
  searchAfterNum = 0
}) => {
  const requestHeaders = {}

  console.log(`Fetch collections from CMR, searchAfter #${searchAfterNum}`)

  if (token) {
    requestHeaders.Authorization = token
  }

  if (searchAfter) {
    requestHeaders['CMR-Search-After'] = searchAfter
  }

  let fetchUrl = `${process.env.CMR_ROOT}/search/collections.json?page_size=${process.env.PAGE_SIZE}`

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
    const { 'cmr-search-after': cmrsearchAfter } = headers

    const { feed = {} } = data
    const { entry = [] } = feed

    const chunkedItems = chunkArray(entry, 10)

    if (chunkedItems.length > 0) {
      const { env: { IS_LOCAL } } = process

      await chunkedItems.forEachAsync(async (chunk) => {
        if (IS_LOCAL === 'true') {
          const queueBody = chunk.map((collection) => {
            const { id: conceptId, revision_id: revisionId } = collection

            return {
              body: JSON.stringify({
                'concept-id': conceptId,
                'revision-id': revisionId,
                action: 'concept-update'
              })
            }
          })

          await indexCmrCollections({ Records: queueBody })
        } else {
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
        }
      })
    }

    // If we have an active searchAfter and there are more results
    if (cmrsearchAfter && entry.length === parseInt(process.env.PAGE_SIZE, 10)) {
      await fetchPageFromCMR({
        searchAfter: cmrsearchAfter,
        token,
        gremlinConnection,
        providerId,
        searchAfterNum: (searchAfterNum + 1)
      })
    }
  } catch (e) {
    console.log(`Could not complete request due to error: ${e}`)
  }
}
