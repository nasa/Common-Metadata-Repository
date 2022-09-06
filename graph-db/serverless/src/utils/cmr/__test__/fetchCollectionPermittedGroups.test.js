import nock from 'nock'

import { fetchCollectionPermittedGroups } from '../fetchCollectionPermittedGroups'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('fetchCollectionPermittedGroups', () => {
  test('returns collection permitted groups metadata', async () => {
    const mockedBody = {
      items: [
        {
          concept_id: 'ACL1376510432-CMR',
          revision_id: 9,
          identity_type: 'Catalog Item',
          acl: {
            group_permissions: [{ permissions: ['read'], group_id: 'AG1337430038-NSIDC_ECS' },
              { permissions: ['read'], group_id: 'AG1337430039-NSIDC_ECS' },
              { permissions: ['read'], group_id: 'AG1337409405-CMR' },
              { permissions: ['read'], group_id: 'AG1386452130-CMR' },
              { permissions: ['read'], group_id: 'AG1337409406-CMR' },
              { permissions: ['read'], user_type: 'registered' },
              { permissions: ['read'], group_id: 'AG1337409407-CMR' }],
            catalog_item_identity: [],
            legacy_guid: '26B6710B-0562-953D-CCE7-E185B36A9545'
          },
          name: 'All Collections',
          location: 'https://cmr.earthdata.nasa.gov:443/access-control/acls/ACL1376510432-CMR'
        },
        {
          concept_id: 'ACL1374052769-CMR',
          revision_id: 81,
          identity_type: 'Catalog Item',
          acl: {
            group_permissions: [
              { permissions: ['read', 'order'], group_id: 'AG1337430038-NSIDC_ECS' },
              { permissions: ['read', 'order'], group_id: 'AG1337409405-CMR' },
              { permissions: ['read', 'order'], group_id: 'AG1337409406-CMR' },
              { permissions: ['read', 'order'], group_id: 'AG1337409413-CMR' },
              { permissions: ['read', 'order'], user_type: 'guest' },
              { permissions: ['read'], user_type: 'guest' },
              { permissions: ['read', 'order'], group_id: 'AG1337409411-CMR' }],
            catalog_item_identity: [],
            legacy_guid: '78B9267A-8876-34B0-020D-2B62ED010C39'
          },
          name: 'IceBridge Public Collection',
          location: 'https://cmr.earthdata.nasa.gov:443/access-control/acls/ACL1374052769-CMR'
        }
      ]
    }

    nock(/local-cmr/)
      .get(/acls/)
      .reply(200, mockedBody)

    const result = await fetchCollectionPermittedGroups('C1708620364-NSIDC_ECS', 'mock_token')

    // The result should be equal to a list of groups ['a', 'b', 'c']
    const permittedGroups = [
      'AG1337430038-NSIDC_ECS',
      'AG1337430039-NSIDC_ECS',
      'AG1337409405-CMR',
      'AG1386452130-CMR',
      'AG1337409406-CMR',
      'registered',
      'AG1337409407-CMR',
      'AG1337409413-CMR',
      'guest',
      'AG1337409411-CMR'
    ]
    expect(result).toEqual(permittedGroups)
  })

  test('If the concept_id does not exist CMR returns a 400 error', async () => {
    const consoleMock = jest.spyOn(console, 'log')
    const mockedBody = []

    nock(/local-cmr/)
      .get(/acls/)
      .reply(400, mockedBody)

    const result = await fetchCollectionPermittedGroups('Not_a_real_Collection', 'mock_token')

    expect(consoleMock).toHaveBeenCalledWith('Could not complete request to acl due to error: Error: Request failed with status code 400')
    expect(result).toEqual(mockedBody)
  })

  test('If the no token was supplied', async () => {
    const consoleMock = jest.spyOn(console, 'log')
    const mockedBody = []

    nock(/local-cmr/)
      .get(/acls/)
      .reply(400, mockedBody)
    const result = await fetchCollectionPermittedGroups('Not_a_real_Collection')
    expect(consoleMock).toHaveBeenCalledWith('Could not complete request to acl due to error: Error: Request failed with status code 400')
    expect(result).toEqual(mockedBody)
  })

  test('If CMR returns an empty ACL with no groups', async () => {
    const mockedBody = {
      items: [
        {
          other: 'other'
        }
      ]
    }

    nock(/local-cmr/)
      .get(/acls/)
      .reply(200, mockedBody)
    const result = await fetchCollectionPermittedGroups('Not_a_real_Collection')
    const emptyArr = []
    expect(result).toEqual(emptyArr)
  })

  test('If CMR returns an empty items with no groups', async () => {
    const mockedBody = {
      status: 200,
      statusText: 'OK',
      data: { hits: 3, took: 48 }
    }
    // intercept the http to acls have the reply not have data
    nock(/local-cmr/)
      .get(/acls/)
      .reply(200, mockedBody)
    const result = await fetchCollectionPermittedGroups('CMR-Collection1234')
    const emptyArr = []
    expect(result).toEqual(emptyArr)
  })

  test('If CMR returns wtih an emprty response, trigger default values', async () => {
    const mockedBody = { Items: [] }
    nock(/local-cmr/)
      .get(/acls/)
      .reply(200, mockedBody)
    const result = await fetchCollectionPermittedGroups('Not_a_real_Collection')
    const emptyArr = []
    expect(result).toEqual(emptyArr)
  })

  test('if group permissions are empty for the returned collection', async () => {
    const mockedBody = {
      items: [
        {
          concept_id: 'ACL1376510432-CMR',
          revision_id: 9,
          identity_type: 'Catalog Item',
          acl: {
            group_permissions: [],
            catalog_item_identity: [],
            legacy_guid: '26B6710B-0562-953D-CCE7-E185B36A9545'
          },
          name: 'All Collections',
          location: 'https://cmr.earthdata.nasa.gov:443/access-control/acls/ACL1376510432-CMR'
        },
        {
          concept_id: 'ACL1374052769-CMR',
          revision_id: 81,
          identity_type: 'Catalog Item',
          acl: {
            group_permissions: [],
            catalog_item_identity: [],
            legacy_guid: '78B9267A-8876-34B0-020D-2B62ED010C39'
          },
          name: 'IceBridge Public Collection',
          location: 'https://cmr.earthdata.nasa.gov:443/access-control/acls/ACL1374052769-CMR'
        }
      ]
    }

    nock(/local-cmr/)
      .get(/acls/)
      .reply(200, mockedBody)

    const result = await fetchCollectionPermittedGroups('C1708620364-NSIDC_ECS', 'mock_token')

    // The result should be equal to a list of groups ['a', 'b', 'c']
    const permittedGroups = []
    expect(result).toEqual(permittedGroups)
  })
})
