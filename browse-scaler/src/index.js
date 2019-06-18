const { slurpImageIntoBuffer, resizeImage, notFound } = require('./resize');
const { getCollectionLevelBrowseImage, getGranuleLevelBrowseImage } = require('./cmr');
const { cacheImage, getImageFromCache } = require('./cache');

const buildResponse = (image, format) => {
  return {
    statusCode: 200,
    headers: {
      'Content-Type': format
    },
    body: image,
    isBase64Encoded: true
  };
};

const getImageUrlFromConcept = async conceptId => {
  if (conceptId.startsWith('G')) {
    return getGranuleLevelBrowseImage(conceptId);
  }

  if (conceptId.startsWith('C')) {
    return getCollectionLevelBrowseImage(conceptId);
  }

  console.log(`Unable to find browse imagery for concept: ${conceptId}`);
  return null;
};

const resizeImageFromConceptId = async (conceptId, height, width) => {
  // If given an image url, fetch the image and resize. If no image
  // exists, return the not found response
  const imageUrl = await getImageUrlFromConcept;

  if (imageUrl === null) {
    const cachedSvg = await getImageFromCache('NOT-FOUND');
    if (cachedSvg) {
      return buildResponse(cachedSvg, 'image/svg');
    }

    const imgNotFound = notFound(height, width);
    cacheImage('NOT-FOUND', imgNotFound);

    return buildResponse(imgNotFound, 'image/svg');
  }

  const cacheKey = `${conceptId}-${height}-${width}`;
  const imageFromCache = await getImageFromCache(cacheKey);
  if (imageFromCache) {
    return buildResponse(imageFromCache, 'image/png');
  }

  const imageBuffer = await slurpImageIntoBuffer(imageUrl);
  const thumbnail = resizeImage(imageBuffer, height, width);
  cacheImage(cacheKey, thumbnail);

  return buildResponse(thumbnail, 'image/png');
};

exports.handler = async event => {
  const conceptId = event.queryStringParameters.concept_id;
  const { height } = event.queryStringParameters;
  const { width } = event.queryStringParameters;

  console.log(`Attempting to resize browse image for concept: ${conceptId}`);
  return resizeImageFromConceptId(conceptId, height, width);
};
