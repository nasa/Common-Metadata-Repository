import gremlin from 'gremlin'

const { statics: gremlinStatistics, P } = gremlin.process
const { lte } = P

/**
 * Delete the collection with the given concept id from graph db
 * @param {String} conceptId Collection concept id from CMR
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @param {String} vertexLabel Label of the linked vertex
 * @param {String} edgeName Name of the edge between the collection vertex and its linked vertex
 * @returns
 */
export const deleteLinkedVertices = async (conceptId, gremlinConnection, vertexLabel, edgeName) => {
  try {
    await gremlinConnection
      .V()
      .has('collection', 'id', conceptId)
      .outE(edgeName)
      .inV()
      .where(gremlinStatistics.inE(edgeName).count().is(lte(1)))
      .drop()
      .next()
  } catch (error) {
    console.log(`Error deleting ${vertexLabel} vertices only linked to collection [${conceptId}]: ${error.message}`)

    return false
  }

  return true
}
