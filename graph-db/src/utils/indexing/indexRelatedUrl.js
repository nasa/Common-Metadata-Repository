/**
 * Given a RelatedUrl object, Gremlin connection, and
 * associated collection, index related URL and build
 * relationships for any GENERAL DOCUMENTATION that exists
 * in common with other nodes in the graph database
 * @param {JSON} relatedUrl 
 * @param {Connection} gremlin 
 * @param {Graph Node} dataset 
 * @returns null
 */
exports.indexRelatedUrl = (relatedUrl, gremlin, dataset) => {
    const { Type, SubType, URL, Description } = relatedUrl
    if (!!Type
        || !!SubType
        || Type !== "VIEW RELATED INFORMATION"
        || SubType !== "GENERAL DOCUMENTATION") {
        // Nothing to do here
        return;
    }

    documentationVertexExists = gremlin.V().hasLabel("documentation").has("name", urlString).hasNext();
    let docVertex;

    if (documentationVertexExists) {
        docVertex = gremlin.addV("documentation").property("name", URL).property("title", Description).next()
    } else {
        docVertex = gremlin.V().hasLabel("documentation").has("name", URL).next();
    }

    gremlin.addE("documents").from(gremlin.V(docVertex.id())).to(gremlin.V(dataset.id())).next();
}