const sharp = require('sharp');
const fetch = require('node-fetch');

exports.slurpImageIntoBuffer = async imageUrl => {
  const thumbnail = await fetch(imageUrl)
    .then(response => {
      if (response.ok) {
        return response;
      }
      return Promise.reject(
        new Error(`Failed to fetch ${response.url}: ${response.status} ${response.statusText}`)
      );
    })
    .then(response => response.buffer())
    .catch(error => {
      console.error(`Could not slurp image from url ${imageUrl}: ${error}`);
      return null;
    });
  console.log(`slurped image into buffer from ${imageUrl}`);
  return thumbnail;
};

exports.resizeImage = async (image, height, width) => {
  // If an image needs to be resized, it is because it was not available
  // in cache, so we will always want to cache that image
  try {
    const w = parseInt(width) || null;
    const h = parseInt(height) || null;
    console.log(`resizing image to dimensions: {h: ${h}, w: ${w}}`);

    const thumbnail = await sharp(image)
      .resize(w, h, { fit: 'inside' })
      .toFormat('png')
      .toBuffer();

    const imgData = await sharp(thumbnail).metadata();
    console.log(`imaged resized! dimensions: {h: ${imgData.height}, w: ${imgData.width}}`);

    return thumbnail;
  } catch (err) {
    console.log(`Could not resize image: ${err}`);
    return null;
  }
};

exports.notFound = async () => {
  const notFound = await sharp('image-unavailable.svg')
    .toFormat('png')
    .toBuffer();
  console.log(`image not found. got file ${notFound}`);
  return notFound;
};
