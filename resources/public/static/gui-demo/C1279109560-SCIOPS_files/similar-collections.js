similar_collections = {
    CMR: "http://cmr.earthdata.nasa.gov/search/collections?concept_id=",
    collectionId: window.location.pathname.split('/').pop().split('.')[0],
    relatedCollections: [],

    getConcepts: async function (concept) {
        var graph_client = cmr.client.graph.create_client({"return-body?": true})

        var graph_channel = cmr.client.graph.get_collection_url_relation(
            graph_client, similar_collections.collectionId)
        cmr.client.common.util.with_callback(graph_channel, async function(data) {
            var conceptMetadata = await data.map(async function(conceptId) {
                $.get({url: similar_collections.CMR + conceptId,
                    beforeSend: function(xhr){xhr.setRequestHeader('Accept', 'application/vnd.nasa.cmr.umm_results+json');}},
                    function(data) {
                        var meta = data.items[0].meta
                        var umm = data.items[0].umm

                        similar_collections.relatedCollections.push({
                            collectionId: meta["concept-id"],
                            provider: meta["provider-id"],
                            shortName: umm["ShortName"],
                            collectionUrl: "https://cmr.earthdata.nasa.gov/search/concepts/" + conceptId + "/" + meta["revision-id"]
                        })
                    })
            })
        })
    },

    renderAndAppend: function(target) {
        $.get('C1279109560-SCIOPS_files/related-collection-template.html', function(template) {
            Mustache.parse(template)
            var render = Mustache.render(template, {collection: similar_collections.relatedCollections}) || "Not Provided"
            target.html(render)
        })
    }
}

$(document).ready(function() {
    similar_collections.getConcepts(similar_collections.collection_id)

    setTimeout(function(){ similar_collections.renderAndAppend($('.related-collections-cards'))}, 500)
})