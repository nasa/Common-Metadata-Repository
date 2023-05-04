// const { resizeImage, notFound } = require('./resize');

import { resizeImage, notFound } from './resize.js';

// const { getCollectionLevelBrowseImage, getGranuleLevelBrowseImage } = require('./cmr');

import { getCollectionLevelBrowseImage, getGranuleLevelBrowseImage } from './cmr.js';

// const { cacheImage, getImageFromCache } = require('./cache');

import { cacheImage, getImageFromCache } from './cache.js';

// const { withTimeout, slurpImageIntoBuffer } = require('./util');

import { withTimeout, slurpImageIntoBuffer } from './util.js';

// const config = require('./config');

// eslint-disable-next-line import/extensions
import { TIMEOUT_INTERVAL } from './config.js';

/**
 * buildResponse: assembles response body to avoid code duplication
 * @param {Buffer<Image>} image
 * @returns {JSON} assembled response object with image as a base64 string
 */
const buildResponse = image => {
  return {
    statusCode: 200,
    headers: {
      'Content-Type': 'image/png',
      'Access-Control-Allow-Origin': '*'
    },
    body: image.toString('base64'),
    isBase64Encoded: true
  };
};

/**
 * getImageUrlFromConcept: call appropriate cmr.js function based on
 * given concept type to extract image url from metadata
 * @param {String} conceptId CMR concept id
 * @param {String} conceptType CMR concept type
 * @param {string} imageSrc optional argument that specifies which image should be chosen defaults to empty str
 * 'dataset' refers to collections
 * @returns {String} image url or null
 */
const getImageUrlFromConcept = async (conceptId, conceptType, imageSrc) => {
  console.log(`Concept id: ${conceptId}`);

  if (!conceptId) {
    return null;
  }

  if (conceptType === 'granules') {
    return getGranuleLevelBrowseImage(conceptId, imageSrc);
  }
  if (conceptType === 'datasets') {
    return getCollectionLevelBrowseImage(conceptId);
  }

  console.error(
    `Unable to fetch imagery for concept-type: ${conceptType} on concept-id ${conceptId}`
  );
  return null;
};

/**
 * resizeImageFromConceptId: call necessary helper functions to resize an image
 * associated with a given concept-id
 * @param {String} conceptType
 * @param {String} conceptId
 * @param {Integer} height
 * @param {Integer} width
 * @returns {JSON} server response object
 */
const resizeImageFromConceptId = async (conceptType, conceptId, height, width, imageSrc) => {
  // If the imageSrc is empty then that will be the cache for the default image
  // TODO there may be one copy of the cached image in that case
  const cacheKey = `${conceptId}-${height}-${width}-${imageSrc}`;
  const imageFromCache = await getImageFromCache(cacheKey);
  if (imageFromCache) {
    console.log(`Returning cached image ${cacheKey}`);
    return buildResponse(imageFromCache);
  }

  // If given an image url, fetch the image and resize. If no valid image
  // exists, return the not found response
  // const imageUrl = await withTimeout(
  //   TIMEOUT_INTERVAL,
  //   getImageUrlFromConcept(conceptId, conceptType, imageSrc)
  // );
  const imageUrl = await getImageUrlFromConcept(conceptId, conceptType, imageSrc);
  console.log('🚀 ~ file: index.js:94 ~ resizeImageFromConceptId ~ imageUrl:', imageUrl);
  // If the url is not `null`, `undefined`, or an empty string try to grab the image and resize it
  if (imageUrl) {
    // const imageBuffer = await withTimeout(TIMEOUT_INTERVAL, slurpImageIntoBuffer(imageUrl));
    const imageBuffer = await slurpImageIntoBuffer(imageUrl);
    console.log('🚀 ~ file: index.js:99 ~ resizeImageFromConceptId ~ imageBuffer:', imageBuffer)
    // TODO we need to be consistent either use axios or use the timeout feature
    if (imageBuffer) {
      const thumbnail = await resizeImage(imageBuffer, height, width);
      if (thumbnail) {
        cacheImage(cacheKey, thumbnail);
        return buildResponse(thumbnail);
      }
    }
  }

  console.log(`No image found for: ${conceptId}. Returning default image.`);
  const imgNotFound = await notFound();
  // scale to requested size
  const thumbnail = await resizeImage(imgNotFound, height, width);

  if (thumbnail) {
    return buildResponse(thumbnail);
  }

  // should never reach this point, but just in case we send back the full size no-image
  return buildResponse(imgNotFound);
};

/**
 * parseArguments: pull relevant parameters from the Lambda event
 * object
 * @param {JSON} event
 * @returns {JSON} parsed arguments that were passed to the server
 */
const parseArguments = event => {
  const pathParams = event.path
    .split('/')
    .filter(param => param !== 'browse-scaler' && param !== 'browse_images' && param !== '');

  const args = {
    conceptType: pathParams[0],
    conceptId: pathParams.pop(),
    h: event.queryStringParameters.h,
    w: event.queryStringParameters.w,
    imageSrc: event.queryStringParameters.imageSrc
  };

  if (!args.conceptId) {
    throw new Error('Please supply a concept id');
  }

  if (!args.h && !args.w) {
    throw new Error('Please supply at least a height or a width');
  }

  return args;
};

/**
 * handler: execute the event task
 * object
 * @param {JSON} event
 * @returns {JSON} Response containing encoded image
 */
export const handler = async event => {
  const args = parseArguments(event);
  const { conceptType, conceptId, w, h, imageSrc = '' } = args;
  console.log(`Attempting to resize browse image for concept: ${JSON.stringify(args)}`);
  const resizedImage = await resizeImageFromConceptId(conceptType, conceptId, h, w, imageSrc);
  return resizedImage;
};
