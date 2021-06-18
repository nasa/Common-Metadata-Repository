import nock from 'nock'

import indexCmrCollection from '../handler'

import { updateCollection, deleteCollection } from '../../testUtil/indexCollection'

import { verifyExistInGraphDb, verifyNotExistInGraphDb } from '../../testUtil/verifyGraphDb'

beforeEach(() => {
  jest.clearAllMocks()
})

const getEvent = (conceptId, actionType) => {
  const eventBody = `{"concept-id": "${conceptId}", "revision-id": "1", "action": "${actionType}" }`
  return { Records: [{ body: eventBody }] }
}

describe('indexCmrCollection handler', () => {
  test('test index of single collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const docName = 'https://en.wikipedia.org/wiki/latent_nfi'

    await updateCollection('C1237293909-TESTPROV', datasetTitle, [docName])
    await verifyExistInGraphDb(datasetTitle, docName)
  })

  test('test index of not found collection', async () => {
    nock(/local-cmr/)
      .get(/collections/)
      .reply(200, {
        hits: 0,
        took: 6,
        items: []
      })

    const event = getEvent('C123755555-TESTPROV', 'concept-update')

    const consoleMock = jest.spyOn(console, 'log')

    const indexed = await indexCmrCollection(event)

    expect(consoleMock).toBeCalledTimes(1)
    expect(consoleMock).toBeCalledWith('Skip indexing of collection [C123755555-TESTPROV] as it is not found in CMR')

    const { body, statusCode } = indexed

    expect(body).toBe('Successfully indexed 0 collection(s)')
    expect(statusCode).toBe(200)
  })

  test('test unsupported event type', async () => {
    const event = getEvent('C1237293909-TESTPROV', 'concept-create')

    const consoleMock = jest.spyOn(console, 'log')

    const indexed = await indexCmrCollection(event)

    expect(consoleMock).toBeCalledTimes(1)
    expect(consoleMock).toBeCalledWith('Action [concept-create] was unsupported for concept [C1237293909-TESTPROV]')

    const { body, statusCode } = indexed

    expect(body).toBe('Successfully indexed 0 collection(s)')
    expect(statusCode).toBe(200)
  })

  test('test unsupported concept type', async () => {
    const event = getEvent('G1237293909-TESTPROV', 'concept-update')

    const consoleMock = jest.spyOn(console, 'log')

    const indexed = await indexCmrCollection(event)

    expect(consoleMock).toBeCalledTimes(1)
    expect(consoleMock).toBeCalledWith('Concept [G1237293909-TESTPROV] was not a collection and will not be indexed')

    const { body, statusCode } = indexed

    expect(body).toBe('Successfully indexed 0 collection(s)')
    expect(statusCode).toBe(200)
  })

  test('test deletion of single collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const docName = 'https://en.wikipedia.org/wiki/latent_nfi'

    // first index the collection and verify dataset and documentation vertices are created
    await updateCollection('C1237293909-TESTPROV', datasetTitle, [docName])
    await verifyExistInGraphDb(datasetTitle, docName)

    // delete the collection and verify dataset and documentation vertices are deleted
    await deleteCollection('C1237293909-TESTPROV')
    await verifyNotExistInGraphDb(datasetTitle, docName)
  })

  test('test deletion collection not delete linked documentation vertex if it is also linked to another collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const anotherDatasetTitle = 'Another Latent reserves within the Swiss NFI'
    // this documentation url is referenced by two collections
    const sharedDocName = 'https://en.wikipedia.org/wiki/latent_nfi'
    // this documentation url is referenced only by one collection
    const ownDocName = 'https://en.wikipedia.org/wiki/latent_nfi2'

    // first index the collection and verify dataset and documentation vertices are created
    await updateCollection('C1237293909-TESTPROV', datasetTitle, [sharedDocName, ownDocName])
    await verifyExistInGraphDb(datasetTitle, sharedDocName)
    await verifyExistInGraphDb(datasetTitle, ownDocName)

    // index a second collection that reference the same documentation vertex and verify dataset and documentation vertices are created
    await updateCollection('C1237294000-TESTPROV', anotherDatasetTitle, [sharedDocName])
    await verifyExistInGraphDb(anotherDatasetTitle, sharedDocName)

    // delete the collection and verify dataset vertex is deleted
    // the documentation vertex that is not referenced by another collection is deleted
    // the documentation vertex that is referenced by another collection is not deleted
    await deleteCollection('C1237293909-TESTPROV')
    await verifyNotExistInGraphDb(datasetTitle, ownDocName)
    await verifyExistInGraphDb(anotherDatasetTitle, sharedDocName)
  })
})
