const { resizeImage, notFound } = require('./resize');
const { getCollectionLevelBrowseImage, getGranuleLevelBrowseImage } = require('./cmr');
const { cacheImage, getImageFromCache } = require('./cache');
const { withTimeout, slurpImageIntoBuffer } = require('./util');

const timeoutInterval = process.env.EXTERNAL_REQUEST_TIMEOUT || 2000;

/**
 * buildResponse: assembles response body to avoid code duplication
 * @param {Buffer<Image>} image
 * @returns {JSON} assembled response object with image as a base64 string
 */
const buildResponse = image => {
  console.log(`Image for response: ${image}`);
  return {
    statusCode: 200,
    headers: {
      'Content-Type': 'image/png'
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
 * 'dataset' refers to collections
 * @returns {String} image url or null
 */
const getImageUrlFromConcept = async (conceptId, conceptType) => {
  console.log(`Concept id: ${conceptId}`);
  if (conceptId === null || conceptId === undefined) {
    return null;
  }

  if (conceptType === 'granules') {
    return getGranuleLevelBrowseImage(conceptId);
  }

  if (conceptType === 'datasets') {
    return getCollectionLevelBrowseImage(conceptId);
  }

  console.log(`Unable to find browse imagery for concept: ${conceptId}`);
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
const resizeImageFromConceptId = async (conceptType, conceptId, height, width) => {
  const cacheKey = `${conceptId}-${height}-${width}`;
  const imageFromCache = await getImageFromCache(cacheKey);
  if (imageFromCache) {
    console.log(`returning cached image ${cacheKey}`);
    return buildResponse(imageFromCache);
  }

  // If given an image url, fetch the image and resize. If no image
  // exists, return the not found response
  const imageUrl = await getImageUrlFromConcept(conceptId, conceptType);
  const imageBuffer = await withTimeout(timeoutInterval, slurpImageIntoBuffer(imageUrl));

  if (imageUrl === null || imageBuffer === null || imageBuffer === undefined) {
    const imgNotFound = await notFound();
    return buildResponse(imgNotFound);
  }

  const thumbnail = await resizeImage(imageBuffer, height, width);
  cacheImage(cacheKey, thumbnail);

  return buildResponse(thumbnail);
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
    w: event.queryStringParameters.w
  };

  if (args.conceptId === null) {
    throw new Error('Please supply a concept id');
  }

  if (args.h === null && args.w === null) {
    throw new Error('Please supply at least a height or a width');
  }

  return args;
};

exports.handler = async event => {
  const args = parseArguments(event);
  console.log(`Attempting to resize browse image for concept: ${JSON.stringify(args)}`);

  return await resizeImageFromConceptId(args.conceptType, args.conceptId, args.h, args.w);
};
