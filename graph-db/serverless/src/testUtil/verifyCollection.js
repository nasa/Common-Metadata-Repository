export const verifyCollectionPropertiesInGraphDb = async (attrs) => {
  const {
    conceptId, datasetTitle, doi
  } = attrs

  // verify the collection vertex with the given properties exists
  const collection = await global.testGremlinConnection
    .V()
    .has('collection', 'title', datasetTitle)
    .has('id', conceptId)
    .has('doi', doi)
    .next()

  const { value: { id: collectionId } } = collection
  expect(collectionId).not.toBe(null)
}
