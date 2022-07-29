import nock from 'nock'

import { mockClient } from 'aws-sdk-client-mock'
import { SQSClient, SendMessageBatchCommand } from '@aws-sdk/client-sqs'

import bootstrapAcls from '../handler'

import * as getEchoToken from '../../utils/cmr/getEchoToken'

const event = { Records: [{ body: '{}' }] }

beforeEach(() => {
  jest.clearAllMocks()
})

const sqsClientMock = mockClient(SQSClient)

describe('bootstrapGremlinServer handler', () => {
  beforeEach(() => {
    sqsClientMock.reset()
  })

  describe('When the response from CMR is an error', () => {
    test('throws an exception', async () => {
      nock(/access-control/)
        .get(/acls/)
        .reply(400, {
          errors: [
            'Parameter [asdf] was not recognized.'
          ]
        })
      jest.spyOn(getEchoToken, 'getEchoToken').mockImplementation(() => undefined)

      const response = await bootstrapAcls(event)

      const { body, statusCode } = response

      expect(body).toBe('Indexing completed')

      expect(statusCode).toBe(400)
    })
  })
 //When the reponse from cmr is in the correct form

})