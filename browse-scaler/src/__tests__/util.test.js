const nock = require ('nock');
const AWSMock = require('aws-sdk-mock');
const AWS = require ('aws-sdk');

const { getSecureParam,
        readFile,
        slurpImageIntoBuffer,
        withTimeout
      } = require ('../util');

const jsonData = require ('./C179003030-ORNL_DAAC.json');

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

describe ('readFile', () => {
    test ('returns file content', async () => {
        const data = await readFile ('./__tests__/C179003030-ORNL_DAAC.json')
        expect (Buffer.isBuffer (data)).toBeTruthy ();
        expect (JSON.parse (data.toString ())).toEqual (jsonData);
    });
});

describe ('withTimeout', () => {
    test ('timeout expires before promise', async () => {
        const res = await withTimeout (1, new Promise ((resolve, reject) => {
            setTimeout(() => resolve ('not null'), 10)
        }))
        expect (res).toBe (null);
    });

    test ('promise resolves before timeout', async () => {
        const res = await withTimeout (10, new Promise ((resolve, reject) => {
            setTimeout(() => resolve ('not null'), 1)
        }))
        expect (res).toBe ('not null');
    });
});
