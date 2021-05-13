require('array-foreach-async')
const { harvestCmrCollections } = require('./cmr/harvestCmrCollections')
const { initializeGremlinConnection } = require('./gremlin/initializeGremlinConnection')
const { indexCmrCollection } = require('./indexing/indexCmrCollection')

/**
 * Harvests collections from CMR and loads them into graph db
 * @returns {Array} Errors from indexing into graph db
 */
exports.bootstrapGremilinServer = async () => {
  const partitionedSearchResults = await harvestCmrCollections()
  const gremlin = initializeGremlinConnection()
  const indexingStatuses = []

  for await (const partition of partitionedSearchResults) {
    console.log(`Indexing [${partition.length}] items into graph db`)
    const indexingStatus = partition.map(
      async (result) => {
        const indexedCollection = await indexCmrCollection(result, gremlin)
        return indexedCollection
      }
    )
    indexingStatuses.push(indexingStatus)
  }

  return indexingStatuses
}
