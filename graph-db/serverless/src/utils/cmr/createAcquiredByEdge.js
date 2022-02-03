import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

/**
 * create the acquiredBy edge between platform/instrument and collection
 * @param {String} piId the id of the platformInstrument vertex
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} collection the parent collection vertex in the gremlin server
 * @returns null
 */
export const createAcquiredByEdge = async (piId, gremlinConnection, collection) => {
  // Create an edge between the given platform and the collection
  let platformEdge = null
  try {
    platformEdge = await gremlinConnection
      .V(piId).as('p')
      .V(collection)
      .coalesce(
        gremlinStatistics.outE('acquiredBy').where(gremlinStatistics.inV().as('p')),
        gremlinConnection.addE('acquiredBy').to('p')
      )
      .next()

    const { value: edgeValue = {} } = platformEdge
    const { id: edgeId } = edgeValue

    console.log(`acquiredBy edge [${edgeId}] indexed to point to collection [${collection}]`)
  } catch (error) {
    console.log(`ERROR creating acquiredBy edge: ${error}`)
  }
}
