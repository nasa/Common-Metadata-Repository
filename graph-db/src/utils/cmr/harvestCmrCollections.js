const { fetchPageFromCMR } = require('./fetchPageFromCmr');
const { clearScrollSession } = require('./clearScrollSession');

exports.harvestCmrCollections = async () => {
    let { scrollId, response } = await fetchPageFromCMR();
    let partitionedSearchResults = [];
    let continueScroll = true;

    partitionedSearchResults.push(response);
    while (continueScroll){
        let scrolledResults = (await fetchPageFromCMR(scrollId)).response;
        partitionedSearchResults.push(scrolledResults);

        console.log(`Got [${scrolledResults.length}] items from the CMR`)
        if (scrolledResults.length < process.env.PAGE_SIZE) {
            continueScroll = false;
        }
    }

    console.log(`Got scroll-id: [${scrollId}]. Clearing session...`);
    clearScrollSession(scrollId);

    console.log(`Partitions: ${partitionedSearchResults.length}`);
    return partitionedSearchResults;
}