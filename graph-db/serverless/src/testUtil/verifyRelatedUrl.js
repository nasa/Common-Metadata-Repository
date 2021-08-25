import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

export const verifyRelatedUrlExistInGraphDb = async (datasetTitle, url) => {
  // verify the dataset vertex with the given title exists
  const dataset = await global.testGremlinConnection
    .V()
    .has('collection', 'title', datasetTitle)
    .next()

  const { datasetValue = {} } = dataset
  const { id: datasetId } = datasetValue

  expect(datasetId).not.toBe(null)

  // verify the relatedUrl vertex with the given name exists
  const doc = await global.testGremlinConnection
    .V()
    .has('relatedUrl', 'url', url)
    .next()

  const { docValue = {} } = doc
  const { id: docId } = docValue

  expect(docId).not.toBe(null)

  // verify the edge exists between the two vertices
  const record = await global.testGremlinConnection
    .V()
    .has('collection', 'title', datasetTitle)
    .outE('linkedBy')
    .filter(gremlinStatistics.inV()
      .has('relatedUrl', 'url', url))
    .next()

  const { recordValue = {} } = record
  const { id: edgeId } = recordValue

  expect(edgeId).not.toBe(null)
}

export const verifyRelatedUrlNotExistInGraphDb = async (datasetTitle, url) => {
  // verify the dataset vertex with the given title does not exist
  const dataset = await global.testGremlinConnection
    .V()
    .has('collection', 'title', datasetTitle)
    .next()

  const { value: datasetValue = {} } = dataset

  expect(datasetValue).toBe(null)

  // verify the relatedUrl vertex with the given name does not exist
  const doc = await global.testGremlinConnection
    .V()
    .has('relatedUrl', 'url', url)
    .next()

  const { value: docValue = {} } = doc

  expect(docValue).toBe(null)
}
