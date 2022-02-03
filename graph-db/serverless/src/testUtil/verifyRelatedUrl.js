import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

export const verifyRelatedUrlExistInGraphDb = async (collectionTitle, url) => {
  // verify the collection vertex with the given title exists
  const collection = await global.testGremlinConnection
    .V()
    .has('collection', 'title', collectionTitle)
    .next()

  const { collectionValue = {} } = collection
  const { id: collectionId } = collectionValue

  expect(collectionId).not.toBe(null)

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
    .has('collection', 'title', collectionTitle)
    .outE('linkedBy')
    .filter(gremlinStatistics.inV()
      .has('relatedUrl', 'url', url))
    .next()

  const { recordValue = {} } = record
  const { id: edgeId } = recordValue

  expect(edgeId).not.toBe(null)
}

export const verifyRelatedUrlNotExistInGraphDb = async (collectionTitle, url) => {
  // verify the collection vertex with the given title does not exist
  const collection = await global.testGremlinConnection
    .V()
    .has('collection', 'title', collectionTitle)
    .next()

  const { value: collectionValue = {} } = collection

  expect(collectionValue).toBe(null)

  // verify the relatedUrl vertex with the given name does not exist
  const doc = await global.testGremlinConnection
    .V()
    .has('relatedUrl', 'url', url)
    .next()

  const { value: docValue = {} } = doc

  expect(docValue).toBe(null)
}
