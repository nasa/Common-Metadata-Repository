import gremlin from 'gremlin'
import 'array-foreach-async'

const gremlinStatistics = gremlin.process.statics
const { P: { lte } } = gremlin.process

/**
 * Delete the collection with the given concept id from graph db
 * @param {String} conceptId Collection concept id from CMR
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @returns
 */
export const deleteCmrCollection = async (conceptId, gremlinConnection) => {
  // drop all the documentation vertices that are connected to and only connected to the dataset vertex
  try {
    await gremlinConnection
      .V()
      .has('dataset', 'concept-id', conceptId)
      .inE('documents')
      .outV()
      .where(gremlinStatistics.outE('documents').count().is(lte(1)))
      .drop()
      .next()
  } catch (error) {
    console.log(`Error deleting documentation vertices only linked to collection [${conceptId}]: ${error.message}`)

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
    console.log(`Error deleting dataset vertex for collection [${conceptId}]: ${error.message}`)

    return false
  }

  console.log(`Deleted collection [${conceptId}] from graph db`)

  return true
}
