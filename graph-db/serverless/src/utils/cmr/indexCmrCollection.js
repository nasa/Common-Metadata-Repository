import gremlin from 'gremlin'

import indexRelatedUrl from './indexRelatedUrl'

const gremlinStatistics = gremlin.process.statics

/**
 * Given a collection from the CMR, index it into Gremlin
 * @param {JSON} collection collection object from `items` array in cmr response
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @returns
 */
export const indexCmrCollection = async (collection, gremlinConnection) => {
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
    // Use `fold` and `coalesce` to check existance of vertex, and create one if none exists.
    dataset = await gremlinConnection
      .V()
      .hasLabel('dataset')
      .has('concept-id', conceptId)
      .fold()
      .coalesce(
        gremlinStatistics.unfold(),
        gremlinConnection.addV('dataset')
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
      indexRelatedUrl(relatedUrl, gremlinConnection, datasetId, conceptId)
    })
  }

  console.log(`Dataset vertex [${datasetId}] indexed for collection [${conceptId}]`)

  return true
}
