import nock from 'nock'

import { mockClient } from 'aws-sdk-client-mock'
import { SQSClient, SendMessageBatchCommand } from '@aws-sdk/client-sqs'

import * as chunkArray from '../../chunkArray'

import collectionUmmJson from '../../../testUtil/__mocks__/ummJsonCollection.json'
import emptyUmmResponse from '../../../testUtil/__mocks__/emptyCollectionUmm.json'
import ummSearchResponse from '../../../testUtil/__mocks__/ummSearchResponse.json'

import { fetchPageFromCMR } from '../fetchPageFromCMR'

const OLD_ENV = process.env

beforeEach(() => {
  jest.clearAllMocks()

  // Manage resetting ENV variables
  jest.resetModules()
  process.env = { ...OLD_ENV }
  delete process.env.NODE_ENV
})

afterEach(() => {
  // Restore any ENV variables overwritten in tests
  process.env = OLD_ENV
})

const sqsClientMock = mockClient(SQSClient)

describe('fetchPageFromCMR', () => {
  beforeEach(() => {
    process.env.IS_LOCAL = 'false'

    sqsClientMock.reset()
  })

  test('Empty page', async () => {
    const mockedBody = emptyUmmResponse

    nock(/local-cmr/).get(/search/).reply(200, mockedBody)

    await fetchPageFromCMR({
      searchAfter: null,
      token: null,
      gremlinConnection: global.testGremlinConnection,
      providerId: 'PROV1'
    })

    expect(sqsClientMock.calls()).toHaveLength(0)
  })

  test('Single result', async () => {
    nock(/local-cmr/).get(/search/).reply(200, ummSearchResponse, { 'cmr-search-after': '["aaa", 123, 456]' })
    nock(/local-cmr/).get(/search/).reply(200, emptyUmmResponse, {})

    await fetchPageFromCMR({
      searchAfter: null,
      token: null,
      gremlinConnection: global.testGremlinConnection,
      providerId: null
    })

    const sendMessageBatchMock = sqsClientMock.commandCalls(SendMessageBatchCommand)

    expect(sqsClientMock.calls()).toHaveLength(1)

    expect(sendMessageBatchMock[0].args[0].input).toEqual({
      QueueUrl: 'http://example.com/collectionIndexQueue',
      Entries: [{
        Id: collectionUmmJson.meta['concept-id'],
        MessageBody: JSON.stringify({
          action: 'concept-update',
          'concept-id': collectionUmmJson.meta['concept-id'],
          collection: collectionUmmJson
        })
      }]
    })
  })

  test('Single result with mocked ECHO token', async () => {
    nock(/local-cmr/).get(/search/).reply(200, ummSearchResponse, { 'cmr-search-after': '["aaa", 123, 456]' })

    nock(/local-cmr/).get(/search/).reply(200, emptyUmmResponse, { 'cmr-search-after': '["bbb", 567, 890]' })

    await fetchPageFromCMR({
      searchAfter: 'fake-search-after',
      token: 'SUPER-SECRET-TOKEN',
      gremlinConnection: global.testGremlinConnection,
      providerId: null
    })

    const sendMessageBatchMock = sqsClientMock.commandCalls(SendMessageBatchCommand)

    expect(sqsClientMock.calls()).toHaveLength(1)

    expect(sendMessageBatchMock[0].args[0].input).toEqual({
      QueueUrl: 'http://example.com/collectionIndexQueue',
      Entries: [{
        Id: collectionUmmJson.meta['concept-id'],
        MessageBody: JSON.stringify({
          action: 'concept-update',
          'concept-id': collectionUmmJson.meta['concept-id'],
          collection: collectionUmmJson
        })
      }]
    })
  })

  test('Invalid concept-id', async () => {
    const mockedBody = {
      errors: ["Invalid concept_id [C1234-PROV1]! I can't believe you've done this"]
    }

    nock(/local-cmr/).get(/search/).reply(400, mockedBody)

    await fetchPageFromCMR({
      searchAfter: null,
      token: null,
      gremlinConnection: global.testGremlinConnection,
      providerId: null
    })

    expect(sqsClientMock.calls()).toHaveLength(0)
  })

  test('Function catches and handles errors', async () => {
    nock(/local-cmr/).get(/search/).reply(200, ummSearchResponse, { 'cmr-search-after': '["aaa", 123, 456]' })
    nock(/local-cmr/).get(/search/).reply(200, emptyUmmResponse, { 'cmr-search-after': '["bbb", 567, 890]' })

    const consoleMock = jest.spyOn(console, 'log')
    const errorResponse = jest.spyOn(chunkArray, 'chunkArray').mockImplementationOnce(() => {
      throw new Error('Oh no! I sure hope this exception is handled')
    })

    await fetchPageFromCMR({
      searchAfter: null,
      token: null,
      gremlinConnection: global.testGremlinConnection,
      providerId: null
    })

    expect(consoleMock).toBeCalledWith('Could not complete request due to an error requesting a page from CMR: Error: Oh no! I sure hope this exception is handled')
    expect(errorResponse).toBeCalledTimes(1)
  })
})
