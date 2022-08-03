/**
 * Delete the acls with the given name from graph db
 * @param {String} name acl name from CMR for catelog item identity type acls
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @returns {Boolean} true if deletion was successful or false if unsuccessful
 */
export const deleteAcl = async (conceptId, gremlinConnection) => {
  try {
    await gremlinConnection
      .V()
      .has('acl', 'id', conceptId)
      .drop()
      .next()
  } catch (error) {
    console.log(`Error deleting acl with conceptId: [${conceptId}]: ${error.message}`)

    return false
  }
  console.log(`Deleted acl of name [${conceptId}] from graph db`)

  return true
}
