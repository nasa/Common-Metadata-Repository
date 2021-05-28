const { indexRelatedUrl } = require('./indexRelatedUrl')

/**
 * Given a collection from the CMR, index it into Gremlin
 * @param {JSON} collection collection object from `items` array in cmr response
 * @param {Gremlin Traversal Object} gremlin connection to gremlin server
 * @returns
 */
exports.indexCmrCollection = async (collection, gremlin) => {
  const {
    meta: { 'concept-id': conceptId },
    umm: {
      EntryTitle: entryTitle,
      DOI: { DOI: doiDescription },
      RelatedUrls: relatedUrls
    }
  } = collection
  let doiUrl = 'Not provided'
  let datasetName = `${process.env.CMR_ROOT}/concepts/${conceptId}.html`

  if (doiDescription) {
    // Take the second element from the split method
    const [, doiAddress] = doiDescription.split(':')
    doiUrl = `https://dx.doi.org/${doiAddress}`
    datasetName = doiUrl
  }

  let dataset = null
  try {
    const exists = await gremlin.V().hasLabel('dataset').has('concept-id', conceptId).hasNext()

    if (exists) {
      dataset = await gremlin.V().hasLabel('dataset').has('name', datasetName).next()
    } else {
      dataset = await gremlin.addV('dataset')
        .property('name', datasetName)
        .property('title', entryTitle)
        .property('concept-id', conceptId)
        .property('doi', doiDescription || 'Not provided')
        .next()
    }
  } catch (error) {
    console.log(`Error indexing collection [${conceptId}]: ${error}`)
  }
  const { value: { id: datasetId } } = dataset

  if (relatedUrls && relatedUrls.length > 0) {
    relatedUrls.forEach((relatedUrl) => {
      indexRelatedUrl(relatedUrl, gremlin, datasetId, conceptId)
    })
  }

  return true
}
