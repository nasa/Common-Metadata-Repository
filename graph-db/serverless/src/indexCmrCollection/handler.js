import 'array-foreach-async'

import { deleteCmrCollection } from '../utils/cmr/deleteCmrCollection'
import { fetchCmrCollection } from '../utils/cmr/fetchCmrCollection'
import { fetchCollectionPermittedGroups } from '../utils/cmr/fetchCollectionPermittedGroups'
import { getConceptType } from '../utils/cmr/getConceptType'
import { getEchoToken } from '../utils/cmr/getEchoToken'
import { indexCmrCollection } from '../utils/cmr/indexCmrCollection'
import { initializeGremlinConnection } from '../utils/gremlin/initializeGremlinConnection'

let gremlinConnection
let token

const updateActionType = 'concept-update'
const deleteActionType = 'concept-delete'

const indexCmrCollections = async (event) => {
  // Prevent creating more tokens than necessary
  if (token === undefined) {
    token = await getEchoToken()
  }

  // Prevent connecting to Gremlin more than necessary
  if (!gremlinConnection) {
    gremlinConnection = initializeGremlinConnection()
  }

  let recordCount = 0
  let skipCount = 0

  const { Records: updatedConcepts } = event

  await updatedConcepts.forEachAsync(async (message) => {
    const { body } = message

    const { 'concept-id': conceptId, 'revision-id': revisionId, action } = JSON.parse(body)

    if (!['collection'].includes(getConceptType(conceptId))) {
      console.log(`Concept [${conceptId}] was not a collection and will not be indexed`)

      return
    }

    if (action !== updateActionType && action !== deleteActionType) {
      console.log(`Action [${action}] was unsupported for concept [${conceptId}]`)

      return
    }

    if (getConceptType(conceptId) === 'collection' && action === updateActionType) {
      try {
        const collection = await fetchCmrCollection(conceptId, token)

        const { data = {} } = collection
        const { items = [] } = data

        if (items.length === 0) {
          console.log(`Skip indexing of collection [${conceptId}] as it is not found in CMR`)

          skipCount += 1
        } else {
          const [firstCollection] = items

          // Fetch the permitted groups for this collection from access-control
          const groupList = await fetchCollectionPermittedGroups(conceptId, token)

          console.log(`Start indexing concept [${conceptId}], revision-id [${revisionId}]`)

          await indexCmrCollection(firstCollection, groupList, gremlinConnection)

          recordCount += 1
        }
      } catch (e) {
        console.log('Collection FAILED during indexing process there may be an issue with the collection verify that the collection for the given env: ', conceptId)
        console.log('Error indexing collection, Execption was thrown: ', e)
      }
    }
    if (getConceptType(conceptId) === 'collection' && action === deleteActionType) {
      console.log(`Start deleting concept [${conceptId}], revision-id [${revisionId}]`)

      await deleteCmrCollection(conceptId, gremlinConnection)

      recordCount += 1
    }
  })

  let body = `Successfully indexed ${recordCount} collection(s).`

  if (skipCount > 0) {
    body += ` Skipped ${skipCount} collection(s).`
  }

  return {
    isBase64Encoded: false,
    statusCode: 200,
    body
  }
}

export default indexCmrCollections
