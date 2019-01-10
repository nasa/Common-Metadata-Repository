similar_collections = {
    CMR: "http://cmr.earthdata.nasa.gov/search/collections?concept_id=",
    collectionId: window.location.pathname.split('/').pop().split('.')[0],
    relatedCollections: [],

    getConcepts: function (concept) {
        var graph_client = cmr.client.graph.create_client({"return-body?": true})

        var graph_channel = cmr.client.graph.get_collection_url_relation(
            graph_client, similar_collections.collectionId)
        cmr.client.common.util.with_callback(graph_channel, async function(data) {
            var conceptMetadata = data.map(async function(conceptId) {
                var data = fetch(similar_collections.CMR + conceptId, {headers: {'Accept': 'application/vnd.nasa.cmr.umm_results+json'}})
                                .then((response) => response.json())

                var meta = data.items[0].meta
                var umm = data.items[0].umm
                return {
                    collectionId: meta["concept-id"],
                    provider: meta["provider-id"],
                    shortName: umm["ShortName"],
                    collectionUrl: "https://cmr.earthdata.nasa.gov/search/concepts/" + conceptId + "/" + meta["revision-id"]
                }
            })

            similar_collections.renderAndAppend($('.related-collections-cards'), Promise.all(conceptMetadata).then((values) => values))
        })
    },
    renderAndAppend: function(target, data) {
        var template = fetch('C1279109560-SCIOPS_files/related-collection-template.html').then((response) => response.text())

        Mustache.parse(template)
        var render = Mustache.render(template, {collection: data}) || "Not Provided"
        target.html(render)
    }
}

$(document).ready(function() {
    similar_collections.getConcepts(similar_collections.collection_id)
})