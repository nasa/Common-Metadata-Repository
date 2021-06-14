const gremlin = require('gremlin')

const gremlinStatistics = gremlin.process.statics

/**
 * Given a RelatedUrl object, Gremlin connection, and
 * associated collection, index related URL and build
 * relationships for any GENERAL DOCUMENTATION that exists
 * in common with other nodes in the graph database
 * @param {JSON} relatedUrl a list of RelatedUrls
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @returns null
 */
exports.indexRelatedUrl = async (relatedUrl, gremlinConnection, dataset, conceptId) => {
  const {
    Subtype: subType,
    URL: url,
    Description: description,
    URLContentType: urlContentType
  } = relatedUrl

  if (!subType
    || !url
    || urlContentType !== 'PublicationURL') {
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

    const { value: { id: documentationId } } = documentationVertex

    console.log(`Documentation vertex [${documentationId}] indexed for collection [${dataset}]`)

    // Use `fold` and `coalesce` the same as above, but to
    // create an edge between this url and its parent collection
    const documentationEdge = await gremlinConnection
      .V(dataset).as('d')
      .V(documentationId)
      .coalesce(
        gremlinStatistics.outE('documents').where(gremlinStatistics.inV().as('d')),
        gremlinConnection.addE('documents').to('d')
      )
      .next()

    const { value: { id: edgeId } } = documentationEdge

    console.log(`Documentation edge [${edgeId}] indexed to point to collection [${dataset}]`)
  } catch (error) {
    console.log(`ERROR indexing RelatedUrl for concept [${conceptId}] ${JSON.stringify(relatedUrl)}: \n Error: ${error}`)
  }
}
