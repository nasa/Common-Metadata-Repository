const gremlin = require('gremlin')

// eslint-disable-next-line no-underscore-dangle
const __ = gremlin.process.statics

/**
 * Given a RelatedUrl object, Gremlin connection, and
 * associated collection, index related URL and build
 * relationships for any GENERAL DOCUMENTATION that exists
 * in common with other nodes in the graph database
 * @param {JSON} relatedUrl a list of RelatedUrls
 * @param {Connection} g a connection to the gremlin server
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @returns null
 */
exports.indexRelatedUrl = async (relatedUrl, g, dataset, conceptId) => {
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
    const documentationVertex = await g
      .V()
      .hasLabel('documentation')
      .has('name', url)
      .fold()
      .coalesce(
        __.unfold(),
        g.addV('documentation').property('name', url).property('title', description || subType)
      )
      .next()

    const { value: { id: documentationId } } = documentationVertex

    // Use `fold` and `coalesce` the same as above, but to
    // create an edge between this url and its parent collection
    await g
      .V(documentationId)
      .out('documents')
      .hasId(dataset)
      .fold()
      .coalesce(
        __.unfold(),
        g.V(documentationId).addE('documents').to(g.V(dataset))
      )
      .next()
  } catch (error) {
    console.log(`ERROR indexing RelatedUrl for concept [${conceptId}] ${JSON.stringify(relatedUrl)}: \n Error: ${error}`)
  }
}
