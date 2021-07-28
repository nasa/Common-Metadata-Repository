export const verifyDatasetPropertiesInGraphDb = async (attrs) => {
  const {
    conceptId, datasetTitle, landingPage, doi
  } = attrs

  // verify the dataset vertex with the given properties exists
  const dataset = await global.testGremlinConnection
    .V()
    .has('dataset', 'title', datasetTitle)
    .has('concept-id', conceptId)
    .has('landing-page', landingPage)
    .has('doi', doi)
    .next()

  const { value: { id: datasetId } } = dataset
  expect(datasetId).not.toBe(null)
}
