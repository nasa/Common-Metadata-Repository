const sharp = require("sharp");
const fetch = require("isomorphic-unfetch");

exports.slurpImageIntoBuffer = async imageUrl => {
  const thumbnail = await fetch(imageUrl)
    .then(response => {
      if (response.ok) {
        return response;
      }
      return Promise.reject(
        new Error(
          `Failed to fetch ${response.url}: ${response.status} ${
            response.statusText
          }`
        )
      );
    })
    .then(response => response.buffer());
  console.log(`slurped image into buffer from ${imageUrl}`);
  return thumbnail;
};

exports.resizeImage = async (image, height, width) => {
  // If an image needs to be resized, it is because it was not available
  // in cache, so we will always want to cache that image
  try {
    const thumbnail = await sharp(image)
      .resize({
        width,
        height,
        fit: "inside"
      })
      .toFormat("png")
      .toBuffer();

    console.log("imaged resized");
    return thumbnail.toString("base64");
  } catch (err) {
    console.log(`Could not resize image: ${err}`);
    return null;
  }
};

exports.notFound = async (height, width) => {
  const notFoundSvg = sharp("image-unavailable.svg")
    .resize(width, height)
    .toBuffer();

  return notFoundSvg.toString("base64");
};
