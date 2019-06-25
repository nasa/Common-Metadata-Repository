const { slurpImageIntoBuffer, resizeImage, notFound } = require("./resize");
const {
  getCollectionLevelBrowseImage,
  getGranuleLevelBrowseImage
} = require("./cmr");
const { cacheImage, getImageFromCache } = require("./cache");

const buildResponse = (image, format) => {
  return {
    statusCode: 200,
    headers: {
      "Content-Type": format
    },
    body: image,
    isBase64Encoded: true
  };
};

const getImageUrlFromConcept = async (conceptId, conceptType) => {
  if (conceptId === null || conceptId === undefined) {
    return null;
  }

  if (conceptType === "granules") {
    return getGranuleLevelBrowseImage(conceptId);
  }

  if (conceptType === "datasets") {
    return getCollectionLevelBrowseImage(conceptId);
  }

  console.log(`Unable to find browse imagery for concept: ${conceptId}`);
  return null;
};

const resizeImageFromConceptId = async (
  conceptType,
  conceptId,
  height,
  width
) => {
  // If given an image url, fetch the image and resize. If no image
  // exists, return the not found response
  const imageUrl = await getImageUrlFromConcept(conceptId, conceptType);

  if (imageUrl === null) {
    const cachedSvg = await getImageFromCache("NOT-FOUND");
    if (cachedSvg) {
      return buildResponse(cachedSvg, "image/svg");
    }

    const imgNotFound = notFound(height, width);
    cacheImage("NOT-FOUND", imgNotFound);

    return buildResponse(imgNotFound, "image/svg");
  }

  const cacheKey = `${conceptId}-${height}-${width}`;
  const imageFromCache = await getImageFromCache(cacheKey);
  if (imageFromCache) {
    return buildResponse(imageFromCache, "image/png");
  }

  const imageBuffer = await slurpImageIntoBuffer(imageUrl);
  const thumbnail = resizeImage(imageBuffer, height, width);
  cacheImage(cacheKey, thumbnail);

  return buildResponse(thumbnail, "image/png");
};

const parseArguments = async event => {
  const pathParams = event.path
    .split("/")
    .filter(param => param !== "browse-scaler" && param !== "browse_images");

  const arguments = {
    conceptType: pathParams[1],
    conceptId: pathParams[2],
    h: event.queryStringParameters.h,
    w: event.queryStringParameters.w
  };

  if (arguments.conceptId === null) {
    throw new Error("Please supply a concept id");
  }

  if (arguments.h === null && arguments.w === null) {
    throw new Error("Please supply at least a height or a width");
  }

  return arguments;
};

exports.handler = async (event, context) => {
  const arguments = parseArguments(event);

  console.log(
    `Attempting to resize browse image for concept: ${arguments.conceptId}`
  );
  return resizeImageFromConceptId(
    arguments.conceptType,
    arguments.conceptId,
    arguments.h,
    arguments.w
  );
};
