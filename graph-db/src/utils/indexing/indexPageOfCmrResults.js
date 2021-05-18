const { indexCmrCollection } = require('./indexCmrCollection')
const { initializeGremlinConnection } = require('../gremlin/initializeGremlinConnection')

/**
 * Helper function to iterate through CMR search results and index into
 * a Graph database
 * @param {JSON} results
 */
exports.indexPageOfCmrResults = async (results) => {
  const gremlin = initializeGremlinConnection()
  results.map(async (result) => {
    try {
      await indexCmrCollection(result, gremlin)
    } catch (error) {
      // If possible, log the error with the concept id for troubleshooting purposes
      const { meta } = result
      const { 'concept-id': conceptId } = meta
      console.warn(`Could not index concept [${conceptId}] into Graph database due to error: ${error}`)
    }
  })
}
