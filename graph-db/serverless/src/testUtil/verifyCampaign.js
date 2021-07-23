import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

export const verifyCampaignExistInGraphDb = async (datasetTitle, campaignName) => {
  // verify the dataset vertex with the given title exists
  const dataset = await global.testGremlinConnection
    .V()
    .has('dataset', 'title', datasetTitle)
    .next()
  const { value: { id: datasetId } } = dataset
  expect(datasetId).not.toBe(null)

  // verify the campaign vertex with the given name exists
  const doc = await global.testGremlinConnection
    .V()
    .has('campaign', 'name', campaignName)
    .next()
  const { value: { id: docId } } = doc
  expect(docId).not.toBe(null)

  // verify the edge exists between the two vertices
  const record = await global.testGremlinConnection
    .V()
    .has('dataset', 'title', datasetTitle)
    .outE('includedIn')
    .filter(gremlinStatistics.inV()
      .has('campaign', 'name', campaignName))
    .next()
  const { value: { id: edgeId } } = record
  expect(edgeId).not.toBe(null)
}

export const verifyCampaignNotExistInGraphDb = async (datasetTitle, campaignName) => {
  // verify the dataset vertex with the given title does not exist
  const dataset = await global.testGremlinConnection
    .V()
    .has('dataset', 'title', datasetTitle)
    .next()
  const { value: datasetValue } = dataset
  expect(datasetValue).toBe(null)

  // verify the campaign vertex with the given name does not exist
  const doc = await global.testGremlinConnection
    .V()
    .has('campaign', 'name', campaignName)
    .next()
  const { value: docValue } = doc
  expect(docValue).toBe(null)
}
