import gremlin from 'gremlin'
const gremlinStatistics = gremlin.process.statics
// Helper function to test indexing groups which requires an acl in graphDb
export const updateAcl = async (concept_id) => {
  try {
    console.log('I am calling the updateAcl function here');
    const addVCommand = global.testGremlinConnection.addV('acl').property('id',concept_id)
    addVCommand.property('legacyGuid', 'guid1234').property('name','aclsName')
    // Use `fold` and `coalesce` to check existance of vertex, and create one if none exists.
    aclVertex = await global.testGremlinConnection
    .V()
    .hasLabel('acl')
    .has('id', concept_id)
    .fold()
    .coalesce(
    gremlinStatistics.unfold(),
    addVCommand
    )
    .next()
  } catch (error) {
    console.log(`Error inserting acl node in test util for [${concept_id}]: ${error.message}`)
    console.log(error)
    return false
  }
  const { value = {} } = aclVertex
  const { id: aclId } = value
  return aclId; // return aclId
}