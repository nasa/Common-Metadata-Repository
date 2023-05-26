import nock from 'nock';
import { readFileSync } from 'fs';

import { getSecureParam, readFile, slurpImageIntoBuffer } from '../util.js';

import jsonData from './mock_responses/C179003030-ORNL_DAAC.json' assert {type: 'json'};

describe('slurpImageIntoBuffer', () => {
  const starsData = readFileSync('./__tests__/stars.jpg');

  nock('http://mock.com')
    .get(/200/)
    .reply(200, starsData)
    .get(/404/)
    .reply(404);

  test('handles 200', async () => {
    const res = await slurpImageIntoBuffer('http://mock.com/200');
    expect(res).toStrictEqual(starsData);
  });

  test('handles 404', async () => {
    const res = await slurpImageIntoBuffer('http://mock.com/404');
    expect(res).toBe(null);
  });
});

describe('getSecureParam', () => {
  test.skip('handles success', async () => {
    expect(await getSecureParam('foo')).toBe('bar');
  });
});

describe('readFile', () => {
  test('returns file content', async () => {
    const data = await readFile('./__tests__/mock_responses/C179003030-ORNL_DAAC.json');
    expect(Buffer.isBuffer(data)).toBeTruthy();
    expect(JSON.parse(data.toString())).toEqual(jsonData);
  });
});
