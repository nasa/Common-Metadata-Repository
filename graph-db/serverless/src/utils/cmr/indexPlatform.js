import gremlin from 'gremlin'

const gremlinStatistics = gremlin.process.statics

/**
 * create the acquiredBy edge between platform/instrument and collection
 * @param {JSON} piVertex the platformInstrument vertex
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @returns null
 */
const createAcquiredByEdge = async (piVertex, gremlinConnection, dataset) => {
  const { value: vertexValue = {} } = piVertex
  const { id: piId } = vertexValue

  console.log(`PlatformInstrument vertex [${piId}] indexed for collection [${dataset}]`)

  // Create an edge between this platform and its parent collection
  const platformEdge = await gremlinConnection
    .V(piId).as('p')
    .V(dataset)
    .coalesce(
      gremlinStatistics.outE('acquiredBy').where(gremlinStatistics.inV().as('p')),
      gremlinConnection.addE('acquiredBy').to('p')
    )
    .next()

  const { value: edgeValue = {} } = platformEdge
  const { id: edgeId } = edgeValue

  console.log(`acquiredBy edge [${edgeId}] indexed to point to collection [${dataset}]`)
}

/**
 * Given an Instrument object, Gremlin connection, and associated platform and collection, index instrument and build relationships between platform/instrument and collection
 * @param {JSON} instrument the instrument
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {String} platformName the name of the platform
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @returns null
 */
const indexInstrument = async (instrument, gremlinConnection, platformName, dataset) => {
  const {
    ShortName: instrumentName
  } = instrument

  const piVertex = await gremlinConnection
    .V()
    .has('platformInstrument', 'platform', platformName)
    .has('instrument', instrumentName)
    .fold()
    .coalesce(
      gremlinStatistics.unfold(),
      gremlinConnection.addV('platformInstrument').property('platform', platformName).property('instrument', instrumentName)
    )
    .next()

  await createAcquiredByEdge(piVertex, gremlinConnection, dataset)
}

/**
 * Given a Platform object, Gremlin connection, and associated collection, index platform and instrument and build relationships between platform/instrument and collection
 * @param {JSON} platform the platform
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @param {String} conceptId Collection concept id from CMR
 * @returns null
 */
const indexPlatform = async (platform, gremlinConnection, dataset, conceptId) => {
  const {
    ShortName: platformName,
    Instruments: instruments
  } = platform

  try {
    if (instruments && instruments.length > 0) {
      await instruments.forEachAsync(async (instrument) => {
        await indexInstrument(instrument, gremlinConnection, platformName, dataset)
      })
    } else {
      // Use `fold` and `coalesce` to check existance of vertex, and create one if none exists.
      const piVertex = await gremlinConnection
        .V()
        .has('platformInstrument', 'platform', platformName)
        .fold()
        .coalesce(
          gremlinStatistics.unfold(),
          gremlinConnection.addV('platformInstrument').property('platform', platformName)
        )
        .next()

      await createAcquiredByEdge(piVertex, gremlinConnection, dataset)
    }
  } catch (error) {
    console.log(`ERROR indexing Platform for concept [${conceptId}] ${JSON.stringify(platform)}: \n Error: ${error}`)
  }
}

export default indexPlatform
