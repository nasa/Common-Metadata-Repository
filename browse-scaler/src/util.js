const fetch = require('node-fetch');

exports.withTimeout = (millis, promise) => {
  // create two promises: one that does the actual work,
  // and one that will reject them after a given number of milliseconds
  const timeout = new Promise((resolve, reject) => setTimeout(() => resolve(null), millis));
  return Promise.race([promise, timeout]).then(value => {
    if (value === null) {
      console.error('Failed to slurp image from buffer, likely due to timeout');
    }
  });
};

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
