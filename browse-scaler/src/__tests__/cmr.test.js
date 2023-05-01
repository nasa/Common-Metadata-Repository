const {
  getBrowseImageFromConcept,
  getCollectionLevelBrowseImage,
  getGranuleLevelBrowseImage,
  getEchoToken
} = require('../cmr');
const config = require('../config');

const nock = require ('nock');
const AWS = require('aws-sdk-mock');

const collectionWithBrowse = require('./C179003030-ORNL_DAAC.json');
const collectionWithoutBrowse = require('./C1214587974-SCIOPS.json');

const granuleWithBrowse = require('./C1711961296-LPCLOUD_granules.json');
const granuleWithBrowseMultipleImages = require('./G1200460416-ESA.json');
const granuleWithoutBrowse = require('./C179003030-ORNL_DAAC_granules.json');

const invalidCollectionIdResponse = require('./C000000000-MISSING_NO.json');
const invalidGranuleIdResponse = require('./G000000000-MISSING_NO.json');

describe('Metadata wrangling', () => {
    test('Get image url from collection with browse url', async () => {
        const imageUrl = await getBrowseImageFromConcept(collectionWithBrowse.feed.entry[0]);
        expect(imageUrl).toBe(
            'https://daac.ornl.gov/graphics/browse/project/square/fife_logo_square.png'
        );
    });

    test('Get image url from collection without browse url', async () => {
        const imageUrl = await getBrowseImageFromConcept(collectionWithoutBrowse.feed.entry[0]);
        expect(imageUrl).toBeUndefined();
    });

    test('Get image url from granule without browse url', async () => {
        const imageUrl = await getBrowseImageFromConcept(granuleWithoutBrowse.feed.entry[0]);
        expect(imageUrl).toBeUndefined();
    });

    test('Get image url from granule with browse url', async () => {
        const imageUrl = await getBrowseImageFromConcept(granuleWithBrowse.feed.entry[0]);
        expect(imageUrl).toBe('https://data.lpdaac.earthdatacloud.nasa.gov/lp-prod-public/ASTGTM.003/ASTGTMV003_N03E008.1.jpg');
    });

    test('Get image url from granule with browse url specified image', async () => {
    const imageSrc =
      'https://eoimages.gsfc.nasa.gov/images/imagerecords/151000/151261/atlanticbloom_amo_2023110_lrg.jpg';
    const imageUrl = await getBrowseImageFromConcept(
      granuleWithBrowseMultipleImages.feed.entry[0],
      imageSrc
    );
    expect(imageUrl).toBe(
      'https://eoimages.gsfc.nasa.gov/images/imagerecords/151000/151261/atlanticbloom_amo_2023110_lrg.jpg'
    );
  });
  test('Get image url from granule with browse url specified image default image', async () => {
    const imageUrl = await getBrowseImageFromConcept(
      granuleWithBrowseMultipleImages.feed.entry[0],
      ''
    );
    expect(imageUrl).toBe(
      'https://airsl2.gesdisc.eosdis.nasa.gov/data/Aqua_AIRS_Level2/AIRH2CCF.006/2002/243/AIRS.2002.08.31.028.L2.CC_H.v6.0.12.0.G14101130602.hdf.jpg');
  });
});

describe ('getCollectionLevelBrowseImage', () => {
    describe ('handles 200', () => {
        const scope = nock (config.CMR_ROOT_URL)
              .get (/search\/collections\.json/)
              .reply (200, collectionWithBrowse);
        test ('returns imagery url', async () => {
            const response = await getCollectionLevelBrowseImage ('C179003030-ORNL_DAAC');
            expect (response).toBe ('https://daac.ornl.gov/graphics/browse/project/square/fife_logo_square.png');
        });
    });

    describe ('handles invalid collection id', () => {
        const scope = nock (config.CMR_ROOT_URL)
              .get (/search\/collections\.json/)
              .reply (404, invalidCollectionIdResponse)
              .get (/search\/granules\.json/)
              .reply (404, invalidGranuleIdResponse);

        test ('returns undefined', async () => {
            const response = await getCollectionLevelBrowseImage ('C000000000-MISSING_NO');
            expect (response).toBeUndefined ();
        });
    })
});

describe ('getGranuleLevelBrowseImage', () => {
    describe ('handles 200', () => {
        const scope = nock (config.CMR_ROOT_URL)
              .get (/search\/granules\.json/)
              .reply (200, granuleWithBrowse);

        test ('returns imagery url', async () => {
            const res = await getGranuleLevelBrowseImage ('G1716133754-LPCLOUD', '');
            expect (res).toBe ('https://data.lpdaac.earthdatacloud.nasa.gov/lp-prod-public/ASTGTM.003/ASTGTMV003_N03E008.1.jpg');
        });
    });

    describe ('not found', () => {
        const scope = nock (config.CMR_ROOT_URL)
              .get (/search\/granules\.json/)
              .reply (404, granuleWithoutBrowse);

        test ('handles 404', async () => {
            const res = await getGranuleLevelBrowseImage ('foo', '');
            expect (res).toBeUndefined ()
        });
    });
});

describe ('getEchoToken', () => {
    test.skip ('fetches token from AWS param store', async () => {
        AWS.mock ('SSM', 'getParameter', async (request) => {
            return {Parameter: { '/test/browse-scaler/CMR_ECHO_SYSTEM_TOKEN': 'mock-token' }};
        });
        const res = await getEchoToken ();
        expect (res).toBe ('mock-token');

        AWS.restore ('SSM');
    });
});
