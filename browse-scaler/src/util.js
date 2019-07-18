const fetch = require('node-fetch');

exports.withTimeout = (millis, promise) => {
  // create two promises: one that does the actual work,
  // and one that will reject them after a given number of milliseconds
  const timeout = new Promise((resolve, reject) => setTimeout(() => reject(null), millis));
  return Promise.race([promise, timeout]).then(value => value);
};

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
