/* REDIS */
const redis = require('redis');
const redisHost = process.env.REDIS_URL || 'localhost';
const redisPort = process.env.REDIS_PORT || 6379;
const redisKeyExpireSeconds = process.env.REDIS_KEY_EXPIRE_SECONDS || 84000;


const client = redis.createClient({
  return_buffers: true,
  host: redisHost,
  port: redisPort
});

const { promisify } = require('util');

const getAsync = promisify(client.get).bind(client);

/**
 * cacheImage: Puts the given image in cache. This does not return anything.
 * @param {String} key This is what you use to get the image later
 * @param {Buffer<Image>} image This is what you want the key to get
 */
exports.cacheImage = (key, image) => {
  client.set(key, image, 'EX', redisKeyExpireSeconds, err => {
    if (err) {
      console.error(`Unable to cache image: ${err}`);
    }
  });
};

/**
 * getImageFromCache: fetches image from cache
 * @param {String} key Which image do you want? This is typically ${concept-id}-${height}-${width}
 * @returns {Buffer<Image>} the image associated with given cache key or null if none is found
 */
exports.getImageFromCache = async key => {
  try {
    const image = await getAsync(key);

    // workaround for bad cache entries that have the string "null" for the value
    if (image && image !== 'null') {
      console.log('got image from cache');
      return image;
    }

    console.log('image is not in cache');
    return null;
  } catch (err) {
    console.error(`Could not get image from cache: ${err}`);
    return null;
  }
};
