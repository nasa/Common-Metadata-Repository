import nock from 'nock'

import indexCmrCollection from '../handler'

import { updateCollection, deleteCollection } from '../../testUtil/indexCollection'

import {
  verifyDocumentationExistInGraphDb, verifyDocumentationNotExistInGraphDb
} from '../../testUtil/verifyDocumentation'

import {
  verifyCampaignExistInGraphDb, verifyCampaignNotExistInGraphDb
} from '../../testUtil/verifyCampaign'

import {
  verifyPlatformInstrumentsExistInGraphDb, verifyPlatformInstrumentsNotExistInGraphDb
} from '../../testUtil/verifyPlatformInstrument'

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
    const platform1 = 'Platform One'
    const instrument1 = 'Instrument One'
    const platform2 = 'Platform Two'
    const docName = 'https://en.wikipedia.org/wiki/latent_nfi'

    await updateCollection(
      'C1237293909-TESTPROV',
      datasetTitle,
      {
        campaigns: [campaign1],
        platforms: [{ platform: platform1, instruments: [instrument1] }, { platform: platform2 }],
        docNames: [docName]
      }
    )

    await verifyCampaignExistInGraphDb(datasetTitle, campaign1)
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle,
      { platform: platform1, instruments: [instrument1] })
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle, { platform: platform2 })
    await verifyDocumentationExistInGraphDb(datasetTitle, docName)
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
    const platform1 = 'Platform One'
    const instrument1 = 'Instrument One'
    const instrument2 = 'Instrument Two'
    const platform2 = 'Platform Two'
    const docName = 'https://en.wikipedia.org/wiki/latent_nfi'

    // first index the collection and verify dataset and documentation vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      datasetTitle,
      {
        campaigns: [campaign1],
        platforms: [
          { platform: platform1, instruments: [instrument1, instrument2] },
          { platform: platform2 }],
        docNames: [docName]
      }
    )

    await verifyCampaignExistInGraphDb(datasetTitle, campaign1)
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle,
      { platform: platform1, instruments: [instrument1, instrument2] })
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle, { platform: platform2 })
    await verifyDocumentationExistInGraphDb(datasetTitle, docName)

    // delete the collection and verify dataset and campaign/documentation vertices are deleted
    await deleteCollection('C1237293909-TESTPROV')
    await verifyCampaignNotExistInGraphDb(datasetTitle, campaign1)
    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle,
      { platform: platform1, instruments: [instrument1, instrument2] })
    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle, { platform: platform2 })
    await verifyDocumentationNotExistInGraphDb(datasetTitle, docName)
  })

  test('test deletion collection not delete linked documentation vertex if it is also linked to another collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const anotherDatasetTitle = 'Another Latent reserves within the Swiss NFI'

    // this campaign is referenced by two collections
    const sharedCampaign = 'SharedCampaign'
    // this campaign is referenced only by one collection
    const ownCampaign = 'OwnCampaign'

    // this platform is referenced by two collections
    const sharedPlatform = 'SharedPlatform'
    // this platform is referenced by one collection
    const ownPlatform = 'OwnPlatform'
    const sharedInstrument = 'SharedInstrument'
    const ownInstrument = 'OwnInstrument'

    // this documentation url is referenced by two collections
    const sharedDocName = 'https://en.wikipedia.org/wiki/latent_nfi'
    // this documentation url is referenced only by one collection
    const ownDocName = 'https://en.wikipedia.org/wiki/latent_nfi2'

    // first index the collection and verify dataset and campaign/documentation vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      datasetTitle,
      {
        campaigns: [sharedCampaign, ownCampaign],
        platforms: [
          { platform: sharedPlatform, instruments: [sharedInstrument, ownInstrument] },
          { platform: ownPlatform }],
        docNames: [sharedDocName, ownDocName]
      }
    )

    await verifyCampaignExistInGraphDb(datasetTitle, sharedCampaign)
    await verifyCampaignExistInGraphDb(datasetTitle, ownCampaign)
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle,
      { platform: sharedPlatform, instruments: [sharedInstrument, ownInstrument] })
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle, { platform: ownPlatform })
    await verifyDocumentationExistInGraphDb(datasetTitle, sharedDocName)
    await verifyDocumentationExistInGraphDb(datasetTitle, ownDocName)

    // index a second collection that reference the same campaign/documentation vertex
    // and verify dataset and campaign/documentation vertices are created
    await updateCollection(
      'C1237294000-TESTPROV',
      anotherDatasetTitle,
      {
        campaigns: [sharedCampaign],
        platforms: [
          { platform: sharedPlatform, instruments: [sharedInstrument] }],
        docNames: [sharedDocName]
      }
    )

    await verifyCampaignExistInGraphDb(anotherDatasetTitle, sharedCampaign)
    await verifyPlatformInstrumentsExistInGraphDb(anotherDatasetTitle,
      { platform: sharedPlatform, instruments: [sharedInstrument] })
    await verifyDocumentationExistInGraphDb(anotherDatasetTitle, sharedDocName)

    // delete the collection and verify dataset vertex is deleted
    // the campaign/documentation/platformInstrument vertex that is not referenced by another collection is deleted
    // the campaign/documentation/platformInstrument vertex that is referenced by another collection is not deleted
    await deleteCollection('C1237293909-TESTPROV')
    await verifyCampaignNotExistInGraphDb(datasetTitle, ownCampaign)
    await verifyCampaignExistInGraphDb(anotherDatasetTitle, sharedCampaign)

    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle,
      { platform: sharedPlatform, instruments: [ownInstrument] })
    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle, { platform: ownPlatform })
    await verifyPlatformInstrumentsExistInGraphDb(anotherDatasetTitle,
      { platform: sharedPlatform, instruments: [sharedInstrument] })

    await verifyDocumentationNotExistInGraphDb(datasetTitle, ownDocName)
    await verifyDocumentationExistInGraphDb(anotherDatasetTitle, sharedDocName)
  })

  test.only('test update collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const anotherDatasetTitle = 'Another Latent reserves within the Swiss NFI'

    // this campaign is referenced by both the old and new version of the collection
    const keptCampaign = 'KeptCampaign'
    // this campaign is referenced only by the old version of collection
    const removedCampaign = 'RemovedCampaign'
    // this campaign is referenced only by the new version of collection
    const newCampaign = 'NewCampaign'

    // this platform is referenced by both the old and new version of the collection
    const keptPlatform = 'KeptPlatform'
    // this platform is referenced only by the old version of collection
    const removedPlatform = 'RemovedPlatform'
    // this platform is referenced only by the new version of collection
    const newPlatform = 'NewPlatform'

    // this instrument is referenced by both the old and new version of the collection
    const keptInstrument = 'KeptInstrument'
    // this instrument is referenced only by the old version of collection
    const removedInstrument = 'RemovedInstrument'
    // this instrument is referenced only by the new version of collection
    const newInstrument = 'NewInstrument'

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
        platforms: [
          { platform: keptPlatform, instruments: [keptInstrument, removedInstrument] },
          { platform: removedPlatform }],
        docNames: [keptDocName, removedDocName]
      }
    )

    await verifyCampaignExistInGraphDb(datasetTitle, keptCampaign)
    await verifyCampaignExistInGraphDb(datasetTitle, removedCampaign)

    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle,
      { platform: keptPlatform, instruments: [keptInstrument, removedInstrument] })
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle, { platform: removedPlatform })

    await verifyDocumentationExistInGraphDb(datasetTitle, keptDocName)
    await verifyDocumentationExistInGraphDb(datasetTitle, removedDocName)

    // update the collection
    await updateCollection(
      'C1237293909-TESTPROV',
      anotherDatasetTitle,
      {
        campaigns: [keptCampaign, newCampaign],
        platforms: [
          { platform: keptPlatform, instruments: [keptInstrument, newInstrument] },
          { platform: newPlatform, instruments: [newInstrument] }],
        docNames: [keptDocName, newDocName]
      }
    )

    // verify the datasetTitle dataset vertex and the removed campaign/documentation/platformInstrument vertex are deleted
    await verifyCampaignNotExistInGraphDb(datasetTitle, removedCampaign)
    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle,
      { platform: keptPlatform, instruments: [removedInstrument] })
    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle, { platform: removedPlatform })
    await verifyDocumentationNotExistInGraphDb(datasetTitle, removedDocName)

    // verify the dataset vertext with the new title exist,
    // verify the campaign/documentation vertices referenced by another collection exist,
    // and there are correct edges between the dataset vertex and the campaign/documentation vertices
    await verifyCampaignExistInGraphDb(anotherDatasetTitle, keptCampaign)
    await verifyCampaignExistInGraphDb(anotherDatasetTitle, newCampaign)
    await verifyPlatformInstrumentsExistInGraphDb(anotherDatasetTitle,
      { platform: keptPlatform, instruments: [keptInstrument, newInstrument] })
    await verifyPlatformInstrumentsExistInGraphDb(anotherDatasetTitle,
      { platform: newPlatform, instruments: [newInstrument] })
    await verifyDocumentationExistInGraphDb(anotherDatasetTitle, keptDocName)
    await verifyDocumentationExistInGraphDb(anotherDatasetTitle, newDocName)
  })
})
