const { indexCmrCollection } = require('../../../commonUtils/indexing/indexCmrCollection')
const { initializeGremlinConnection } = require('../../../commonUtils/gremlin/initializeGremlinConnection')
const { fetchCmrCollection } = require('../../../commonUtils/cmr/fetchCmrCollection')
const { getEchoToken } = require('../../../commonUtils/cmr/getEchoToken')
const { getConceptType } = require('../utils/getConceptType')

module.exports.indexCmrCollection = async (event) => {
  const { Records: [{ body }] } = event
  const { 'concept-id': conceptId } = JSON.parse(body)

  console.log(`Got concept-id: [${conceptId}]`)

  if (getConceptType(conceptId) !== 'collection') {
    return {
      statusCode: 200,
      body: `Concept [${conceptId}] was not a collection and will not be indexed`
    }
  }

  const token = await getEchoToken()
  const collection = await fetchCmrCollection(conceptId, token)
  const gremlin = await initializeGremlinConnection()

  const { items } = collection
  const indexedSucessfully = await indexCmrCollection(items[0], gremlin)

  return {
    statusCode: 200,
    body: `Collection [${conceptId}] indexed sucessfully: ${indexedSucessfully}`
  }
}
