import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

/**
 * create the hasAccessTo edge between the acl vertex and the collection vertexes in collection item identifier list within catalog item acls
 * @param {String} collectionConceptId the item in the collection item identifier
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} aclId the value of the newely created acl that we are indexing
 * @returns null
 */
export const createHasAccessToEdge = async (collectionConceptId, gremlinConnection, aclId) => {
  // Create an edge between the given platform and the collection. use coalesce pattern to fist check if edge already exists
  let aclToCollectionEdge = null
  try {
    aclToCollectionEdge = await gremlinConnection
      .V().has('collection','id',collectionConceptId).as('p')
      .V(aclId)
      .coalesce(
        gremlinStatistics.outE('hasAccessTo').where(gremlinStatistics.inV().as('p')),
        gremlinConnection.addE('hasAccessTo').to('p')
      )
      .next()

    const { value: edgeValue = {} } = aclToCollectionEdge
    const { id: edgeId } = edgeValue

    console.log(`hasAccessTo edge [${edgeId}] indexed to point to collection [${collectionConceptId}]`)
  } catch (error) {
    console.log(`ERROR creating hasAccessTo edge: ${error} could not make edge to ${collectionConceptId}`)
  }
}
