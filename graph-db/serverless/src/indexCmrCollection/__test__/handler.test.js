import nock from 'nock'

import indexCmrCollection from '../handler'

import { updateCollection, deleteCollection } from '../../testUtil/indexCollection'

import { verifyCollectionPropertiesInGraphDb } from '../../testUtil/verifyCollection'

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
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const conceptId = 'C1237293909-TESTPROV'
    const project1 = 'Project One'
    const platform1 = 'Platform One'
    const instrument1 = 'Instrument One'
    const platform2 = 'Platform Two'
    const relatedUrl = 'https://en.wikipedia.org/wiki/latent_nfi'
    const doi = 'doi:10.16904/envidat.166'

    await updateCollection(
      conceptId,
      datasetTitle,
      {
        projects: [project1],
        platforms: [{ platform: platform1, instruments: [instrument1] }, { platform: platform2 }],
        relatedUrls: [relatedUrl],
        doi
      }
    )

    await verifyCollectionPropertiesInGraphDb(
      {
        datasetTitle,
        conceptId,
        doi
      }
    )

    await verifyProjectExistInGraphDb(datasetTitle, project1)
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle,
      { platform: platform1, instruments: [instrument1] })
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle, { platform: platform2 })
    await verifyRelatedUrlExistInGraphDb(datasetTitle, relatedUrl)
  })

  test('test indexing of a collection with no DOI value', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const conceptId = 'C1237293909-TESTPROV'
    const platform1 = 'Platform One'

    await updateCollection(
      conceptId,
      datasetTitle,
      {
        platforms: [{ platform: platform1 }]
      }
    )

    await verifyCollectionPropertiesInGraphDb(
      {
        datasetTitle,
        conceptId
      }
    )

    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle, { platform: platform1 })
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
    const project1 = 'Project One'
    const platform1 = 'Platform One'
    const instrument1 = 'Instrument One'
    const instrument2 = 'Instrument Two'
    const platform2 = 'Platform Two'
    const relatedUrl = 'https://en.wikipedia.org/wiki/latent_nfi'

    // first index the collection and verify dataset and relatedUrl vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      datasetTitle,
      {
        projects: [project1],
        platforms: [
          { platform: platform1, instruments: [instrument1, instrument2] },
          { platform: platform2 }],
        relatedUrls: [relatedUrl]
      }
    )

    await verifyProjectExistInGraphDb(datasetTitle, project1)
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle,
      { platform: platform1, instruments: [instrument1, instrument2] })
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle, { platform: platform2 })
    await verifyRelatedUrlExistInGraphDb(datasetTitle, relatedUrl)

    // delete the collection and verify dataset and project/relatedUrl vertices are deleted
    await deleteCollection('C1237293909-TESTPROV')
    await verifyProjectNotExistInGraphDb(datasetTitle, project1)
    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle,
      { platform: platform1, instruments: [instrument1, instrument2] })
    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle, { platform: platform2 })
    await verifyRelatedUrlNotExistInGraphDb(datasetTitle, relatedUrl)
  })

  test('test deletion collection not delete linked relatedUrl vertex if it is also linked to another collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const anotherDatasetTitle = 'Another Latent reserves within the Swiss NFI'

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

    // first index the collection and verify dataset and project/relatedUrl vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      datasetTitle,
      {
        projects: [sharedProject, ownProject],
        platforms: [
          { platform: sharedPlatform, instruments: [sharedInstrument, ownInstrument] },
          { platform: ownPlatform }],
        relatedUrls: [sharedDocUrl, ownDocUrl]
      }
    )

    await verifyProjectExistInGraphDb(datasetTitle, sharedProject)
    await verifyProjectExistInGraphDb(datasetTitle, ownProject)
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle,
      { platform: sharedPlatform, instruments: [sharedInstrument, ownInstrument] })
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle, { platform: ownPlatform })
    await verifyRelatedUrlExistInGraphDb(datasetTitle, sharedDocUrl)
    await verifyRelatedUrlExistInGraphDb(datasetTitle, ownDocUrl)

    // index a second collection that reference the same project/relatedUrl vertex
    // and verify dataset and project/relatedUrl vertices are created
    await updateCollection(
      'C1237294000-TESTPROV',
      anotherDatasetTitle,
      {
        projects: [sharedProject],
        platforms: [
          { platform: sharedPlatform, instruments: [sharedInstrument] }],
        relatedUrls: [sharedDocUrl]
      }
    )

    await verifyProjectExistInGraphDb(anotherDatasetTitle, sharedProject)
    await verifyPlatformInstrumentsExistInGraphDb(anotherDatasetTitle,
      { platform: sharedPlatform, instruments: [sharedInstrument] })
    await verifyRelatedUrlExistInGraphDb(anotherDatasetTitle, sharedDocUrl)

    // delete the collection and verify dataset vertex is deleted
    // the project/relatedUrl/platformInstrument vertex that is not referenced by another collection is deleted
    // the project/relatedUrl/platformInstrument vertex that is referenced by another collection is not deleted
    await deleteCollection('C1237293909-TESTPROV')
    await verifyProjectNotExistInGraphDb(datasetTitle, ownProject)
    await verifyProjectExistInGraphDb(anotherDatasetTitle, sharedProject)

    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle,
      { platform: sharedPlatform, instruments: [ownInstrument] })
    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle, { platform: ownPlatform })
    await verifyPlatformInstrumentsExistInGraphDb(anotherDatasetTitle,
      { platform: sharedPlatform, instruments: [sharedInstrument] })

    await verifyRelatedUrlNotExistInGraphDb(datasetTitle, ownDocUrl)
    await verifyRelatedUrlExistInGraphDb(anotherDatasetTitle, sharedDocUrl)
  })

  test('test update collection', async () => {
    const datasetTitle = 'Latent reserves within the Swiss NFI'
    const anotherDatasetTitle = 'Another Latent reserves within the Swiss NFI'

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

    // first index the collection and verify dataset and relatedUrl vertices are created
    await updateCollection(
      'C1237293909-TESTPROV',
      datasetTitle,
      {
        projects: [keptProject, removedProject],
        platforms: [
          { platform: keptPlatform, instruments: [keptInstrument, removedInstrument] },
          { platform: removedPlatform }],
        relatedUrls: [keptDocUrl, removedDocUrl]
      }
    )

    await verifyProjectExistInGraphDb(datasetTitle, keptProject)
    await verifyProjectExistInGraphDb(datasetTitle, removedProject)

    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle,
      { platform: keptPlatform, instruments: [keptInstrument, removedInstrument] })
    await verifyPlatformInstrumentsExistInGraphDb(datasetTitle, { platform: removedPlatform })

    await verifyRelatedUrlExistInGraphDb(datasetTitle, keptDocUrl)
    await verifyRelatedUrlExistInGraphDb(datasetTitle, removedDocUrl)

    // update the collection
    await updateCollection(
      'C1237293909-TESTPROV',
      anotherDatasetTitle,
      {
        projects: [keptProject, newProject],
        platforms: [
          { platform: keptPlatform, instruments: [keptInstrument, newInstrument] },
          { platform: newPlatform, instruments: [newInstrument] }],
        relatedUrls: [keptDocUrl, newDocUrl]
      }
    )

    // verify the datasetTitle dataset vertex and the removed project/relatedUrl/platformInstrument vertex are deleted
    await verifyProjectNotExistInGraphDb(datasetTitle, removedProject)
    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle,
      { platform: keptPlatform, instruments: [removedInstrument] })
    await verifyPlatformInstrumentsNotExistInGraphDb(datasetTitle, { platform: removedPlatform })
    await verifyRelatedUrlNotExistInGraphDb(datasetTitle, removedDocUrl)

    // verify the dataset vertext with the new title exist,
    // verify the project/relatedUrl vertices referenced by another collection exist,
    // and there are correct edges between the dataset vertex and the project/relatedUrl vertices
    await verifyProjectExistInGraphDb(anotherDatasetTitle, keptProject)
    await verifyProjectExistInGraphDb(anotherDatasetTitle, newProject)
    await verifyPlatformInstrumentsExistInGraphDb(anotherDatasetTitle,
      { platform: keptPlatform, instruments: [keptInstrument, newInstrument] })
    await verifyPlatformInstrumentsExistInGraphDb(anotherDatasetTitle,
      { platform: newPlatform, instruments: [newInstrument] })
    await verifyRelatedUrlExistInGraphDb(anotherDatasetTitle, keptDocUrl)
    await verifyRelatedUrlExistInGraphDb(anotherDatasetTitle, newDocUrl)
  })
})
