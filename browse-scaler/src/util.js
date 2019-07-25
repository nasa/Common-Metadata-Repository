const fetch = require('node-fetch');

/**
 * withTimeout: Meant to alleviate image URLs that cannot resolve. Races two promises
 * to keep from waiting too long for a given request. This is mostly used for slurpImageIntoBuffer
 * @param {Integer} millis the maximum allowed length for the promise to run
 * @param {Promise} promise the promise that does the actual work
 */
exports.withTimeout = (millis, promise) => {
  // create two promises: one that does the actual work,
  // and one that will reject them after a given number of milliseconds
  const timeout = new Promise((resolve, reject) => setTimeout(() => reject(null), millis));
  return Promise.race([promise, timeout]).then(value => value);
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
  console.log(`slurped image into buffer from ${imageUrl}`);
  return thumbnail;
};
