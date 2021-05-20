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
exports.indexRelatedUrl = async (relatedUrl, gremlin, dataset) => {
  const {
    Subtype: subType,
    URL: url,
    Description: description,
    URLContentType: urlContentType
  } = relatedUrl
  if (!!subType
    || urlContentType !== 'PublicationURL') {
    // We only care about documentation at the moment.
    // Checking the URLContentType is the most efficient way to find its type.
    // Return early if it isn't some kind of documentation.
    return
  }

  const documentationVertexExists = await gremlin.V().hasLabel('documentation').has('name', url).hasNext()
  let documentationVertex

  if (documentationVertexExists) {
    documentationVertex = await gremlin.V().hasLabel('documentation').has('name', url).next()
  } else {
    documentationVertex = await gremlin.addV('documentation').property('name', url).property('title', description).next()
  }

  const { value: documentationValue } = documentationVertex
  const { id: documentationId } = documentationValue
  const documentationElement = gremlin.V(documentationId)
  try {
    documentationElement.addE('documents').to(gremlin.V(dataset)).next()
  } catch (error) {
    console.log(`ERROR indexing RelatedUrl ${JSON.stringify(relatedUrl)}: \n Error: ${error}`)
  }
}
