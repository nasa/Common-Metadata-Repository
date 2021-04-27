const { fetchPageFromCMR } = require('./fetchPageFromCmr');
const { clearScrollSession } = require('./clearScrollSession');

/**
 * Harvest UMM JSON collection metadata from CMR environment set in env variable
 * @returns {JSON} partitioned array of CMR collection search results
 */
exports.harvestCmrCollections = async () => {
    const response = await fetchPageFromCMR();
    const results = (await response.json()).items
    const scrollId = response.headers.get("CMR-Scroll-Id");
    let partitionedSearchResults = [];
    let continueScroll = true;
    
    partitionedSearchResults.push(results);
    while (continueScroll) {
        const scrolledResults = await fetchPageFromCMR(scrollId).then(results => results.json())
        partitionedSearchResults.push(scrolledResults.items);

        console.log(`Got [${scrolledResults.items.length}] items from the CMR`)
        if (scrolledResults.items.length < process.env.PAGE_SIZE) {
            continueScroll = false;
        }
    }

    console.log(`Got scroll-id: [${scrollId}]. Clearing session...`);
    await clearScrollSession(scrollId);

    console.log(`Partitions: ${partitionedSearchResults.length}`);
    return partitionedSearchResults;
}