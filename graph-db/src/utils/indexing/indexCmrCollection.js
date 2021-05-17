const { indexRelatedUrl } = require('./indexRelatedUrl')

/**
 * Given a collection from the CMR, index it into Gremlin
 * @param {JSON} collection collection object from `items` array in cmr response
 * @param {Gremlin Traversal Object} gremlin connection to gremlin server
 * @returns
 */
exports.indexCmrCollection = async (collection, gremlin) => {
  const { meta, umm } = collection
  const { 'concept-id': conceptId } = meta
  const { EntryTitle: entryTitle, DOI: doi, RelatedUrls: relatedUrls } = umm
  const { DOI: doiDescription } = doi
  let doiUrl = 'Not provided'
  let datasetName = `${process.env.CMR_ROOT}/concepts/${conceptId}.html`

  if (doiDescription) {
    // Take the second element from the split method
    const [, doiAddress] = doiUrl.split(':')
    doiUrl = `http://doi.org/${doiAddress}`
    datasetName = doiUrl
  }

  const exists = await gremlin.V().hasLabel('dataset').has('concept-id', conceptId).hasNext()
  let dataset = null
  if (!exists) {
    dataset = await gremlin
      .addV('dataset')
      .property('name', datasetName)
      .property('title', entryTitle)
      .property('concept-id', conceptId)
      .property('doi', doi.DOI || 'Not provided')
      .next()
  } else {
    dataset = await gremlin.V().hasLabel('dataset').has('name', datasetName).next()
  }

  if (relatedUrls && relatedUrls.length > 0) {
    relatedUrls.forEach((relatedUrl) => {
      indexRelatedUrl(relatedUrl, gremlin, dataset)
    })
  }

  return 200
}
