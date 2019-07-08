const { slurpImageIntoBuffer, resizeImage, notFound } = require('./resize');
const { getCollectionLevelBrowseImage, getGranuleLevelBrowseImage } = require('./cmr');
const { cacheImage, getImageFromCache } = require('./cache');

const buildResponse = (image, format) => {
  console.log(`Image for response: ${image}`);
  console.log(`Image format: ${format}`);
  return {
    statusCode: 200,
    headers: {
      'Content-Type': format
    },
    body: image,
    isBase64Encoded: true
  };
};

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

const resizeImageFromConceptId = async (conceptType, conceptId, height, width) => {
  // If given an image url, fetch the image and resize. If no image
  // exists, return the not found response
  const imageUrl = await getImageUrlFromConcept(conceptId, conceptType);

  if (imageUrl === null) {
    // const cachedSvg = await getImageFromCache("NOT-FOUND");
    // if (cachedSvg) {
    //   return buildResponse(cachedSvg, "image/svg");
    // }

    const imgNotFound = await notFound(height, width);
    // cacheImage("NOT-FOUND", imgNotFound);

    return buildResponse(imgNotFound, 'image/svg');
  }

  // const cacheKey = `${conceptId}-${height}-${width}`;
  // const imageFromCache = await getImageFromCache(cacheKey);
  // if (imageFromCache) {
  //   return buildResponse(imageFromCache, "image/png");
  // }

  const imageBuffer = await slurpImageIntoBuffer(imageUrl);
  const thumbnail = await resizeImage(imageBuffer, height, width);
  // cacheImage(cacheKey, thumbnail);

  return buildResponse(thumbnail, 'image/png');
};

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

exports.handler = async (event, context) => {
  const args = parseArguments(event);
  console.log(`Attempting to resize browse image for concept: ${JSON.stringify(args)}`);

  return resizeImageFromConceptId(args.conceptType, args.conceptId, args.h, args.w);
};
