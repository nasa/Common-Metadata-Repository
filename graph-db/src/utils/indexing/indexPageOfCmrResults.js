const { indexCmrCollection } = require('./indexCmrCollection')

/**
 * Helper function to iterate through CMR search results and index into
 * a Graph database
 * @param {JSON} results
 * @param {Traversal} gremlinConnection
 */
exports.indexPageOfCmrResults = async (results, gremlinConnection) => {
  results.map(async (result) => {
    try {
      await indexCmrCollection(result, gremlinConnection)
    } catch (error) {
      // If possible, log the error with the concept id for troubleshooting purposes
      const { meta } = result
      const { 'concept-id': conceptId } = meta
      console.warn(`Could not index concept [${conceptId}] into Graph database due to error: ${error}`)
    }
  })
}
