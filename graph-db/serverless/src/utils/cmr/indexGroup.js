import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

/**
 * Given a project object, Gremlin connection, and associated collection, index project and build relationships for any GENERAL project that exists in common with other nodes in the graph database
 * @param {JSON} groupPermissions the group permissions of the acl in the form {permissions[], groupId}
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} aclId the id of the acl in the graphDB. Id referes to the graphDB's vertex id NOT a concept id
 * @returns null
 */
export const indexGroup = async (groupPermissions, gremlinConnection, aclId) => {
  console.log('Reached the indexGroup function');
  console.log('This is a seperate iteration', groupPermissions);
  console.log(aclId);
  const {
    permissions: permissions,
    group_id: group_id
  } = groupPermissions
  console.log(permissions)
  console.log('The group_id for this permission', group_id)

  if (!group_id) {
    console.log('This was a user_type not a group', groupPermissions)
    return false
  }
  console.log('This is not a user_type, it had a group id so we will continue to parse it')
  try {
    // Use `fold` and `coalesce` to check existance of vertex, and create one if none exists.
    // permissions is a property of the group vertex; it is a list
    const groupVertex = await gremlinConnection
      .V()
      .has('group', 'id', group_id)
      .fold()
      .coalesce(
        gremlinStatistics.unfold(),
        gremlinConnection.addV('group')
        .property('id', group_id)
        .property('permissions', [permissions])
      )
      .next()

    const { value: vertexValue = {} } = groupVertex
    const { id: groupId } = vertexValue

    console.log(`group vertex [${groupId}] indexed for acl [${aclId}]`)
    
    // The permissions of an existing group could be updated by the acl event
    const upPermsGroupVertex = await gremlinConnection
      .V()
      .has('group', 'id', group_id)
      .property('permissions', permissions)
      .next()
      // TODO get the id of the property itself for better logging
    const { value: upPermValue = {} } = upPermsGroupVertex
    const { id: upGroupId } = upPermValue
    console.log(`group vertex it's permissions have been updated [${upGroupId}]`)

    // Create an edge between this group and its linked access control list
     const groupEdge = await gremlinConnection
     .V().hasLabel('acl').has('id',aclId).as('c')
     .V(groupId)
      .coalesce(
        gremlinStatistics.outE('accessControlledBy').where(gremlinStatistics.inV().as('c')),
        gremlinConnection.addE('accessControlledBy').to('c')
      )
      .next()
    
    const { value:edgeValue } = groupEdge
    console.log(edgeValue)
    console.log(`group edge [${edgeValue}] indexed to point to acl [${aclId}]`)
    let success = 'successfuly indexed group'
    console.log(success);
    return success
  
  } catch (error) {
    // Log useful information pertaining to the error
    console.log(`Failed to index group for acl [${aclId}] ${JSON.stringify(groupPermissions)}`)

    // Log the error
    console.log(error)

    // Re-throw the error
    throw error
  }
}