import gremlin from 'gremlin'

import { createAcquiredByEdge } from './createAcquiredByEdge'
import { indexInstrument } from './indexInstrument'

const gremlinStatistics = gremlin.process.statics

/**
 * Given a Platform object, Gremlin connection, and associated collection, index platform and instrument and build relationships between platform/instrument and collection
 * @param {JSON} platform the platform
 * @param {Connection} gremlinConnection a connection to the gremlin server
 * @param {Graph Node} collection the parent collection vertex in the gremlin server
 * @param {String} conceptId Collection concept id from CMR
 * @returns null
 */
export const indexPlatform = async (platform, gremlinConnection, collection, conceptId) => {
  const {
    ShortName: platformName,
    Instruments: instruments
  } = platform

  try {
    if (instruments && instruments.length > 0) {
      await instruments.forEachAsync(async (instrument) => {
        await indexInstrument(instrument, gremlinConnection, platformName, collection)
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

      const { value: vertexValue = {} } = piVertex
      const { id: piId } = vertexValue

      console.log(`PlatformInstrument vertex [${piId}] indexed for collection [${collection}]`)

      await createAcquiredByEdge(piId, gremlinConnection, collection)
    }
  } catch (error) {
    // Log specific error message, but throw error again to stop indexing
    console.error(`ERROR indexing Platform for concept [${conceptId}] ${JSON.stringify(platform)}: ${error}`)

    throw error
  }
}
