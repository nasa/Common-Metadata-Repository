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
    Subtype: subType,
    URL: url,
    Description: description,
    URLContentType: urlContentType
  } = relatedUrl

  if (!subType || !url || urlContentType !== 'PublicationURL') {
    // We only care about documentation at the moment.
    // Checking the URLContentType is the most efficient way to find its type.
    // Return early if it isn't some kind of documentation.
    return
  }

  try {
    // Use `fold` and `coalesce` to check existance of vertex, and create one if none exists.
    const documentationVertex = await gremlinConnection
      .V()
      .has('documentation', 'name', url)
      .fold()
      .coalesce(
        gremlinStatistics.unfold(),
        gremlinConnection.addV('documentation').property('name', url).property('title', description || subType)
      )
      .next()

    const { value: vertexValue = {} } = documentationVertex
    const { id: documentationId } = vertexValue

    console.log(`Documentation vertex [${documentationId}] indexed for collection [${dataset}]`)

    // Create an edge between this url and its parent collection
    const documentationEdge = await gremlinConnection
      .V(documentationId).as('d')
      .V(dataset)
      .coalesce(
        gremlinStatistics.outE('documentedBy').where(gremlinStatistics.inV().as('d')),
        gremlinConnection.addE('documentedBy').to('d')
      )
      .next()

    const { value: edgeValue = {} } = documentationEdge
    const { id: edgeId } = edgeValue

    console.log(`Documentation edge [${edgeId}] indexed to point to collection [${dataset}]`)
  } catch (error) {
    // Log specific error message, but throw error again to stop indexing
    console.error(`ERROR indexing RelatedUrl for concept [${conceptId}] ${JSON.stringify(relatedUrl)}: \n Error: ${error}`)
    throw error
  }
}

export default indexRelatedUrl
