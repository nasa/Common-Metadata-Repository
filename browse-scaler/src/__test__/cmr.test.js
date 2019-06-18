const { getBrowseImageFromConcept } = require('../cmr');

const collectionWithBrowse = require('./C179003030-ORNL_DAAC.json');
const collectionWithoutBrowse = require('./C1214587974-SCIOPS.json');
const granuleWithoutBrowse = require('./C179003030-ORNL_DAAC_granules.json');

describe('Metadata wrangling', () => {
  test('Get image url from collection with browse url', async () => {
    const imageUrl = await getBrowseImageFromConcept(collectionWithBrowse.feed.entry[0]);
    expect(imageUrl).toBe(
      'https://daac.ornl.gov/graphics/browse/project/square/fife_logo_square.png'
    );
  });

  test('Get image url from collection without browse url', async () => {
    const imageUrl = await getBrowseImageFromConcept(collectionWithoutBrowse.feed.entry[0]);
    expect(imageUrl).toBe(null);
  });

  test('Get image url from granule without browse url', async () => {
    const imageUrl = await getBrowseImageFromConcept(granuleWithoutBrowse.feed.entry[0]);
    expect(imageUrl).toBe(null);
  });
});
