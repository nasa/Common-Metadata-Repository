import { SSMClient, GetParameterCommand } from '@aws-sdk/client-ssm'

let ssm

/**
 * Given token name, retrieve it from Parameter Store
 * @param {String} param name of parameter to fetch
 * @returns {JSON} server response object from Parameter Store
 */
export const getSecureParam = async (param) => {
  if (!ssm) {
    ssm = new SSMClient({
      region: 'us-east-1'
    })
  }

  const command = new GetParameterCommand({
    Name: param,
    WithDecryption: true
  })

  const request = await ssm.send(command)

  const { Parameter: { Value: value } } = request

  return value
}
