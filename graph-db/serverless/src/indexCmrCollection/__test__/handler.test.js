import nock from 'nock'

import indexCmrCollection from '../handler'

import { updateCollection, deleteCollection } from '../../testUtil/indexCollection'

import { verifyCollectionPropertiesInGraphDb } from '../../testUtil/verifyCollection'

import * as fetchCollectionPermittedGroups from '../../utils/cmr/fetchCollectionPermittedGroups'

import {
  verifyRelatedUrlExistInGraphDb, verifyRelatedUrlNotExistInGraphDb
} from '../../testUtil/verifyRelatedUrl'

import {
  verifyProjectExistInGraphDb, verifyProjectNotExistInGraphDb
} from '../../testUtil/verifyProject'

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
    const collectionTitle = 'Latent reserves within the Swiss NFI'
    const conceptId = 'C1237293909-TESTPROV'
    const project1 = 'Project One'
    const platform1 = 'Platform One'
    const instrument1 = 'Instrument One'
    const platform2 = 'Platform Two'
    const relatedUrl = 'https://en.wikipedia.org/wiki/latent_nfi'
    const doi = 'doi:10.16904/envidat.166'

    await updateCollection(
      conceptId,
      collectionTitle,
      {
        projects: [project1],
        platforms: [{ platform: platform1, instruments: [instrument1] }, { platform: platform2 }],
        relatedUrls: [relatedUrl],
        doi
      }
    )

    await verifyCollectionPropertiesInGraphDb(
      {
        collectionTitle,
        conceptId,
        doi
      }
    )

    await verifyProjectExistInGraphDb(collectionTitle, project1)
    await verifyPlatformInstrumentsExistInGraphDb(collectionTitle,
      {
        platform: platform1,
        instruments: [instrument1]
      })
    await verifyPlatformInstrumentsExistInGraphDb(collectionTitle, { platform: platform2 })
    await verifyRelatedUrlExistInGraphDb(collectionTitle, relatedUrl)
  })

  test('test indexing of a collection with no DOI value', async () => {
    const collectionTitle = 'Latent reserves within the Swiss NFI'
    const conceptId = 'C1237293909-TESTPROV'
    const platform1 = 'Platform One'

    await updateCollection(
      conceptId,
      collectionTitle,
      {
        platforms: [{ platform: platform1 }]
      }
    )

    await verifyCollectionPropertiesInGraphDb(
      {
        collectionTitle,
        conceptId
      }
    )

    await verifyPlatformInstrumentsExistInGraphDb(collectionTitle, { platform: platform1 })
  })

  test('test index of not found collection', async () => {
    nock(/local-cmr/)
      .get(/collections/)
      .reply(200, {
        hits: 0,
        took: 6,
        items: []
      })

    const fetchGroupsMock = jest.spyOn(fetchCollectionPermittedGroups, 'fetchCollectionPermittedGroups').mockReturnValueOnce([])

    const event = getEvent('C123755555-TESTPROV', 'concept-update')

    const consoleMock = jest.spyOn(console, 'log')

    const indexed = await indexCmrCollection(event)

    expect(fetchGroupsMock).toBeCalledTimes(0)

    expect(consoleMock).toBeCalledTimes(1)
    expect(consoleMock).toBeCalledWith('Skip indexing of collection [C123755555-TESTPROV] as it is not found in CMR')

    const { body, statusCode } = indexed

    expect(body).toBe('Successfully indexed 0 collection(s). Skipped 1 collection(s).')
    expect(statusCode).toBe(200)
  })

  test('test indexing of a broken collection', async () => {
    // Pass an error to the response of fetchCmrCollection
    nock(/local-cmr/)
      .get(/collections/)
      .replyWithError('Error Collection not found', { statusCode: 404 })

    const event = getEvent('C123755555-TESTPROV', 'concept-update')

    const consoleMock = jest.spyOn(console, 'log')

    const indexed = await indexCmrCollection(event)

    expect(consoleMock).toBeCalledTimes(3)
    expect(consoleMock).toBeCalledWith('Could not complete request due to error: Error: Error Collection not found')
    expect(consoleMock).toBeCalledWith('Collection FAILED during indexing process there may be an issue with the collection verify that the collection for the given env: ', 'C123755555-TESTPROV')
    expect(consoleMock).toBeCalledWith('Error indexing collection, Execption was thrown: ', new Error('Cannot read properties of null (reading \'data\')'))

    const { body } = indexed

    expect(body).toBe('Successfully indexed 0 collection(s).')
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
    const collectionTitle = 'Latent reserves within the Swiss NFI'
    const project1 = 'Project One'
    const platform1 = 'Platform One'
    const instrument1 = 'Instrument One'
    const instrument2 = 'Instrument Two'
    const platform2 = 'Platform Two'
    const relatedUrl = 'https://en.wikipedia.org/wiki/latent_nfi'

    // first index the collection and verify collection and relatedUrl vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      collectionTitle,
      {
        projects: [project1],
        platforms: [
          { platform: platform1, instruments: [instrument1, instrument2] },
          { platform: platform2 }],
        relatedUrls: [relatedUrl]
      }
    )

    await verifyProjectExistInGraphDb(collectionTitle, project1)
    await verifyPlatformInstrumentsExistInGraphDb(collectionTitle,
      { platform: platform1, instruments: [instrument1, instrument2] })
    await verifyPlatformInstrumentsExistInGraphDb(collectionTitle, { platform: platform2 })
    await verifyRelatedUrlExistInGraphDb(collectionTitle, relatedUrl)

    // delete the collection and verify collection and project/relatedUrl vertices are deleted
    await deleteCollection('C1237293909-TESTPROV')
    await verifyProjectNotExistInGraphDb(collectionTitle, project1)
    await verifyPlatformInstrumentsNotExistInGraphDb(collectionTitle,
      { platform: platform1, instruments: [instrument1, instrument2] })
    await verifyPlatformInstrumentsNotExistInGraphDb(collectionTitle, { platform: platform2 })
    await verifyRelatedUrlNotExistInGraphDb(collectionTitle, relatedUrl)
  })

  test('test deletion collection not delete linked relatedUrl vertex if it is also linked to another collection', async () => {
    const collectionTitle = 'Latent reserves within the Swiss NFI'
    const anothercollectionTitle = 'Another Latent reserves within the Swiss NFI'

    // this project is referenced by two collections
    const sharedProject = 'SharedProject'
    // this project is referenced only by one collection
    const ownProject = 'OwnProject'

    // this platform is referenced by two collections
    const sharedPlatform = 'SharedPlatform'
    // this platform is referenced by one collection
    const ownPlatform = 'OwnPlatform'
    const sharedInstrument = 'SharedInstrument'
    const ownInstrument = 'OwnInstrument'

    // this relatedUrl url is referenced by two collections
    const sharedDocUrl = 'https://en.wikipedia.org/wiki/latent_nfi'
    // this relatedUrl url is referenced only by one collection
    const ownDocUrl = 'https://en.wikipedia.org/wiki/latent_nfi2'

    // first index the collection and verify collection and project/relatedUrl vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      collectionTitle,
      {
        projects: [sharedProject, ownProject],
        platforms: [
          { platform: sharedPlatform, instruments: [sharedInstrument, ownInstrument] },
          { platform: ownPlatform }],
        relatedUrls: [sharedDocUrl, ownDocUrl]
      }
    )

    await verifyProjectExistInGraphDb(collectionTitle, sharedProject)
    await verifyProjectExistInGraphDb(collectionTitle, ownProject)
    await verifyPlatformInstrumentsExistInGraphDb(collectionTitle,
      { platform: sharedPlatform, instruments: [sharedInstrument, ownInstrument] })
    await verifyPlatformInstrumentsExistInGraphDb(collectionTitle, { platform: ownPlatform })
    await verifyRelatedUrlExistInGraphDb(collectionTitle, sharedDocUrl)
    await verifyRelatedUrlExistInGraphDb(collectionTitle, ownDocUrl)

    // index a second collection that reference the same project/relatedUrl vertex
    // and verify collection and project/relatedUrl vertices are created
    await updateCollection(
      'C1237294000-TESTPROV',
      anothercollectionTitle,
      {
        projects: [sharedProject],
        platforms: [
          { platform: sharedPlatform, instruments: [sharedInstrument] }],
        relatedUrls: [sharedDocUrl]
      }
    )

    await verifyProjectExistInGraphDb(anothercollectionTitle, sharedProject)
    await verifyPlatformInstrumentsExistInGraphDb(anothercollectionTitle,
      { platform: sharedPlatform, instruments: [sharedInstrument] })
    await verifyRelatedUrlExistInGraphDb(anothercollectionTitle, sharedDocUrl)

    // delete the collection and verify collection vertex is deleted
    // the project/relatedUrl/platformInstrument vertex that is not referenced by another collection is deleted
    // the project/relatedUrl/platformInstrument vertex that is referenced by another collection is not deleted
    await deleteCollection('C1237293909-TESTPROV')
    await verifyProjectNotExistInGraphDb(collectionTitle, ownProject)
    await verifyProjectExistInGraphDb(anothercollectionTitle, sharedProject)

    await verifyPlatformInstrumentsNotExistInGraphDb(collectionTitle,
      { platform: sharedPlatform, instruments: [ownInstrument] })
    await verifyPlatformInstrumentsNotExistInGraphDb(collectionTitle, { platform: ownPlatform })
    await verifyPlatformInstrumentsExistInGraphDb(anothercollectionTitle,
      { platform: sharedPlatform, instruments: [sharedInstrument] })

    await verifyRelatedUrlNotExistInGraphDb(collectionTitle, ownDocUrl)
    await verifyRelatedUrlExistInGraphDb(anothercollectionTitle, sharedDocUrl)
  })

  test('test update collection', async () => {
    const collectionTitle = 'Latent reserves within the Swiss NFI'
    const anothercollectionTitle = 'Another Latent reserves within the Swiss NFI'

    // this project is referenced by both the old and new version of the collection
    const keptProject = 'KeptProject'
    // this project is referenced only by the old version of collection
    const removedProject = 'RemovedProject'
    // this project is referenced only by the new version of collection
    const newProject = 'NewProject'

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

    // this relatedUrl url is referenced by both the old and new version of the collection
    const keptDocUrl = 'https://en.wikipedia.org/wiki/latent_nfi'
    // this relatedUrl url is referenced only by the old version of collection
    const removedDocUrl = 'https://en.wikipedia.org/wiki/latent_nfi_old'
    // this relatedUrl url is referenced only by the new version of collection
    const newDocUrl = 'https://en.wikipedia.org/wiki/latent_nfi_new'

    // first index the collection and verify collection and relatedUrl vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      collectionTitle,
      {
        projects: [keptProject, removedProject],
        platforms: [
          { platform: keptPlatform, instruments: [keptInstrument, removedInstrument] },
          { platform: removedPlatform }],
        relatedUrls: [keptDocUrl, removedDocUrl]
      }
    )

    await verifyProjectExistInGraphDb(collectionTitle, keptProject)
    await verifyProjectExistInGraphDb(collectionTitle, removedProject)

    await verifyPlatformInstrumentsExistInGraphDb(collectionTitle,
      { platform: keptPlatform, instruments: [keptInstrument, removedInstrument] })
    await verifyPlatformInstrumentsExistInGraphDb(collectionTitle, { platform: removedPlatform })

    await verifyRelatedUrlExistInGraphDb(collectionTitle, keptDocUrl)
    await verifyRelatedUrlExistInGraphDb(collectionTitle, removedDocUrl)

    // update the collection
    await updateCollection(
      'C1237293909-TESTPROV',
      anothercollectionTitle,
      {
        projects: [keptProject, newProject],
        platforms: [
          { platform: keptPlatform, instruments: [keptInstrument, newInstrument] },
          { platform: newPlatform, instruments: [newInstrument] }],
        relatedUrls: [keptDocUrl, newDocUrl]
      }
    )

    // verify the collectionTitle collection vertex and the removed project/relatedUrl/platformInstrument vertex are deleted
    await verifyProjectNotExistInGraphDb(collectionTitle, removedProject)
    await verifyPlatformInstrumentsNotExistInGraphDb(collectionTitle,
      { platform: keptPlatform, instruments: [removedInstrument] })
    await verifyPlatformInstrumentsNotExistInGraphDb(collectionTitle, { platform: removedPlatform })
    await verifyRelatedUrlNotExistInGraphDb(collectionTitle, removedDocUrl)

    // verify the collection vertext with the new title exist,
    // verify the project/relatedUrl vertices referenced by another collection exist,
    // and there are correct edges between the collection vertex and the project/relatedUrl vertices
    await verifyProjectExistInGraphDb(anothercollectionTitle, keptProject)
    await verifyProjectExistInGraphDb(anothercollectionTitle, newProject)
    await verifyPlatformInstrumentsExistInGraphDb(anothercollectionTitle,
      { platform: keptPlatform, instruments: [keptInstrument, newInstrument] })
    await verifyPlatformInstrumentsExistInGraphDb(anothercollectionTitle,
      { platform: newPlatform, instruments: [newInstrument] })
    await verifyRelatedUrlExistInGraphDb(anothercollectionTitle, keptDocUrl)
    await verifyRelatedUrlExistInGraphDb(anothercollectionTitle, newDocUrl)
  })
})
