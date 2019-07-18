/* REDIS */
const redis = require('redis');

const client = redis.createClient({
  return_buffers: true,
  host: process.env.REDIS_URL,
  port: 6379
});

const { promisify } = require('util');

const getAsync = promisify(client.get).bind(client);

exports.cacheImage = (key, image) => {
  client.set(key, image, err => {
    if (err) {
      console.error(`Unable to cache image: ${err}`);
    }
  });
};

exports.getImageFromCache = async key => {
  try {
    const image = await getAsync(key);
    console.log(`got image from cache ${image}`);

    if (image) {
      return image;
    }

    return null;
  } catch (err) {
    console.error(`Could not get image from cache: ${err}`);
    return null;
  }
};
