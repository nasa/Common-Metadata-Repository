const gremlin = require('gremlin')

// eslint-disable-next-line no-underscore-dangle
const __ = gremlin.process.statics
const { indexRelatedUrl } = require('./indexRelatedUrl')

/**
 * Given a collection from the CMR, index it into Gremlin
 * @param {JSON} collection collection object from `items` array in cmr response
 * @param {Gremlin Traversal Object} g connection to gremlin server
 * @returns
 */
exports.indexCmrCollection = async (collection, g) => {
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

  console.log(`RelatedUrls for concept [${conceptId}]: ${relatedUrls}`)

  let dataset = null
  try {
    dataset = await g
      .V()
      .hasLabel('dataset')
      .has('concept-id', conceptId)
      .fold()
      .coalesce(
        __.unfold(),
        g.addV('dataset')
          .property('name', datasetName)
          .property('title', entryTitle)
          .property('concept-id', conceptId)
          .property('doi', doiDescription || 'Not provided')
      )
      .next()
  } catch (error) {
    console.log(`Error indexing collection [${conceptId}]: ${error.message}`)
    return false
  }

  const { value: { id: datasetId } } = dataset

  if (relatedUrls && relatedUrls.length > 0) {
    relatedUrls.forEach((relatedUrl) => {
      indexRelatedUrl(relatedUrl, g, datasetId, conceptId)
    })
  }

  console.log(`Indexed collection [${conceptId}] successfully`)

  return true
}
