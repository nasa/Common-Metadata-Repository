import nock from 'nock'
import indexAcl from '../handler'

beforeEach(() => {
  jest.clearAllMocks()
})

describe('indexCmrCollection handler', () => {
  // Test conditions:
  // 1. fetchAccessControlList returns false because the acl was not in CMR
  // 2. the gremlin connection is bad
  // 3. action was not update or delete
  // 4. action was delete
  // 5. action was update/insert vertex
  
  test('fetchAccessControlList returns false because the acl was not in CMR', async () => {
    nock(/local-cmr/)
      .get(/acls/)
      .reply(200, {
        hits: 0,
        took: 6,
        items: null
      })

    const event = {"action": "concept-update", "concept-id": "ACL1111111111-CMR", "revision-id": 2}

    const consoleMock = jest.spyOn(console, 'log')

    const indexed = await indexAcl(event)

    // expect(consoleMock).toBeCalledTimes(1)
    // expect(consoleMock).toBeCalledWith('Skip indexing of collection [C123755555-TESTPROV] as it is not found in CMR')

    const { body, statusCode } = indexed

    expect(body).toBe('Successfully indexed 0 collection(s). Skipped 1 collection(s).')
    expect(statusCode).toBe(200)
  })


})
