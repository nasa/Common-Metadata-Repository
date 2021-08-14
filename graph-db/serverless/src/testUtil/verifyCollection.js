export const verifyCollectionPropertiesInGraphDb = async (attrs) => {
  const {
    conceptId, datasetTitle, doi
  } = attrs

  const verifyCommand = global.testGremlinConnection
    .V()
    .has('collection', 'title', datasetTitle)
    .has('id', conceptId)

  if (doi) {
    verifyCommand.has('doi', doi)
  } else {
    verifyCommand.hasNot('doi')
  }

  // verify the collection vertex with the given properties exists
  const collection = await verifyCommand.next()

  const { collectionValue = {} } = collection
  const { id: collectionId } = collectionValue

  expect(collectionId).not.toBe(null)
}
