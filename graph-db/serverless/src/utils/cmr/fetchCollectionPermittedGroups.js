import axios from 'axios'
import axiosRetry from 'axios-retry'
/**
 * Fetch a the permitted groups of a collection from CMR access control
 * @param {String} conceptId Collection concept id from CMR
 * @param {String} token An optional Authorization Token
 * @returns [] An array containing the permitted groups of a collection
 */

// Compensate for any misses to the endpoint max retries is going to be 4 using
// exponential timing between the calls
// axiosRetry(axios, { retryDelay: axiosRetry.exponentialDelay, retries: 4 })
// More aggressive wait between call request attempts than from the exponential delay
axiosRetry(axios, {
  retryDelay: (retryCount) => retryCount * 500,
  retries: 3
})
// exponential delay
// export function exponentialDelay(retryNumber = 0) {
//   const delay = Math.pow(2, retryNumber) * 100
//   const randomSum = delay * 0.2 * Math.random() // 0-20% of the delay
//   return delay + randomSum
// }
// 4th try would take approximately 800ms

export const fetchCollectionPermittedGroups = async (conceptId, token) => {
  const requestHeaders = {}
  const groups = []

  if (token) {
    requestHeaders.Authorization = token
  }

  let response
  try {
    // TODO: We can put a wait function here to ensure that this thing is waiting a long time between calls so that we can
    // ensure that we do not overwhelm the access-control app

    response = await axios({
      url: `${process.env.CMR_ROOT}/access-control/acls?permitted_concept_id=${conceptId}&include_full_acl=true`,
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
