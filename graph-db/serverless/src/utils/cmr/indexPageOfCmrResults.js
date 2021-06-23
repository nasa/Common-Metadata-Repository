import 'array-foreach-async'

import { indexCmrCollection } from './indexCmrCollection'

/**
 * Helper function to iterate through CMR search results and index into a Graph database
 * @param {JSON} results
 */
export const indexPageOfCmrResults = async (results, gremlin) => {
  await results.forEachAsync(async (result) => {
    try {
      await indexCmrCollection(result, gremlin)
    } catch (error) {
      // If possible, log the error with the concept id for troubleshooting purposes
      const { meta } = result
      const { 'concept-id': conceptId } = meta

      console.error(`Could not index concept [${conceptId}] into Graph database due to error: ${error}`)
    }
  })
}
