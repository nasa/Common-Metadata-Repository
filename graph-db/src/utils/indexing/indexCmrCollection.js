const { indexRelatedUrl } = require('./indexRelatedUrl');

exports.indexCmrCollection = async (collection, gremlin) => {
    const { meta, umm } = collection;
    const conceptId = meta['concept-id'];
    const { EntryTitle, DOI, RelatedUrls } = umm;
    const hasDOI = !!DOI.DOI;
    let doiUrl = "Not supplied";
    let datasetName = `${process.env.CMR_ROOT}/concepts/${conceptId}.html`;

    if (hasDOI) {
        const doiAddress = DOI.DOI.split(':').pop();
        doiUrl = `http://doi.org/${doiAddress}`;
        datasetName = doiAddress;
    }

    const exists = await gremlin.V().hasLabel("dataset").has("concept-id", conceptId).hasNext();
    let dataset = null;
    if (!exists) {
        dataset = await gremlin
        .addV("dataset")
        .property("name", datasetName)
        .property("title", EntryTitle)
        .property("concept-id", conceptId)
        .property("doi", DOI.DOI || "Not supplied")
        .next();
    }
    else {
        dataset = await gremlin.V().hasLabel("dataset").has("name", datasetName).next();                              
    }
    
    if (RelatedUrls && RelatedUrls.length >= 1) {
        RelatedUrls.map(relatedUrl => {
            indexRelatedUrl(relatedUrl, gremlin, dataset);
        })
    }
    
    return 200;
}