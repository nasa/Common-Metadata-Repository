/**
 * Given a RelatedUrl object, Gremlin connection, and
 * associated collection, index related URL and build
 * relationships for any GENERAL DOCUMENTATION that exists
 * in common with other nodes in the graph database
 * @param {JSON} relatedUrl a list of RelatedUrls
 * @param {Connection} gremlin a connection to the gremlin server
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @returns null
 */
exports.indexRelatedUrl = async (relatedUrl, gremlin, dataset, conceptId) => {
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
    const documentationVertexExists = await gremlin.V().hasLabel('documentation').has('name', url).hasNext()
    let documentationVertex

    if (documentationVertexExists) {
      documentationVertex = await gremlin.V().hasLabel('documentation').has('name', url).next()
    } else {
      documentationVertex = await gremlin.addV('documentation').property('name', url).property('title', description || subType).next()
    }

    const { value: { id: documentationId } } = documentationVertex
    gremlin.V(documentationId).addE('documents').to(gremlin.V(dataset)).iterate()
  } catch (error) {
    console.log(`ERROR indexing RelatedUrl for concept [${conceptId}] ${JSON.stringify(relatedUrl)}: \n Error: ${error}`)
  }
}
