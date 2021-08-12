const nock = require ('nock');
const AWSMock = require('aws-sdk-mock');
const AWS = require ('aws-sdk');
const fs = require ('fs');

const { getSecureParam,
    readFile,
    slurpImageIntoBuffer,
    withTimeout
    } = require ('../util');

const jsonData = require ('./C179003030-ORNL_DAAC.json');

describe ('slurpImageIntoBuffer', () => {
    let starsData = fs.readFileSync ('./__tests__/stars.jpg');

    nock ('http://mock.com')
        .get (/200/)
        .reply (200, starsData)
        .get (/404/)
        .reply (404);
  
  test ('handles 200', async () => {
      const res = await slurpImageIntoBuffer ('http://mock.com/200')
      expect (res).toStrictEqual (starsData);
  });
  
  test ('handles 404', async () => {
    const res = await slurpImageIntoBuffer ('http://mock.com/404')
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
