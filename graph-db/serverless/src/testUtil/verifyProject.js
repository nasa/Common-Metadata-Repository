import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

export const verifyProjectExistInGraphDb = async (collectionTitle, projectName) => {
  // verify the collection vertex with the given title exists
  const collection = await global.testGremlinConnection
    .V()
    .has('collection', 'title', collectionTitle)
    .next()
  const { value: { id: collectionId } } = collection
  expect(collectionId).not.toBe(null)

  // verify the project vertex with the given name exists
  const doc = await global.testGremlinConnection
    .V()
    .has('project', 'name', projectName)
    .next()
  const { value: { id: docId } } = doc
  expect(docId).not.toBe(null)

  // verify the edge exists between the two vertices
  const record = await global.testGremlinConnection
    .V()
    .has('collection', 'title', collectionTitle)
    .outE('includedIn')
    .filter(gremlinStatistics.inV()
      .has('project', 'name', projectName))
    .next()
  const { value: { id: edgeId } } = record
  expect(edgeId).not.toBe(null)
}

export const verifyProjectNotExistInGraphDb = async (collectionTitle, projectName) => {
  // verify the collection vertex with the given title does not exist
  const collection = await global.testGremlinConnection
    .V()
    .has('collection', 'title', collectionTitle)
    .next()
  const { value: collectionValue } = collection
  expect(collectionValue).toBe(null)

  // verify the project vertex with the given name does not exist
  const doc = await global.testGremlinConnection
    .V()
    .has('project', 'name', projectName)
    .next()
  const { value: docValue } = doc
  expect(docValue).toBe(null)
}
