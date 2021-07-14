import nock from 'nock'

import indexCmrCollection from '../handler'

import { updateCollection, deleteCollection } from '../../testUtil/indexCollection'

import {
  verifyDocumentationExistInGraphDb, verifyDocumentationNotExistInGraphDb,
  verifyCampaignExistInGraphDb, verifyCampaignNotExistInGraphDb
} from '../../testUtil/verifyGraphDb'

beforeEach(() => {
  jest.clearAllMocks()
})

const getEvent = (conceptId, actionType) => {
  const eventBody = `{"concept-id": "${conceptId}", "revision-id": "1", "action": "${actionType}" }`
  return { Records: [{ body: eventBody }] }
}

describe('indexCmrCollection handler', () => {
  test('test initial indexing of a collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const campaign1 = 'Campaign One'
    const docName = 'https://en.wikipedia.org/wiki/latent_nfi'

    await updateCollection(
      'C1237293909-TESTPROV',
      datasetTitle,
      {
        campaigns: [campaign1],
        docNames: [docName]
      }
    )

    await verifyDocumentationExistInGraphDb(datasetTitle, docName)
    await verifyCampaignExistInGraphDb(datasetTitle, campaign1)
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

    expect(body).toBe('Successfully indexed 0 collection(s). Skipped 1 collection(s).')
    expect(statusCode).toBe(200)
  })

  test('test unsupported event type', async () => {
    const event = getEvent('C1237293909-TESTPROV', 'concept-create')

    const consoleMock = jest.spyOn(console, 'log')

    const indexed = await indexCmrCollection(event)

    expect(consoleMock).toBeCalledTimes(1)
    expect(consoleMock).toBeCalledWith('Action [concept-create] was unsupported for concept [C1237293909-TESTPROV]')

    const { body, statusCode } = indexed

    expect(body).toBe('Successfully indexed 0 collection(s).')
    expect(statusCode).toBe(200)
  })

  test('test unsupported concept type', async () => {
    const event = getEvent('G1237293909-TESTPROV', 'concept-update')

    const consoleMock = jest.spyOn(console, 'log')

    const indexed = await indexCmrCollection(event)

    expect(consoleMock).toBeCalledTimes(1)
    expect(consoleMock).toBeCalledWith('Concept [G1237293909-TESTPROV] was not a collection and will not be indexed')

    const { body, statusCode } = indexed

    expect(body).toBe('Successfully indexed 0 collection(s).')
    expect(statusCode).toBe(200)
  })

  test('test deletion of single collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const campaign1 = 'Campaign One'
    const docName = 'https://en.wikipedia.org/wiki/latent_nfi'

    // first index the collection and verify dataset and documentation vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      datasetTitle,
      {
        campaigns: [campaign1],
        docNames: [docName]
      }
    )

    await verifyDocumentationExistInGraphDb(datasetTitle, docName)
    await verifyCampaignExistInGraphDb(datasetTitle, campaign1)

    // delete the collection and verify dataset and documentation vertices are deleted
    await deleteCollection('C1237293909-TESTPROV')
    await verifyDocumentationNotExistInGraphDb(datasetTitle, docName)
    await verifyCampaignNotExistInGraphDb(datasetTitle, campaign1)
  })

  test('test deletion collection not delete linked documentation vertex if it is also linked to another collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const anotherDatasetTitle = 'Another Latent reserves within the Swiss NFI'

    // this campaign is referenced by two collections
    const sharedCampaign = 'SharedCampaign'
    // this campaign is referenced only by one collection
    const ownCampaign = 'OwnCampaign'

    // this documentation url is referenced by two collections
    const sharedDocName = 'https://en.wikipedia.org/wiki/latent_nfi'
    // this documentation url is referenced only by one collection
    const ownDocName = 'https://en.wikipedia.org/wiki/latent_nfi2'

    // first index the collection and verify dataset and documentation vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      datasetTitle,
      {
        campaigns: [sharedCampaign, ownCampaign],
        docNames: [sharedDocName, ownDocName]
      }
    )

    await verifyCampaignExistInGraphDb(datasetTitle, sharedCampaign)
    await verifyCampaignExistInGraphDb(datasetTitle, ownCampaign)
    await verifyDocumentationExistInGraphDb(datasetTitle, sharedDocName)
    await verifyDocumentationExistInGraphDb(datasetTitle, ownDocName)

    // index a second collection that reference the same documentation vertex and verify dataset and documentation vertices are created
    await updateCollection(
      'C1237294000-TESTPROV',
      anotherDatasetTitle,
      {
        campaigns: [sharedCampaign],
        docNames: [sharedDocName]
      }
    )

    await verifyCampaignExistInGraphDb(anotherDatasetTitle, sharedCampaign)
    await verifyDocumentationExistInGraphDb(anotherDatasetTitle, sharedDocName)

    // delete the collection and verify dataset vertex is deleted
    // the campaign/documentation vertex that is not referenced by another collection is deleted
    // the campaign/documentation vertex that is referenced by another collection is not deleted
    await deleteCollection('C1237293909-TESTPROV')
    await verifyCampaignNotExistInGraphDb(datasetTitle, ownCampaign)
    await verifyCampaignExistInGraphDb(anotherDatasetTitle, sharedCampaign)
    await verifyDocumentationNotExistInGraphDb(datasetTitle, ownDocName)
    await verifyDocumentationExistInGraphDb(anotherDatasetTitle, sharedDocName)
  })

  test('test update collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const anotherDatasetTitle = 'Another Latent reserves within the Swiss NFI'

    // this campaign is referenced by both the old and new version of the collection
    const keptCampaign = 'KeptCampaign'
    // this campaign is referenced only by the old version of collection
    const removedCampaign = 'RemovedCampaign'
    // this campaign is referenced only by the new version of collection
    const newCampaign = 'NewCampaign'

    // this documentation url is referenced by both the old and new version of the collection
    const keptDocName = 'https://en.wikipedia.org/wiki/latent_nfi'
    // this documentation url is referenced only by the old version of collection
    const removedDocName = 'https://en.wikipedia.org/wiki/latent_nfi_old'
    // this documentation url is referenced only by the new version of collection
    const newDocName = 'https://en.wikipedia.org/wiki/latent_nfi_new'

    // first index the collection and verify dataset and documentation vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      datasetTitle,
      {
        campaigns: [keptCampaign, removedCampaign],
        docNames: [keptDocName, removedDocName]
      }
    )

    await verifyCampaignExistInGraphDb(datasetTitle, keptCampaign)
    await verifyCampaignExistInGraphDb(datasetTitle, removedCampaign)
    await verifyDocumentationExistInGraphDb(datasetTitle, keptDocName)
    await verifyDocumentationExistInGraphDb(datasetTitle, removedDocName)

    // update the collection
    await updateCollection(
      'C1237293909-TESTPROV',
      anotherDatasetTitle,
      {
        campaigns: [keptCampaign, newCampaign],
        docNames: [keptDocName, newDocName]
      }
    )

    // verify the datasetTitle dataset vertex and the removed campaign/documentation vertex are deleted
    await verifyCampaignNotExistInGraphDb(datasetTitle, removedCampaign)
    await verifyDocumentationNotExistInGraphDb(datasetTitle, removedDocName)

    // verify the dataset vertext with the new title exist,
    // verify the campaign/documentation vertices referenced by another collection exist,
    // and there are correct edges between the dataset vertex and the campaign/documentation vertices
    await verifyCampaignExistInGraphDb(anotherDatasetTitle, keptCampaign)
    await verifyCampaignExistInGraphDb(anotherDatasetTitle, newCampaign)
    await verifyDocumentationExistInGraphDb(anotherDatasetTitle, keptDocName)
    await verifyDocumentationExistInGraphDb(anotherDatasetTitle, newDocName)
  })
})
