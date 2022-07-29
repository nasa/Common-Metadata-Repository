
/**
 * Delete the acls with the given name from graph db
 * @param {String} name acl name from CMR for catelog item identity type acls
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @returns {Boolean} true if deletion was successful or false if unsuccessful
 */
export const deleteCollectionAcl = async (conceptId, gremlinConnection) => {
// We do NOT want to delete groups even though they were instantiated along with the Acls since the groups may be connected to other acls
  // delete the collection access control list
  let gremlinResult = null
  try {
    gremlinResult = await gremlinConnection
      .V()
      .has('acl', 'id', conceptId)
      .drop()
      .next()
  //TODO this function does not error out if there is no acl by that name
  //gremlin merely interprets it and does nothing with the name i.e. does not inform that the vertex doesn't exist
  } catch (error) {
    console.log(`Error deleting collection acl with conceptId: [${conceptId}]: ${error.message}`)
    return false
  }
  console.log(`Deleted collection acl of name [${conceptId}] from graph db`)
  return true
}