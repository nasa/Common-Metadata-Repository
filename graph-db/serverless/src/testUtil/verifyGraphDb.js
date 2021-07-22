import gremlin from 'gremlin'
import 'array-foreach-async'

const gremlinStatistics = gremlin.process.statics

export const verifyDocumentationExistInGraphDb = async (datasetTitle, docName) => {
  // verify the dataset vertex with the given title exists
  const dataset = await global.testGremlinConnection
    .V()
    .has('dataset', 'title', datasetTitle)
    .next()
  const { value: { id: datasetId } } = dataset
  expect(datasetId).not.toBe(null)

  // verify the documentation vertex with the given name exists
  const doc = await global.testGremlinConnection
    .V()
    .has('documentation', 'name', docName)
    .next()
  const { value: { id: docId } } = doc
  expect(docId).not.toBe(null)

  // verify the edge exists between the two vertices
  const record = await global.testGremlinConnection
    .V()
    .has('dataset', 'title', datasetTitle)
    .outE('documentedBy')
    .filter(gremlinStatistics.inV()
      .has('documentation', 'name', docName))
    .next()
  const { value: { id: edgeId } } = record
  expect(edgeId).not.toBe(null)
}

export const verifyDocumentationNotExistInGraphDb = async (datasetTitle, docName) => {
  // verify the dataset vertex with the given title does not exist
  const dataset = await global.testGremlinConnection
    .V()
    .has('dataset', 'title', datasetTitle)
    .next()
  const { value: datasetValue } = dataset
  expect(datasetValue).toBe(null)

  // verify the documentation vertex with the given name does not exist
  const doc = await global.testGremlinConnection
    .V()
    .has('documentation', 'name', docName)
    .next()
  const { value: docValue } = doc
  expect(docValue).toBe(null)
}

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

const verifyPlatformInstrumentExistInGraphDb = async (
  datasetTitle,
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
      .has('dataset', 'title', datasetTitle)
      .outE('acquiredBy')
      .filter(gremlinStatistics.inV()
        .has('platformInstrument', 'platform', platformName)
        .has('instrument', instrumentName))
      .next()
  } else {
    record = await global.testGremlinConnection
      .V()
      .has('dataset', 'title', datasetTitle)
      .outE('acquiredBy')
      .filter(gremlinStatistics.inV()
        .has('platformInstrument', 'platform', platformName))
      .next()
  }
  const { value: { id: edgeId } } = record
  expect(edgeId).not.toBe(null)
}

export const verifyPlatformInstrumentsExistInGraphDb = async (datasetTitle, attrs) => {
  const { platform, instruments } = attrs
  // verify the dataset vertex with the given title exists
  const dataset = await global.testGremlinConnection
    .V()
    .has('dataset', 'title', datasetTitle)
    .next()
  const { value: { id: datasetId } } = dataset
  expect(datasetId).not.toBe(null)

  // verify the platformInstrument vertex with the given platforms/instruments exists
  if (instruments && instruments.length > 0) {
    await instruments.forEachAsync(async (instrument) => {
      await verifyPlatformInstrumentExistInGraphDb(datasetTitle, platform, instrument)
    })
  } else {
    await verifyPlatformInstrumentExistInGraphDb(datasetTitle, platform, null)
  }
}

const verifyPlatformInstrumentNotExistInGraphDb = async (
  datasetTitle,
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

export const verifyPlatformInstrumentsNotExistInGraphDb = async (datasetTitle, attrs) => {
  const { platform, instruments } = attrs
  // verify the dataset vertex with the given title does not exist
  const dataset = await global.testGremlinConnection
    .V()
    .has('dataset', 'title', datasetTitle)
    .next()
  const { value: datasetValue } = dataset
  expect(datasetValue).toBe(null)

  // verify the platformInstrument vertex with the given platforms/instruments does not exist
  if (instruments && instruments.length > 0) {
    await instruments.forEachAsync(async (instrument) => {
      await verifyPlatformInstrumentNotExistInGraphDb(datasetTitle, platform, instrument)
    })
  } else {
    await verifyPlatformInstrumentNotExistInGraphDb(datasetTitle, platform, null)
  }
}
