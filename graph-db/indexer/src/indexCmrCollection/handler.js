const { indexCmrCollection } = require('../../../commonUtils/indexing/indexCmrCollection')
const { initilizeGremlinConnection } = require('../../../commonUtils/gremlin/initializeGremlinConnection')
const { fetchCmrCollection } = require('../../../commonUtils/cmr/fetchCmrCollection')
const { getEchoToken } = require('../../../commonUtils/cmr/getEchoToken')

module.exports.indexCmrCollection = async (event) => {
  const {
    body: {
      'concept-id': conceptId
    }
  } = event
  const token = await getEchoToken()
  const collection = await fetchCmrCollection(conceptId, token)
  const gremlin = initilizeGremlinConnection()

  const indexedSucessfully = await indexCmrCollection(collection, gremlin)

  return {
    statusCode: 200,
    body: `Collection [${conceptId}] indexed sucessfully: ${indexedSucessfully}`
  }
}
