import gremlin from 'gremlin'
import 'array-foreach-async'

const gremlinStatistics = gremlin.process.statics
const { P: { lte } } = gremlin.process

/**
 * Delete the collection with the given concept id from graph db
 * @param {String} conceptId Collection concept id from CMR
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @param {String} vertexLabel Label of the linked vertex
 * @param {String} edgeName Name of the edge between the dataset vertex and its linked vertex
 * @returns
 */
const deleteLinkedVertices = async (conceptId, gremlinConnection, vertexLabel, edgeName) => {
  try {
    await gremlinConnection
      .V()
      .has('dataset', 'concept-id', conceptId)
      .outE(edgeName)
      .inV()
      .where(gremlinStatistics.inE(edgeName).count().is(lte(1)))
      .drop()
      .next()
  } catch (error) {
    console.error(`Error deleting ${vertexLabel} vertices only linked to collection [${conceptId}]: ${error.message}`)

    return false
  }

  return true
}
/**
 * Delete the collection with the given concept id from graph db
 * @param {String} conceptId Collection concept id from CMR
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @returns
 */
export const deleteCmrCollection = async (conceptId, gremlinConnection) => {
  // drop all the campaign vertices that are connected to and only connected to the dataset vertex
  let success
  success = await deleteLinkedVertices(conceptId, gremlinConnection, 'campaign', 'includedIn')
  if (success === false) {
    return false
  }

  // drop all the documentation vertices that are connected to and only connected to the dataset vertex
  success = await deleteLinkedVertices(conceptId, gremlinConnection, 'documentation', 'documentedBy')
  if (success === false) {
    return false
  }

  // delete the dataset vertex
  try {
    await gremlinConnection
      .V()
      .has('dataset', 'concept-id', conceptId)
      .drop()
      .next()
  } catch (error) {
    console.error(`Error deleting dataset vertex for collection [${conceptId}]: ${error.message}`)

    return false
  }

  console.log(`Deleted collection [${conceptId}] from graph db`)

  return true
}
