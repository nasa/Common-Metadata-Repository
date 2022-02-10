const cache = require ('../cache');
const { readFile } = require ('../util');

beforeEach (async () => { await cache.clearToken() }) 

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

    test ('When I request a token from an empty cache, then it is null', async () => {
        const res = await cache.getTokenInCache();
        expect (res).toBe (null);
    });

    test ('When I set a token in cache, then I can retrieve it', async () => {
        const mytoken = 'token'
        await cache.setTokenInCache(mytoken);
        const res = await cache.getTokenInCache();
        expect (res).toBe (mytoken);;
    });
});
