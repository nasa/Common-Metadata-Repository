/* REDIS */
const redis = require('redis');
const redisHost = process.env.REDIS_URL || 'localhost';
const redisPort = process.env.REDIS_PORT || 6379;
const redisKeyExpireSeconds = process.env.REDIS_KEY_EXPIRE_SECONDS || 84000;


var redisClient = null;

const { promisify } = require('util');

/**
* getClient: Retrieve the Redis client. Start new client if client is null or not connected.
* @returns {RedisClient} RedisClient object containing the connection to Redis.
*/
function getClient() {
  if (redisClient && redisClient.connected) {
    return redisClient;
  }

  if (redisClient) {
    redisClient.quit();
  }

  redisClient = redis.createClient({
    return_buffers: true,
    host: redisHost,
    port: redisPort
  }).on("error", (err) => {
    console.error(`Failed to connect to Redis with error: ${err}.`);
    redisClient.quit();
  });

  return redisClient;
}

/**
 * cacheImage: Puts the given image in cache. This does not return anything.
 * @param {String} key This is what you use to get the image later
 * @param {Buffer<Image>} image This is what you want the key to get
 */
exports.cacheImage = (key, image) => {
  getClient().set(key, image, 'EX', redisKeyExpireSeconds, err => {
    if (err) {
      console.error(`Unable to cache image ${key}: ${err}`);
    }
  });
};

/**
 * getImageFromCache: fetches image from cache
 * @param {String} key Which image do you want? This is typically ${concept-id}-${height}-${width}
 * @returns {Buffer<Image>} the image associated with given cache key or null if none is found
 */
exports.getImageFromCache = async key => {
  let client = getClient();
  return promisify(client.get).bind(client)(key)
    .catch(err => {
      console.error(`Unable to retrieve image ${key} from Redis.`);
      return null;
    })
    .then(image => {
      // workaround for bad cache entries that have the string "null" for the value
      if (image && image !== 'null') {
        console.log(`got image ${key} from cache`);
        return image;
      }
      console.log(`image ${key} is not in cache`);
      return null;
    });
};
