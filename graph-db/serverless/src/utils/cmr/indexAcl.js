import gremlin from 'gremlin'

import { isEmpty } from 'lodash'

import { createHasAccessToEdge } from './createHasAccessToEdge'
import { deleteAcl } from './deleteAcl'
import { indexGroup } from './indexGroup'

const gremlinStatistics = gremlin.process.statics

/**
 * Create a new Acl vertex with properties from the "recieving SNS topic message"
 * @param {string} concept_id // the concept_id of the acl that we are indexing
 * @param {string} aclObj // acl map holding the relevent data for building the vertex
 * @param {Gremlin Traversal Object} gremlinConnection connection to gremlin server in gremlin fashion usually, g
 * @returns {Map} //Returns a Map will the results of the query i.e. the identity of the new ACL vertex
 */
export const indexAcl = async (conceptId, aclObj, gremlinConnection) => {
  const {
    catalog_item_identity: catalogItemIdentity = {},
    group_permissions: groupPermissions = [],
    legacy_guid: legacyGuid
  } = aclObj

  const {
    name,
    provider_id: providerId,
    collection_identifier: collectionIdentifier = {}
  } = catalogItemIdentity

  await deleteAcl(conceptId, gremlinConnection)

  let aclVertex = null

  // TODO: Handle `All Collections`
  if (isEmpty(collectionIdentifier)) {
    console.log('This does not have a collection identifier')

    return false
  }

  try {
    const addVCommand = gremlinConnection.addV('acl').property('id', conceptId)
    addVCommand.property('name', name).property('providerId', providerId).property('legacyGuid', legacyGuid)

    // Use `fold` and `coalesce` to check existance of vertex, and create one if none exists.
    aclVertex = await gremlinConnection
      .V()
      .hasLabel('acl')
      .has('id', conceptId)
      .fold()
      .coalesce(
        gremlinStatistics.unfold(),
        addVCommand
      )
      .next()
  } catch (error) {
    console.log(`Error inserting acl node [${conceptId}]: ${error.message}`)

    return false
  }

  const { value = {} } = aclVertex
  const { id: aclId } = value // The id in the graphDB for the acl node not a concept id property

  // console.log('values stored in acl', aclId)
  // if the group permissions and exist and there are more than one of them
  await groupPermissions.forEachAsync(async (group) => {
    await indexGroup(group, gremlinConnection, conceptId)
  })

  // if there are collections in the collection identifer list i.e it is not an all collections acl
  // There is a minimum length of 1 collection required for a collection identifier field in the schema
  // we'll need to figure out temporal and access value type
  // parse out the concept Ids list

  // TODO we need to find out if the schema requires certain components in a specific way that we are missing
  const {
    concept_ids: conceptIds = []
  } = collectionIdentifier

  await conceptIds.forEachAsync(async (collectionConceptId) => {
    await createHasAccessToEdge(collectionConceptId, gremlinConnection, aclId)
  })

  const { id: nodeId } = value
  console.log(`Node [${nodeId}] for acl - ${conceptId}] successfully inserted into graph db.`)

  return aclVertex
}
