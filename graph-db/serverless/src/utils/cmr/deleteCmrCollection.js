import { deleteLinkedVertices } from './deleteLinkedVertices'

/**
 * Delete the collection with the given concept id from graph db
 * @param {String} conceptId Collection concept id from CMR
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @returns
 */
export const deleteCmrCollection = async (conceptId, gremlinConnection) => {
  let success

  // drop all the project vertices that are connected to and only connected to the collection vertex
  success = await deleteLinkedVertices(conceptId, gremlinConnection, 'project', 'includedIn')
  if (success === false) {
    return false
  }

  // drop all the platformInstrument vertices that are connected to and only connected to the collection vertex
  success = await deleteLinkedVertices(conceptId, gremlinConnection, 'platformInstrument', 'acquiredBy')
  if (success === false) {
    return false
  }

  // drop all the relatedUrl vertices that are connected to and only connected to the collection vertex
  success = await deleteLinkedVertices(conceptId, gremlinConnection, 'relatedUrl', 'linkedBy')
  if (success === false) {
    return false
  }

  // delete the collection vertex
  try {
    await gremlinConnection
      .V()
      .has('collection', 'id', conceptId)
      .drop()
      .next()
  } catch (error) {
    console.log(`Error deleting collection vertex for collection [${conceptId}]: ${error.message}`)

    return false
  }

  console.log(`Deleted collection [${conceptId}] from graph db`)

  return true
}
