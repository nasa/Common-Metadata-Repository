const cache = require ('../cache');
const { readFile } = require ('../util');

describe ('cache tests', () => {
    test ('data round trip', async () => {
        const imgData = await readFile ('__tests__/stars.jpg');
        cache.cacheImage ('someData', imgData);

        const res = await cache.getImageFromCache ('someData');

        expect (res).toStrictEqual (imgData);
    });

    test ('\"null\" string entry', async () => {
        cache.cacheImage ('itsnull', 'null');

        const res = await cache.getImageFromCache ('itsnull');

        // the actual string null
        expect (res.toString ()).toBe ('null');
    });

    test ('key does not exist', async () => {
        const res = await cache.getImageFromCache ('idontexist');
        expect (res).toBe (null);
    });
});
