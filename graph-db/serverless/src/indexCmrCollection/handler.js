import { fetchCmrCollection } from '../utils/cmr/fetchCmrCollection'
import { getConceptType } from '../utils/cmr/getConceptType'
import { getEchoToken } from '../utils/cmr/getEchoToken'
import { indexCmrCollection } from '../utils/cmr/indexCmrCollection'
import { initializeGremlinConnection } from '../utils/gremlin/initializeGremlinConnection'

const indexCmrCollections = async (event) => {
  const { Records: [{ body }] } = event
  const { 'concept-id': conceptId, action } = JSON.parse(body)

  console.log(`Got event: [${body}]`)

  if (getConceptType(conceptId) !== 'collection') {
    return {
      statusCode: 200,
      body: `Concept [${conceptId}] was not a collection and will not be indexed`
    }
  }

  if (action !== 'concept-update') {
    return {
      statusCode: 200,
      body: `Action [${action}] was unsupported for concept [${conceptId}]`
    }
  }

  const token = await getEchoToken()
  const gremlin = initializeGremlinConnection()

  const collection = await fetchCmrCollection(conceptId, token)
  const { items } = collection
  const indexedSuccessfully = await indexCmrCollection(items[0], gremlin)

  return {
    statusCode: 200,
    body: `Collection [${conceptId}] indexed sucessfully: ${indexedSuccessfully}`
  }
}

export default indexCmrCollections
