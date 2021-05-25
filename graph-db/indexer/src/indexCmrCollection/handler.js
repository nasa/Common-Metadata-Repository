const { indexCmrCollection } = require('../utils/indexing/indexCmrCollection')
const { initilizeGremlinConnection } = require('../utils/gremlin/initializeGremlinConnection')
const { fetchCmrCollection } = require('../utils/cmr/fetchCmrCollection')
const { getEchoToken } = require('../utils/cmr/getEchoToken')

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
