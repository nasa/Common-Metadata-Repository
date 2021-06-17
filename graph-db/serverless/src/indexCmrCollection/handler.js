import { fetchCmrCollection } from '../utils/cmr/fetchCmrCollection'
import { getConceptType } from '../utils/cmr/getConceptType'
import { getEchoToken } from '../utils/cmr/getEchoToken'
import { indexCmrCollection } from '../utils/cmr/indexCmrCollection'
import { initializeGremlinConnection } from '../utils/gremlin/initializeGremlinConnection'

let gremlinConnection
let token

const indexCmrCollections = async (event) => {
  // Prevent creating more tokens than necessary
  if (!token) {
    token = await getEchoToken()
  }

  // Prevent connecting to Gremlin more than necessary
  if (!gremlinConnection) {
    gremlinConnection = initializeGremlinConnection()
  }

  let recordCount = 0

  const { Records: updatedConcepts = [] } = event

  await updatedConcepts.forEachAsync(async (message) => {
    const { body } = message

    const { 'concept-id': conceptId, 'revision-id': revisionId, action } = JSON.parse(body)

    if (getConceptType(conceptId) !== 'collection') {
      console.log(`Concept [${conceptId}] was not a collection and will not be indexed`)
    }

    if (action !== 'concept-update') {
      console.log(`Action [${action}] was unsupported for concept [${conceptId}]`)
    }

    if (getConceptType(conceptId) === 'collection' && action === 'concept-update') {
      console.log(`Start indexing concept [${conceptId}], revision-id [${revisionId}]`)

      const collection = await fetchCmrCollection(conceptId, token)

      const { items } = collection
      const [firstCollection] = items

      await indexCmrCollection(firstCollection, gremlinConnection)

      recordCount += 1
    }
  })

  return {
    isBase64Encoded: false,
    statusCode: 200,
    body: `Successfully indexed ${recordCount} collection(s)`
  }
}

export default indexCmrCollections
