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
exports.indexRelatedUrl = (relatedUrl, gremlin, dataset) => {
  const {
    Subtype: subType,
    URL: url,
    Description: description,
    URLContentType: urlContentType
  } = relatedUrl
  if (urlContentType !== 'PublicationURL') {
    // We only care about documentation at the moment.
    // Checking the URLContentType is the most efficient way to find its type.
    // Return early if it isn't some kind of documentation.
    return
  }

  const documentationVertexExists = gremlin.V().hasLabel(`related-information:${subType}`).has('name', url).hasNext()
  let docVertex

  if (documentationVertexExists) {
    docVertex = gremlin.addV(`related-information:${subType}`).property('name', url).property('title', description).next()
  } else {
    docVertex = gremlin.V().hasLabel(`related-information:${subType}`).has('name', url).next()
  }

  const { id: subTypeVertexId } = docVertex
  const datasetId = dataset.id
  gremlin.addE(`related-information:${subType}`).from(gremlin.V(subTypeVertexId)).to(gremlin.V(datasetId)).next()
}
