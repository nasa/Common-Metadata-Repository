import gremlin from 'gremlin'

import { createAcquiredByEdge } from './createAcquiredByEdge'

const gremlinStatistics = gremlin.process.statics

/**
 * Given an Instrument object, Gremlin connection, and associated platform and collection, index instrument and build relationships between platform/instrument and collection
 * @param {JSON} instrument the instrument
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {String} platformName the name of the platform
 * @param {Graph Node} collection the parent collection vertex in the gremlin server
 * @returns null
 */
export const indexInstrument = async (instrument, gremlinConnection, platformName, collection) => {
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
    // Log useful information pertaining to the error
    console.log(`Failed to index Instrument for platform [${platformName}] ${JSON.stringify(instrument)}`)

    // Log the error
    console.log(error)

    // Re-throw the error
    throw error
  }

  const { value: vertexValue = {} } = piVertex
  const { id: piId } = vertexValue

  console.log(`PlatformInstrument vertex [${piId}] indexed for collection [${collection}]`)

  await createAcquiredByEdge(piId, gremlinConnection, collection)
}
