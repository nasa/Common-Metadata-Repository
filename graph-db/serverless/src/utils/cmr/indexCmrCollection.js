import gremlin from 'gremlin'
import 'array-foreach-async'

import { deleteCmrCollection } from './deleteCmrCollection'
import { indexPlatform } from './indexPlatform'
import { indexProject } from './indexProject'
import { indexRelatedUrl } from './indexRelatedUrl'

const gremlinStatistics = gremlin.process.statics

/**
 * Given a collection from the CMR, index it into Gremlin
 * @param {JSON} collectionObj collection object from `items` array in cmr response
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server
 * @returns
 */

export const indexCmrCollection = async (collectionObj, groupList, gremlinConnection, depth = 1) => {
  // const maxDepth = 5
  const {
    meta: {
      'concept-id': conceptId,
      'provider-id': providerId
    },
    umm: {
      EntryTitle: entryTitle,
      DOI: {
        DOI: doiDescription
      },
      Projects: projects,
      Platforms: platforms,
      RelatedUrls: relatedUrls,
      ShortName: shortName
    }
  } = collectionObj

  // Delete the collection first so that we can clean up its related, relatedUrl vertices
  await deleteCmrCollection(conceptId, gremlinConnection)
  // TODO Introduce some kind of timeout here so that this can finnish executing on gremlin?
  let collection = null
  try {
    const addVCommand = gremlinConnection.addV('collection')
      .property('title', entryTitle)
      .property('id', conceptId)
      .property('shortName', shortName)
      .property('providerId', providerId)

    // See gremlin multi-properties vs list property
    if (groupList.length > 0) {
      groupList.forEach((group) => {
        addVCommand.property('permittedGroups', group)
      })
    }

    if (doiDescription) {
      addVCommand.property('doi', doiDescription)
    }

    // Use `fold` and `coalesce` to check existence of vertex, and create one if none exists.
    collection = await gremlinConnection
      .V()
      .hasLabel('collection')
      .has('id', conceptId)
      .fold()
      .coalesce(
        gremlinStatistics.unfold(),
        addVCommand
      )
      .next()
  } catch (error) {
    console.log(`Error indexing collection into graph database [${conceptId}]: ${error.message}`)
    if (depth > 3) {
      console.log('Max depth exceeded with depth at value', depth)
      return false
    }
    // setTimeout(5000)
    console.log('retrying the lambda function to index the graph database depth = ', depth)
    // Exponential back-off and retry policy

    await indexCmrCollection(collectionObj, groupList, gremlinConnection, depth + 1)
    return false
  }

  const { value = {} } = collection
  const { id: collectionId } = value

  if (projects && projects.length > 0) {
    await projects.forEachAsync(async (project) => {
      await indexProject(project, gremlinConnection, collectionId, conceptId)
    })
  }

  if (platforms && platforms.length > 0) {
    await platforms.forEachAsync(async (platform) => {
      await indexPlatform(platform, gremlinConnection, collectionId, conceptId)
    })
  }

  if (relatedUrls && relatedUrls.length > 0) {
    await relatedUrls.forEachAsync(async (relatedUrl) => {
      await indexRelatedUrl(relatedUrl, gremlinConnection, collectionId, conceptId)
    })
  }

  console.log(`Collection vertex [${collectionId}] indexed for collection [${conceptId}]`)

  return true
}
