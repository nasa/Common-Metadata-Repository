const { Client } = require('@elastic/elasticsearch');

const es1Client = new Client({
  node: 'http://localhost:9201',
});

const es7Client = new Client({
  node: 'http://localhost:9200',
});

const collectionDataType = require('./resources/collection_data_type_list');
const granuleDataFormat = require('./resources/granule_data_format_list');
const instrument = require('./resources/instrument_list');
const platform = require('./resources/platform_list');
const project = require('./resources/project_list');
const provider = require('./resources/provider_list');
const spatialKeyword = require('./resources/spatial_keyword_list');

const data = {
  collectionDataType,
  granuleDataFormat,
  instrument,
  platform,
  project,
  provider,
  spatialKeyword,
};

const es1indexMapping = require('../resources/autocomplete.index.1.6.2');
const es7indexMapping = require('../resources/autocomplete.index');

async function deleteIndexes() {
  try {
    await es1Client.indices.delete({ index: 'autocomplete' });
  } catch (err) {
    console.error(err);
  }

  try {
    await es7Client.indices.delete({ index: 'autocomplete' });
  } catch (err) {
    console.error(err);
  }
}

async function createIndexes() {
  try {
    await es1Client.indices.create(
      {
        index: 'autocomplete',
        body: es1indexMapping,
      },
    );
  } catch (err) {
    console.error(err);
  }

  try {
    await es7Client.indices.create(
      {
        index: 'autocomplete',
        body: es7indexMapping,
      },
    );
  } catch (err) {
    console.error(err);
  }
}

async function inject(indexes) {
  const es1body = Object.keys(indexes)
    .flatMap((type) => indexes[type].map((value) => ({ type, value })))
    .flatMap((suggestion) => [{ index: { _index: 'autocomplete', _type: 'suggestion' } }, suggestion]);

  const es7body = Object.keys(indexes)
    .flatMap((type) => indexes[type].map((value) => ({ type, value })))
    .flatMap((suggestion) => [{ index: { _index: 'autocomplete' } }, suggestion]);

  try {
    await es1Client.bulk({ refresh: true, body: es1body });
  } catch (err) {
    console.error(err);
  }

  try {
    await es7Client.bulk({ refresh: true, body: es7body });
  } catch (err) {
    console.error(err);
  }
}


(async () => {
  console.time('deleted in :');
  await deleteIndexes();
  console.timeEnd('deleted in :');

  console.time('created in :');
  await createIndexes();
  console.timeEnd('created in :');

  console.time('populated in :');
  await inject(data);
  console.timeEnd('populated in :');
})();
