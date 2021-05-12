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
    Type: type,
    SubType: subType,
    URL: url,
    Description: description,
  } = relatedUrl
  if (!!type
        || !!subType
        || type !== 'VIEW RELATED INFORMATION'
        || subType !== 'GENERAL DOCUMENTATION') {
    // We only care about documentation at the moment.
    // Return early
    return
  }

  const documentationVertexExists = gremlin.V().hasLabel('documentation').has('name', url).hasNext()
  let docVertex

  if (documentationVertexExists) {
    docVertex = gremlin.addV('documentation').property('name', URL).property('title', description).next()
  } else {
    docVertex = gremlin.V().hasLabel('documentation').has('name', URL).next()
  }

  gremlin.addE('documents').from(gremlin.V(docVertex.id())).to(gremlin.V(dataset.id())).next()
};
