const fetch = require('node-fetch');

/* CMR ENVIRONMENT VARIABLES */
const cmrRootUrl = `http://${process.env.CMR_ROOT}`;
const cmrCollectionUrl = `${cmrRootUrl}/search/collections.json?concept_id=`;
const cmrGranuleUrl = `${cmrRootUrl}/search/granules.json?concept_id=`;

/**
 * fetchConceptFromCMR: Given a concept id, fetch the metadata supplied by
 * the elasticsearch JSON response
 * @param {String} conceptId A collection or granule concept-id
 * @param {String} cmrEndpoint The collection or granule search URL. This is set
 * as a variable so you don't have to think about what it is
 * @returns {JSON} the collection associated with the supplied id
 */
const fetchConceptFromCMR = async (conceptId, cmrEndpoint) => {
  return fetch(cmrEndpoint + conceptId)
    .then(response => response.json())
    .then(json => json.feed.entry[0])
    .catch(error => {
      console.log(`Could not find concept ${conceptId}: ${error}`);
      return null;
    });
};

/**
 * getBrowseImageFromConcept: Given a CMR concept, marshall the JSON and
 * filter any associated links to find browse images associated with the concept
 * @param {JSON} concept the JSON metadata associated with a CMR concept
 * @returns {String} the image url if one is found, or null if none is found
 */
exports.getBrowseImageFromConcept = async concept => {
  try {
    if (concept === null) {
      return null;
    }

    const { links } = concept;
    const imgRegex = /\b(browse#)$/;
    const imgurl = links.filter(link => imgRegex.test(link.rel))[0];

    console.log(`links from metadata ${JSON.stringify(links)}`);
    console.log(`image link from metadata ${JSON.stringify(imgurl)}`);
    if (imgurl) {
      return imgurl.href;
    }

    return null;
  } catch (err) {
    console.log(`Could not get image from concept: ${err}`);
    return null;
  }
};

/**
 * getGranuleLevelBrowseImage: Given a or collection id, get the first associated granule
 * @param {String} granuleId CMR concept-id. This can be a collection _or_ granule id
 * @returns {String} any image links found. If a collection id is supplied, this will
 * return any links found in the first granule associated with said collection
 */
exports.getGranuleLevelBrowseImage = async granuleId => {
  const granuleConcept = await fetchConceptFromCMR(granuleId, cmrGranuleUrl);
  const granuleImagery = await this.getBrowseImageFromConcept(granuleConcept);

  return granuleImagery;
};

/**
 * getCollectionLevelBrowseImage: When no browse imagery exists for a collection,
 * pull the imagery from the first available granule. If that does not exist, return
 * null, which would indicate that we should return the 'image-not-found' response
 * @param {String} collectionId CMR concept id
 * @returns {String} browse image url, if any are found. Return null if not
 */
exports.getCollectionLevelBrowseImage = async collectionId => {
  const collectionConcept = await fetchConceptFromCMR(collectionId, cmrCollectionUrl);
  const collectionImagery = await this.getBrowseImageFromConcept(collectionConcept);
  if (collectionImagery) {
    return collectionImagery;
  }

  const firstGranuleFromCollection = await fetchConceptFromCMR(collectionId, cmrGranuleUrl);
  const granuleImagery = await this.getBrowseImageFromConcept(firstGranuleFromCollection);

  return granuleImagery;
};
