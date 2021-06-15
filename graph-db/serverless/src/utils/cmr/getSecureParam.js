import AWS from 'aws-sdk'

let ssm

/**
 * getParam: Given token name, retrieve it from Parameter Store
 * @param {String} param name of parameter to fetch
 * @returns {JSON} server response object from Parameter Store
 */
export const getSecureParam = async (param) => {
  if (!ssm) {
    ssm = new AWS.SSM({
      region: 'us-east-1'
    })
  }

  const request = await ssm
    .getParameter({
      Name: param,
      WithDecryption: true
    })
    .promise()

  const { Parameter: { Value: value } } = request

  return value
}
