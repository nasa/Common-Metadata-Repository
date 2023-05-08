import { SSMClient, GetParameterCommand } from '@aws-sdk/client-ssm';
import fs from 'fs';
import axios from 'axios';
import { AWS_REGION, TIMEOUT_INTERVAL } from './config.js';

let ssm;

if (!ssm) {
  ssm = new SSMClient({
    region: AWS_REGION
  });
}

/**
 * getParam: Given token name, retrieve it from Parameter Store
 * @param {String} param name of parameter to fetch
 * @returns {JSON} server response object from Parameter Store
 */
export const getSecureParam = async param => {
  const command = new GetParameterCommand({ Name: param, WithDecryption: true });
  const request = await ssm.send(command);
  return request.Parameter.Value;
};

export const slurpImageIntoBuffer = async imageUrl => {
  try {
    // axios responseType defaults to json for images we should use arraybuffer type
    const response = await axios({
      url: imageUrl,
      method: 'GET',
      timeout: TIMEOUT_INTERVAL,
      responseType: 'arraybuffer'
    });
    console.log(`${imageUrl} - ${response.url}: ${response.status}`);
    return response.data;
  } catch (error) {
    console.error(`Could not slurp image from url ${imageUrl}: ${error}`);
    return null;
  }
};

// // TODO based on the readme we should be able to get rid of this now that we are node 18
/**
 * This replicates the functionality of promise based readFile function
 * In the node12 fs/promises does not exist yet,
 * Once at node14 this function may be replaced with the native call
 *
 * const fs = require('fs/promises')
 * const buffer = await fs.readFile('<filename>');
 */
// TODO: We should remove this from SC it is only being used for tests
export const readFile = async f => {
  return fs.promises.readFile(f);
};
