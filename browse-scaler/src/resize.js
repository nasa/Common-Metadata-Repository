const sharp = require('sharp');

const { withTimeout } = require('./util');
const timeoutInterval = process.env.EXTERNAL_REQUEST_TIMEOUT || 2000;
/**
 * resizeImage: Resize a given image to a given height and width
 * @param {Buffer<Image>} image An image binary contained in a buffer
 * @param {Integer} height How tall do you want the image to be?
 * @param {Integer} width How wide do you want the image to be?
 * @return {Buffer<Image>} This will give you a resized image or null
 */
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

/**
 * notFound: No image available? This will pull the svg file and return it as a Buffer
 * @return {Buffer<Image>} This is what you show the user when an image cannot be found or resized
 */
exports.notFound = async () => {
  console.log("BEFORE sharp"); 
  const notFound = await withTimeout(timeoutInterval, sharp('image-unavailable.svg').toFormat('png').toBuffer());
  console.log(`image not found. got file ${notFound}`);
  return notFound;
};
