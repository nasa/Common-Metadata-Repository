import nock from 'nock';
import { handler } from '../index.js';
import { CMR_ROOT_URL } from '../config.js';

import collectionWithBrowse from './__mocks__/C179003030-ORNL_DAAC.js';
import collectionWithoutBrowse from './__mocks__/C1214587974-SCIOPS.js';

import granuleWithBrowse from './__mocks__/C1711961296-LPCLOUD_granules.js';
import granuleWithBrowseMultipleImages from './__mocks__/G1200460416-ESA.js';
// import granuleWithoutBrowse from './C179003030-ORNL_DAAC_granules.json';

// import invalidCollectionIdResponse from './C000000000-MISSING_NO.json';
// import invalidGranuleIdResponse from './G000000000-MISSING_NO.json';

describe('handler functions', () => {
  describe('get collection(dataset) imagery', () => {
    nock(CMR_ROOT_URL)
      .get(/search\/collections\.json/)
      .reply(200, collectionWithBrowse);

    test('no errors', async () => {
      const res = await handler({
        path: '/datasets/C00000000-FOO',
        queryStringParameters: {
          h: '300',
          w: '400'
        }
      });
      expect(res).toHaveProperty('statusCode', 200);
    });
  });

  describe('get granule imagery', () => {
    nock(CMR_ROOT_URL)
      .get(/search\/collections\.json/)
      .reply(200, collectionWithoutBrowse)
      .get(/search\/granules\.json/)
      .reply(200, granuleWithBrowse);

    test('no errors', async () => {
      const res = await handler({
        path: '/granule/G00000000-FOO',
        queryStringParameters: {
          h: '1280',
          w: '1024'
        }
      });
      expect(res).toHaveProperty('statusCode', 200);
    });
  });

  describe('get granule imagery', () => {
    nock(CMR_ROOT_URL)
      .get(/search\/granules\.json/)
      .reply(200, granuleWithBrowseMultipleImages);

    test('multiple image urls', async () => {
      const res = await handler({
        path: '/granule/G1200460416-ESA',
        queryStringParameters: {
          h: '1280',
          w: '1024',
          imageSrc:
            'https://eoimages.gsfc.nasa.gov/images/imagerecords/151000/151261/atlanticbloom_amo_2023110_lrg.jpg'
        }
      });
      expect(res).toHaveProperty('statusCode', 200);
    });
  });
});
