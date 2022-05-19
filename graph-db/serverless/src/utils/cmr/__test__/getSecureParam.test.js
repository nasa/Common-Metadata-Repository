import { mockClient } from 'aws-sdk-client-mock'

import { SSMClient, GetParameterCommand } from '@aws-sdk/client-ssm'

import { getSecureParam } from '../getSecureParam'

const ssmClientMock = mockClient(SSMClient)

beforeEach(() => {
  jest.clearAllMocks()
})

describe('getSecureParam', () => {
  beforeEach(() => {
    ssmClientMock.reset()
  })

  test('fetches urs credentials from AWS', async () => {
    ssmClientMock.on(GetParameterCommand).resolves({
      Parameter: { Value: 'SUPER-SECRET-TOKEN' }
    })

    const response = await getSecureParam(`/${process.env.ENVIRONMENT}/graph-db/CMR_ECHO_SYSTEM_TOKEN`)

    expect(response).toEqual('SUPER-SECRET-TOKEN')
    expect(ssmClientMock.calls()).toHaveLength(1)
  })
})
