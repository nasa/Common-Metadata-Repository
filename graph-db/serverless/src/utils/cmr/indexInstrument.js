import gremlin from 'gremlin'

import { createAcquiredByEdge } from './createAcquiredByEdge'

const gremlinStatistics = gremlin.process.statics

/**
 * Given an Instrument object, Gremlin connection, and associated platform and collection, index instrument and build relationships between platform/instrument and collection
 * @param {JSON} instrument the instrument
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {String} platformName the name of the platform
 * @param {Graph Node} dataset the parent collection vertex in the gremlin server
 * @returns null
 */
export const indexInstrument = async (instrument, gremlinConnection, platformName, dataset) => {
  const {
    ShortName: instrumentName
  } = instrument

  let piVertex
  try {
    piVertex = await gremlinConnection
      .V()
      .has('platformInstrument', 'platform', platformName)
      .has('instrument', instrumentName)
      .fold()
      .coalesce(
        gremlinStatistics.unfold(),
        gremlinConnection.addV('platformInstrument').property('platform', platformName).property('instrument', instrumentName)
      )
      .next()
  } catch (error) {
    // Log specific error message, but throw error again to stop indexing
    console.error(`ERROR indexing instrument: ${error}`)
    throw error
  }

  const { value: vertexValue = {} } = piVertex
  const { id: piId } = vertexValue
  console.log(`PlatformInstrument vertex [${piId}] indexed for collection [${dataset}]`)

  await createAcquiredByEdge(piId, gremlinConnection, dataset)
}
