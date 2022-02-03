import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

/**
 * Given a RelatedUrl object, Gremlin connection, and associated collection, index related URL and build relationships for any GENERAL DOCUMENTATION that exists in common with other nodes in the graph database
 * @param {JSON} relatedUrl a list of RelatedUrls
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} collection the parent collection vertex in the gremlin server
 * @returns null
 */
export const indexRelatedUrl = async (
  relatedUrl,
  gremlinConnection,
  collection,
  conceptId
) => {
  const {
    URL: url,
    Type: type,
    Subtype: subtype,
    Description: description
  } = relatedUrl

  try {
    const addVCommand = gremlinConnection.addV('relatedUrl')
      .property('url', url)
      .property('type', type)

    if (description) {
      addVCommand.property('description', description)
    }

    if (subtype) {
      addVCommand.property('subtype', subtype)
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

    console.log(`RelatedUrl vertex [${relatedUrlId}] indexed for collection [${collection}]`)

    // Create an edge between this url and its parent collection
    const relatedUrlEdge = await gremlinConnection
      .V(relatedUrlId).as('d')
      .V(collection)
      .coalesce(
        gremlinStatistics.outE('linkedBy').where(gremlinStatistics.inV().as('d')),
        gremlinConnection.addE('linkedBy').to('d')
      )
      .next()

    const { value: edgeValue = {} } = relatedUrlEdge
    const { id: edgeId } = edgeValue

    console.log(`RelatedUrl edge [${edgeId}] indexed to point to collection [${collection}]`)
  } catch (error) {
    // Log useful information pertaining to the error
    console.log(`Failed to index RelatedUrl for concept [${conceptId}] ${JSON.stringify(relatedUrl)}`)

    // Log the error
    console.log(error)

    // Re-throw the error
    throw error
  }
}
