import gremlin from 'gremlin'
import 'array-foreach-async'

const gremlinStatistics = gremlin.process.statics

const verifyPlatformInstrumentExistInGraphDb = async (
  collectionTitle,
  platformName,
  instrumentName
) => {
  let platformInstrumentVertex
  // verify the platformInstrument vertex with the given name exists
  if (instrumentName) {
    platformInstrumentVertex = await global.testGremlinConnection
      .V()
      .has('platformInstrument', 'platform', platformName)
      .has('instrument', instrumentName)
      .next()
  } else {
    platformInstrumentVertex = await global.testGremlinConnection
      .V()
      .has('platformInstrument', 'platform', platformName)
      .next()
  }
  const { value: { id: docId } } = platformInstrumentVertex
  expect(docId).not.toBe(null)

  // verify the edge exists between the platformInstrument vertex and the collection vertex
  let record
  if (instrumentName) {
    record = await global.testGremlinConnection
      .V()
      .has('collection', 'title', collectionTitle)
      .outE('acquiredBy')
      .filter(gremlinStatistics.inV()
        .has('platformInstrument', 'platform', platformName)
        .has('instrument', instrumentName))
      .next()
  } else {
    record = await global.testGremlinConnection
      .V()
      .has('collection', 'title', collectionTitle)
      .outE('acquiredBy')
      .filter(gremlinStatistics.inV()
        .has('platformInstrument', 'platform', platformName))
      .next()
  }
  const { value: { id: edgeId } } = record
  expect(edgeId).not.toBe(null)
}

export const verifyPlatformInstrumentsExistInGraphDb = async (collectionTitle, attrs) => {
  const { platform, instruments } = attrs
  // verify the collection vertex with the given title exists
  const collection = await global.testGremlinConnection
    .V()
    .has('collection', 'title', collectionTitle)
    .next()
  const { value: { id: collectionId } } = collection
  expect(collectionId).not.toBe(null)

  // verify the platformInstrument vertex with the given platforms/instruments exists
  if (instruments && instruments.length > 0) {
    await instruments.forEachAsync(async (instrument) => {
      await verifyPlatformInstrumentExistInGraphDb(collectionTitle, platform, instrument)
    })
  } else {
    await verifyPlatformInstrumentExistInGraphDb(collectionTitle, platform, null)
  }
}

const verifyPlatformInstrumentNotExistInGraphDb = async (
  collectionTitle,
  platformName,
  instrumentName
) => {
  let platformInstrumentVertex
  // verify the platformInstrument vertex with the given name exists
  if (instrumentName) {
    platformInstrumentVertex = await global.testGremlinConnection
      .V()
      .has('platformInstrument', 'platform', platformName)
      .has('instrument', instrumentName)
      .next()
  } else {
    platformInstrumentVertex = await global.testGremlinConnection
      .V()
      .has('platformInstrument', 'platform', platformName)
      .next()
  }
  const { value: docValue } = platformInstrumentVertex
  expect(docValue).toBe(null)
}

export const verifyPlatformInstrumentsNotExistInGraphDb = async (collectionTitle, attrs) => {
  const { platform, instruments } = attrs
  // verify the collection vertex with the given title does not exist
  const collection = await global.testGremlinConnection
    .V()
    .has('collection', 'title', collectionTitle)
    .next()
  const { value: collectionValue } = collection
  expect(collectionValue).toBe(null)

  // verify the platformInstrument vertex with the given platforms/instruments does not exist
  if (instruments && instruments.length > 0) {
    await instruments.forEachAsync(async (instrument) => {
      await verifyPlatformInstrumentNotExistInGraphDb(collectionTitle, platform, instrument)
    })
  } else {
    await verifyPlatformInstrumentNotExistInGraphDb(collectionTitle, platform, null)
  }
}
