import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

/**
 * Given a project object, Gremlin connection, and associated collection, index campaign and build relationships for any GENERAL campaign that exists in common with other nodes in the graph database
 * @param {JSON} project UMM project object
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @returns null
 */
const indexCampaign = async (project, gremlinConnection, dataset, conceptId) => {
  // CMR UMM-C actually put the campaign info in the Projects ShortName field rather than the Campaigns field for historical reasons
  const {
    ShortName: campaign
  } = project

  try {
    // Use `fold` and `coalesce` to check existance of vertex, and create one if none exists.
    const campaignVertex = await gremlinConnection
      .V()
      .has('campaign', 'name', campaign)
      .fold()
      .coalesce(
        gremlinStatistics.unfold(),
        gremlinConnection.addV('campaign').property('name', campaign)
      )
      .next()

    const { value: vertexValue = {} } = campaignVertex
    const { id: campaignId } = vertexValue

    console.log(`campaign vertex [${campaignId}] indexed for collection [${dataset}]`)

    // Create an edge between this campaign and its parent collection
    const campaignEdge = await gremlinConnection
      .V(campaignId).as('c')
      .V(dataset)
      .coalesce(
        gremlinStatistics.outE('includedIn').where(gremlinStatistics.inV().as('c')),
        gremlinConnection.addE('includedIn').to('c')
      )
      .next()

    const { value: edgeValue = {} } = campaignEdge
    const { id: edgeId } = edgeValue

    console.log(`campaign edge [${edgeId}] indexed to point to collection [${dataset}]`)
  } catch (error) {
    console.log(`ERROR indexing campaign for concept [${conceptId}] ${JSON.stringify(project)}: \n Error: ${error}`)
  }
}

export default indexCampaign
