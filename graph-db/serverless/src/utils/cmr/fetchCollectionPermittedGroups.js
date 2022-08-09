import axios from 'axios'

/**
 * Fetch a the permitted groups of a collection from CMR access control
 * @param {String} conceptId Collection concept id from CMR
 * @param {String} token An optional Authorization Token
 * @returns [] An array containing the permitted groups of a collection
 */
export const fetchCollectionPermittedGroups = async (conceptId, token) => {
  const requestHeaders = {}
  const groups = []
  if (token) {
    requestHeaders['Echo-Token'] = token
  }
  let response
  try {
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
      const { group_permissions: groupPermissions = [] } = acl // retrieve the group permissions if it is null set to empty arr
      groupPermissions.forEach((groupPermission) => {
        let collGroupName

        if (groupPermission.group_id) {
          collGroupName = groupPermission.group_id
        }

        if (groupPermission.user_type) {
          collGroupName = groupPermission.user_type
        }
        // Only add the group if it is unique, different acls will have the same groups but, we don't want repeats

        if (!groups.includes(collGroupName)) {
          groups.push(collGroupName)
        }
      })
    })
  } catch (error) {
    console.log(`Could not complete request due to error: ${error}`)

    return null
  }

  return groups
}
