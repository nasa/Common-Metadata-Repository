import axios from 'axios'
import axiosRetry from 'axios-retry'
/**
 * Fetch a the permitted groups of a collection from CMR access control
 * @param {String} conceptId Collection concept id from CMR
 * @param {String} token An optional Authorization Token
 * @returns [] An array containing the permitted groups of a collection
 */

// Compensate for any misses to the endpoint
axiosRetry(axios, {
  retryDelay: (retryCount) => retryCount * 600,
  retries: 3
})

export const fetchCollectionPermittedGroups = async (conceptId, token) => {
  const requestHeaders = {}
  const groups = []

  if (token) {
    requestHeaders.Authorization = token
  }

  let response
  try {
    response = await axios({
      url: `${process.env.cmrRoot}/access-control/acls?permitted_concept_id=${conceptId}&include_full_acl=true`,
      method: 'GET',
      headers: requestHeaders,
      json: true
    })

    const { data = {} } = response
    const { items = [] } = data

    items.forEach((item) => {
      const { acl = {} } = item
      const { group_permissions: groupPermissions = [] } = acl

      groupPermissions.forEach((groupPermission) => {
        let collectionGroupName

        const {
          group_id: groupId,
          user_type: userType
        } = groupPermission

        if (groupId) {
          ({ group_id: collectionGroupName } = groupPermission)
        }

        if (userType) {
          ({ user_type: collectionGroupName } = groupPermission)
        }

        // Only add the group if it is unique, different acls will have the same groups but, we don't want repeats
        if (!groups.includes(collectionGroupName)) {
          groups.push(collectionGroupName)
        }
      })
    })
  } catch (error) {
    console.log(`Could not complete request to Access Control App to retrieve group information for ${conceptId} due to error: ${error}`)
  }
  return groups
}
