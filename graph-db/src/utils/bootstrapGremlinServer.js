const { harvestCmrCollections } = require('./cmr/harvestCmrCollections');
const { initializeGremlinConnection } = require('./gremlin/initializeGremlinConnection');
const { indexCmrCollection } = require('./indexing/indexCmrCollection');

exports.bootstrapGremilinServer = async () => {
    const partitionedSearchResults = await harvestCmrCollections();
    const gremlin = initializeGremlinConnection();
    let indexingStatuses = [];

    partitionedSearchResults.forEach(partition => {
        console.log(`Indexing [${partition.length}] items into graph db`);
        const indexingStatus = partition.map(result => indexCmrCollection(result, gremlin));
        indexingStatuses.push(indexingStatus);
    });

    return indexingStatuses;
}