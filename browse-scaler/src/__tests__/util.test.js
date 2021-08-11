const { readFile } = require ('../util');
const nock = require ('nock');
const AWSMock = require('aws-sdk-mock');
const AWS = require ('aws-sdk');

const { getSecureParam,
        slurpImageIntoBuffer } = require ('../util');

afterEach (() => {
    nock.restore ();
});

describe ('slurpImageIntoBuffer', () => {
    test ('handles 200', async () => {
        let starsData = await readFile ('__tests__/stars.jpg');
        const scope = nock (/mock\.com/)
              .get (/.*/)
              .reply (200, starsData);

        const res = await slurpImageIntoBuffer ('http://mock.com/stars.jpg')
        expect (res).toStrictEqual (starsData);
    });

    test ('handles 404', async () => {
        const scope = nock (/mock\.com/)
              .get (/.*/)
              .reply (404);

        const res = await slurpImageIntoBuffer ('http://mock.com/404.jpg')
        expect (res).toBe (null);
    });
});

describe ('getSecureParam', () => {
    test.skip ('handles success', async () => {
        expect (await getSecureParam ('foo')).toBe ('bar');
    });
});
