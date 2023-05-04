import { cacheImage, getImageFromCache } from '../cache.js';
import { readFile } from '../util.js';

// import redis from 'redis-mock';
// jest.mock('redis', () => redis);

describe('cache tests', () => {
  test('data round trip', async () => {
    const imgData = await readFile('__tests__/stars.jpg');
    cacheImage('someData', imgData);

    const res = await getImageFromCache('someData');

    expect(res).toStrictEqual(imgData);
  });

  test('"null" string entry', async () => {
    cacheImage('itsnull', 'null');

    const res = await getImageFromCache('itsnull');

    // the actual string null
    expect(res.toString()).toBe('null');
  });

  test('key does not exist', async () => {
    const res = await getImageFromCache('idontexist');
    expect(res).toBe(null);
  });
});
