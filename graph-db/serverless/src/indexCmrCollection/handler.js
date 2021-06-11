const { indexCmrCollection } = require('../../../commonUtils/indexing/indexCmrCollection')
const { initializeGremlinConnection } = require('../../../commonUtils/gremlin/initializeGremlinConnection')
const { fetchCmrCollection } = require('../../../commonUtils/cmr/fetchCmrCollection')
const { getEchoToken } = require('../../../commonUtils/cmr/getEchoToken')
const { getConceptType } = require('../utils/getConceptType')

module.exports.indexCmrCollection = async (event) => {
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
  const gremlin = await initializeGremlinConnection()

  const collection = await fetchCmrCollection(conceptId, token)
  const { items } = collection
  const indexedSuccessfully = await indexCmrCollection(items[0], gremlin)

  return {
    statusCode: 200,
    body: `Collection [${conceptId}] indexed sucessfully: ${indexedSuccessfully}`
  }
}
