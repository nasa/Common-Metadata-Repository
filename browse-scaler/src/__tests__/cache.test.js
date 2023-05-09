import { cacheImage, getImageFromCache, closeInstance } from '../cache.js';
import { readFile } from '../util.js';

// TODO: We should actually mock redis
// TODO: Right now we require a real instance to be up to pass test
// Needed function to close connection for tests opening redis connection to avoid open handles
afterAll(() => closeInstance());

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
