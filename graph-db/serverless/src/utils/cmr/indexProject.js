import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

/**
 * Given a project object, Gremlin connection, and associated collection, index project and build relationships for any GENERAL project that exists in common with other nodes in the graph database
 * @param {JSON} project UMM project object
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @returns null
 */
const indexProject = async (project, gremlinConnection, dataset, conceptId) => {
  // CMR UMM-C actually put the project info in the Projects ShortName field rather than the Projects field for historical reasons
  const {
    ShortName: shortName
  } = project

  try {
    // Use `fold` and `coalesce` to check existance of vertex, and create one if none exists.
    const projectVertex = await gremlinConnection
      .V()
      .has('project', 'name', shortName)
      .fold()
      .coalesce(
        gremlinStatistics.unfold(),
        gremlinConnection.addV('project').property('name', shortName)
      )
      .next()

    const { value: vertexValue = {} } = projectVertex
    const { id: projectId } = vertexValue

    console.log(`project vertex [${projectId}] indexed for collection [${dataset}]`)

    // Create an edge between this project and its parent collection
    const projectEdge = await gremlinConnection
      .V(projectId).as('c')
      .V(dataset)
      .coalesce(
        gremlinStatistics.outE('includedIn').where(gremlinStatistics.inV().as('c')),
        gremlinConnection.addE('includedIn').to('c')
      )
      .next()

    const { value: edgeValue = {} } = projectEdge
    const { id: edgeId } = edgeValue

    console.log(`project edge [${edgeId}] indexed to point to collection [${dataset}]`)
  } catch (error) {
    // Log specific error message, but throw error again to stop indexing
    console.error(`ERROR indexing project for concept [${conceptId}] ${JSON.stringify(project)}: \n Error: ${error}`)

    throw error
  }
}

export default indexProject
