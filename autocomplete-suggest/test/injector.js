const { Client } = require('@elastic/elasticsearch');

const esClient = new Client({
  node: 'http://localhost:9210',
});

const AC_INDEX_NAME = 'autocomplete';

const collectionDataType = require('./resources/collection_data_type_list');
const granuleDataFormat = require('./resources/granule_data_format_list');
const instrument = require('./resources/instrument_list');
const platform = require('./resources/platform_list');
const project = require('./resources/project_list');
const provider = require('./resources/provider_list');
const spatialKeyword = require('./resources/spatial_keyword_list');

const AC_DATA = {
  collectionDataType,
  granuleDataFormat,
  instrument,
  platform,
  project,
  provider,
  spatialKeyword,
};

const AC_INDEX_MAPPING = require('../resources/autocomplete.index.1.6.2');

async function deleteIndex(index) {
  try {
    await esClient
      .indices
      .delete({ index });
  } catch (err) {

  }
}

async function createIndex(mapping, index) {
  try {
    await esClient
      .indices
      .create(
        {
          index,
          body: mapping,
        },
      );
  } catch (err) {

  }
}

async function inject(indexes) {
  const body = Object.keys(indexes)
    .flatMap((type) => indexes[type].map((value) => ({ type, value })))
    .flatMap((suggestion) => [{ index: { _index: AC_INDEX_NAME, _type: 'suggestion' } }, suggestion]);

  try {
    await esClient.bulk({
      refresh: true,
      body,
    });
  } catch (err) {

  }
}


(async () => {
  /* eslint-disable no-console */
  console.time('refresh autocomplete');
  console.time('deleted in :');
  await deleteIndex(AC_INDEX_NAME);
  console.timeEnd('deleted in :');

  console.time('created in :');
  await createIndex(AC_INDEX_MAPPING, AC_INDEX_NAME);
  console.timeEnd('created in :');

  console.time('populated in :');
  await inject(AC_DATA);
  console.timeEnd('populated in :');
  console.log('===============================');
  console.timeEnd('refresh autocomplete');
  /* eslint-enable no-console */
})();
