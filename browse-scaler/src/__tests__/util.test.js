const fs = require ('fs');
const nock = require ('nock');
const AWSMock = require('aws-sdk-mock');
const AWS = require ('aws-sdk');

const { getSecureParam,
        slurpImageIntoBuffer } = require ('../util');

afterEach (() => {
    nock.restore ();
});

/**
 * This replicates the functionality of promise based readFile function
 * In the node12 fs/promises does not exist yet, 
 * Once at node14 this function may be replaced with the native call
 * 
 * const fs = require('fs/promises')
 * const buffer = await fs.readFile('<filename>');
 */
async function readFile (f) {
    return new Promise ((resolve, reject) => {
        fs.readFile (f, (err, data) => {
            if (err) {
                reject (err);
            }
            resolve (data);
        });
    });
}

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
