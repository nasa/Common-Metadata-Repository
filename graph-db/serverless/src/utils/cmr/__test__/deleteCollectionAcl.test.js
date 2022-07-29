import { deleteCollectionAcl } from '../deleteCollectionAcl'

beforeEach(() => {
  jest.clearAllMocks()
})
describe('cmr#deleteCollectionAcl', () => {
  test('handles failed deletion of an Acl due to bad gremlin connection', async () => {
    const deleteSuccess = await deleteCollectionAcl('The name of some acl collection', null)
    expect(deleteSuccess).toEqual(false)
  })
  // This test results in a success even if the Acl is not in the graphDB
  test('handles succesful deletion of an Acl', async () => {
    const deleteSuccess = await deleteCollectionAcl('The name of some acl collection', global.testGremlinConnection)
    expect(deleteSuccess).toEqual(true)
  })
})