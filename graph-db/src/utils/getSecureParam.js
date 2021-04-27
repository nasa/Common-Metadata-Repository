const AWS = require('aws-sdk');

const ssm = new AWS.SSM();

/**
 * getParam: Given token name, retrieve it from Parameter Store
 * @param {String} param name of parameter to fetch
 * @returns {JSON} server response object from Parameter Store
 */
exports.getSecureParam = async (param) => {
  const request = await ssm
    .getParameter({
      Name: param,
      WithDecryption: true,
    })
    .promise();
  return request.Parameter.Value;
};
