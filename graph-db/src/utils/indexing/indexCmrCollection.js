const { indexRelatedUrl } = require('./indexRelatedUrl');

exports.indexCmrCollection = async (collection, gremlin) => {
    const { meta, umm } = collection;
    const conceptId = meta['concept-id'];
    const { EntryTitle: entryTitle, DOI: doi, RelatedUrls: relatedUrls } = umm;
    const hasDOI = !!doi.DOI;
    let doiUrl = "Not supplied";
    let datasetName = `${process.env.CMR_ROOT}/concepts/${conceptId}.html`;

    if (hasDOI) {
        const doiAddress = doi.DOI.split(':').pop();
        doiUrl = `http://doi.org/${doiAddress}`;
        datasetName = doiAddress;
    }

    const exists = await gremlin.V().hasLabel("dataset").has("concept-id", conceptId).hasNext();
    let dataset = null;
    if (!exists) {
        dataset = await gremlin
        .addV("dataset")
        .property("name", datasetName)
        .property("title", entryTitle)
        .property("concept-id", conceptId)
        .property("doi", doi.DOI || "Not supplied")
        .next();
    }
    else {
        dataset = await gremlin.V().hasLabel("dataset").has("name", datasetName).next();                              
    }
    
    if (relatedUrls && relatedUrls.length >= 1) {
        relatedUrls.map(relatedUrl => {
            indexRelatedUrl(relatedUrl, gremlin, dataset);
        })
    }
    
    return 200;
}