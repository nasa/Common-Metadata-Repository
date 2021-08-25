import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

/**
 * Given a RelatedUrl object, Gremlin connection, and associated collection, index related URL and build relationships for any GENERAL DOCUMENTATION that exists in common with other nodes in the graph database
 * @param {JSON} relatedUrl a list of RelatedUrls
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @returns null
 */
const indexRelatedUrl = async (relatedUrl, gremlinConnection, dataset, conceptId) => {
  const {
    URL: url,
    Type: type,
    SubType: subType,
    Description: description
  } = relatedUrl

  if (!url || type !== 'VIEW RELATED INFORMATION') {
    // Ignore non related information related urls for now
    return
  }

  try {
    const addVCommand = gremlinConnection.addV('relatedUrl')
      .property('url', url)
      .property('type', type)
      .property('subType', subType)

    if (description) {
      addVCommand.property('description', description)
    }

    // Use `fold` and `coalesce` to check existance of vertex, and create one if none exists.
    const relatedUrlVertex = await gremlinConnection
      .V()
      .has('relatedUrl', 'url', url)
      .fold()
      .coalesce(
        gremlinStatistics.unfold(),
        addVCommand
      )
      .next()

    const { value: vertexValue = {} } = relatedUrlVertex
    const { id: relatedUrlId } = vertexValue

    console.log(`RelatedUrl vertex [${relatedUrlId}] indexed for collection [${dataset}]`)

    // Create an edge between this url and its parent collection
    const relatedUrlEdge = await gremlinConnection
      .V(relatedUrlId).as('d')
      .V(dataset)
      .coalesce(
        gremlinStatistics.outE('linkedBy').where(gremlinStatistics.inV().as('d')),
        gremlinConnection.addE('linkedBy').to('d')
      )
      .next()

    const { value: edgeValue = {} } = relatedUrlEdge
    const { id: edgeId } = edgeValue

    console.log(`RelatedUrl edge [${edgeId}] indexed to point to collection [${dataset}]`)
  } catch (error) {
    // Log specific error message, but throw error again to stop indexing
    console.error(`ERROR indexing RelatedUrl for concept [${conceptId}] ${JSON.stringify(relatedUrl)}: \n Error: ${error}`)

    throw error
  }
}

export default indexRelatedUrl
