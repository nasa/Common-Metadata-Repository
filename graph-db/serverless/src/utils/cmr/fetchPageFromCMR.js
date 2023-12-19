import 'array-foreach-async'

import axios from 'axios'

import { SQSClient, SendMessageBatchCommand } from '@aws-sdk/client-sqs'

import { chunkArray } from '../chunkArray'
import { getSqsConfig } from '../aws/getSqsConfig'

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
  searchAfterNum = 0,
  sqsEntryJobCount = 0
}) => {
  const requestHeaders = {}

  console.log(`Fetch collections from CMR, searchAfter #${searchAfterNum}`)
  console.log(`Total SQS queue job size #${sqsEntryJobCount}`)

  if (token) {
    requestHeaders.Authorization = token
  }

  if (searchAfter) {
    requestHeaders['CMR-Search-After'] = searchAfter
  }

  const { env } = process
  const { cmrRoot, pageSize } = env

  let fetchUrl = `${cmrRoot}/search/collections.umm_json?page_size=${pageSize}`

  if (providerId !== null) {
    fetchUrl += `&provider=${providerId}`
  }

  try {
    if (sqs == null) {
      sqs = new SQSClient(getSqsConfig())
    }

    // Send request to CMR
    const cmrCollections = await axios({
      url: fetchUrl,
      method: 'GET',
      headers: requestHeaders
    })

    // Iterate over CMR-pages
    const { data, headers } = cmrCollections
    const { 'cmr-search-after': cmrsearchAfter } = headers

    const { items = [] } = data

    let currentBatchSize = 0

    // Split page array into array with sub-arrays of size 10
    const chunkedItems = chunkArray(items, 10)

    if (chunkedItems.length > 0) {
      await chunkedItems.forEachAsync(async (chunk) => {
        const sqsEntries = []

        chunk.forEach((collection) => {
          const { meta } = collection
          const { 'concept-id': conceptId } = meta

          // TODO: Since we already made a call to CMR we may want to pass that information into the event
          // TODO: it is possible that by passing cmr data in the event the data could be stale by the time
          // the indexCollection handler picks it up to index it into the graphDb
          sqsEntries.push({
            Id: conceptId,
            MessageBody: JSON.stringify({
              action: 'concept-update',
              'concept-id': conceptId,
              collection
            })
          })

          // Retrieve the total number of collections being processed in here
          currentBatchSize += 1
        })

        const command = new SendMessageBatchCommand({
          QueueUrl: process.env.collectionIndexingQueueUrl,
          Entries: sqsEntries
        })

        await sqs.send(command)
      })
    }

    // If we have an active searchAfter so we aren't on the last page and there are more results
    // Second argument is to parse integers in base 10
    if (cmrsearchAfter && items.length === parseInt(pageSize, 10)) {
      // TODO: Investigate putting in a wait function between calls for each page to allow it to get picked
      // up by the other lambda and indexed into the graph database
      await fetchPageFromCMR({
        searchAfter: cmrsearchAfter,
        token,
        gremlinConnection,
        providerId,
        searchAfterNum: (searchAfterNum + 1),
        sqsEntryJobCount: (sqsEntryJobCount + currentBatchSize)
      })
    }
  } catch (error) {
    console.log(`Could not complete request due to an error requesting a page from CMR: ${error}`)
  }
}
