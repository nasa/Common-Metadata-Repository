const AWS = require('aws-sdk')

let ssm

/**
 * getParam: Given token name, retrieve it from Parameter Store
 * @param {String} param name of parameter to fetch
 * @returns {JSON} server response object from Parameter Store
 */
exports.getSecureParam = async (param) => {
  if (ssm == null) {
    ssm = new AWS.SSM()
  }

  const request = await ssm
    .getParameter({
      Name: param,
      WithDecryption: true
    })
    .promise()
  return request.Parameter.Value
}
