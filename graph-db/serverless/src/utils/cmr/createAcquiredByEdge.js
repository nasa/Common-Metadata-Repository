import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

/**
 * create the acquiredBy edge between platform/instrument and collection
 * @param {String} piId the id of the platformInstrument vertex
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @returns null
 */
export const createAcquiredByEdge = async (piId, gremlinConnection, dataset) => {
  // Create an edge between the given platform and the collection
  let platformEdge
  try {
    platformEdge = await gremlinConnection
      .V(piId).as('p')
      .V(dataset)
      .coalesce(
        gremlinStatistics.outE('acquiredBy').where(gremlinStatistics.inV().as('p')),
        gremlinConnection.addE('acquiredBy').to('p')
      )
      .next()
  } catch (error) {
    console.error(`ERROR creating acquiredBy edge: ${error}`)
  }

  const { value: edgeValue = {} } = platformEdge
  const { id: edgeId } = edgeValue

  console.log(`acquiredBy edge [${edgeId}] indexed to point to collection [${dataset}]`)
}
