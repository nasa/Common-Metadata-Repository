const fetch = require('node-fetch');
const AWS = require('aws-sdk');
const config = require ('./config');
const fs = require ('fs');

AWS.config.update ({region: config.AWS_REGION});

const ssm = new AWS.SSM();

/**
 * getParam: Given token name, retrieve it from Parameter Store
 * @param {String} param name of parameter to fetch
 * @returns {JSON} server response object from Parameter Store
 */
exports.getSecureParam = async param => {
  const request = await ssm
      .getParameter({
        Name: param,
        WithDecryption: true
      })
      .promise();
  return request.Parameter.Value;
};

/**
 * withTimeout: Meant to alleviate image URLs that cannot resolve. Races two promises
 * to keep from waiting too long for a given request. This is mostly used for slurpImageIntoBuffer
 * @param {Integer} millis the maximum allowed length for the promise to run
 * @param {Promise} promise the promise that does the actual work
 */
exports.withTimeout = (millis, promise) => {
  // create two promises: one that does the actual work,
  // and one that will reject them after a given number of milliseconds
  // eslint-disable-next-line prefer-promise-reject-errors
  const timeout = new Promise((resolve, reject) => setTimeout(() => reject(null), millis));
  // eslint-disable-next-line no-unused-vars
  return Promise.race([promise, timeout]).then(value => value, value => null);
};

/**
 * slurpImageIntoBuffer: fetches images from a given url using the fetch API
 * @param {String} imageUrl link to an image pulled from the metadata of a CMR concept
 * @returns {Buffer<Image>} the image contained in a buffer
 */
exports.slurpImageIntoBuffer = async imageUrl => {
  const thumbnail = await fetch(imageUrl)
      .then(response => {
        if (response.ok) {
          console.log (`${imageUrl} - ${response.url}: ${response.status}`);
          return response.buffer();
        }
        return Promise.reject(
          new Error(`Failed to fetch ${response.url}: ${response.status} ${response.statusText}`)
        );
      })
      .catch(error => {
        console.error(`Could not slurp image from url ${imageUrl}: ${error}`);
        return null;
      });

  return thumbnail;
};

/**
 * This replicates the functionality of promise based readFile function
 * In the node12 fs/promises does not exist yet,
 * Once at node14 this function may be replaced with the native call
 *
 * const fs = require('fs/promises')
 * const buffer = await fs.readFile('<filename>');
 */
exports.readFile = async (f) => {
  return new Promise ((resolve, reject) => {
    fs.readFile (f, (err, data) => {
      if (err) {
        reject (err);
      }
      resolve (data);
    });
  });
}
