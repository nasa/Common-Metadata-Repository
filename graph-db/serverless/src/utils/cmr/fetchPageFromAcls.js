import 'array-foreach-async'

import axios from 'axios'

import { SQSClient, SendMessageBatchCommand } from '@aws-sdk/client-sqs'

import { chunkArray } from '../chunkArray'

let sqs
/**
 * Fetch acls from the access control application
 * @param {String} token An optional Echo Token
 * @returns [{JSON}] An array of UMM JSON collection results
 */
export const fetchPageFromAcls = async ({
  searchAfter,
  token,
  gremlinConnection,
  searchAfterNum = 0
}) => {
  const requestHeaders = {}

  console.log(`Fetch ACLs from CMR, searchAfter #${searchAfterNum}`)

  if (token) {
    requestHeaders.Authorization = `Bearer ${token}`
  }

  if (searchAfter) {
    requestHeaders['CMR-Search-After'] = searchAfter
  }

  const fetchUrl = `${process.env.CMR_ROOT}/access-control/acls?identity_type=catalog_item`

  try {
    if (sqs == null) {
      sqs = new SQSClient()
    }

    const cmrAcls = await axios({
      url: fetchUrl,
      method: 'GET',
      headers: requestHeaders
    })

    const { data, headers } = cmrAcls
    const { 'cmr-search-after': cmrsearchAfter } = headers

    const { items = [] } = data

    const chunkedItems = chunkArray(items, 10)

    if (chunkedItems.length > 0) {
      await chunkedItems.forEachAsync(async (chunk) => {
        const sqsEntries = []

        chunk.forEach((collection) => {
          const { concept_id: conceptId } = collection

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

    // If we have an active searchAfter and there are more results
    if (cmrsearchAfter && items.length === parseInt(process.env.PAGE_SIZE, 10)) {
      await fetchPageFromAcls({
        searchAfter: cmrsearchAfter,
        token,
        gremlinConnection,
        searchAfterNum: (searchAfterNum + 1)
      })
    }
  } catch (e) {
    console.log(`Could not complete request due to error: ${e}`)
  }
}
