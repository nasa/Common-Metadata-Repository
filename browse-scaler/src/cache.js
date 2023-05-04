/* REDIS */
// const redis = require('redis');
import redis from 'redis';
import { promisify } from 'util';
import { REDIS_URL, REDIS_PORT, REDIS_KEY_EXPIRE_SECONDS } from './config.js';
// const { promisify } = require('util');

export const Cache = (function(cfg) {
  // redis singleton
  let instance;

  /**
   * getInstance: Retrieve the Redis client. Start new client if client is null or not connected. //
   * @returns {RedisClient} RedisClient object containing the connection to Redis. //
   */
  function getInstance() {
    if (!instance) {
      instance = redis
        .createClient({
          return_buffers: true,
        host: cfg.REDIS_URL,
        port: cfg.REDIS_PORT
      }).on("error", (err) => {
        console.error(`Failed to connect to Redis with error: ${err}.`);
        instance.quit();
      });
    }
    return instance;
  }
  return { getInstance };
})({ REDIS_PORT, REDIS_URL });

// exports.Cache = Cache;

/**
 * cacheImage: Puts the given image in cache. This does not return anything.
 * @param {String} key This is what you use to get the image later
 * @param {Buffer<Image>} image This is what you want the key to get
 */
export const cacheImage = (key, image, expiration = REDIS_KEY_EXPIRE_SECONDS) => {
  Cache.getInstance().set(key, image, 'EX', expiration, err => {
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
export const getImageFromCache = async key => {
  const client = Cache.getInstance();
  return promisify(client.get)
    .bind(client)(key)
    .catch(err => {
      console.error(`Unable to retrieve image ${key} from Redis due to error ${err}`);
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
