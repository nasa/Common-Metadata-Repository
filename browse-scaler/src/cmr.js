const fetch = require('node-fetch');
const { getSecureParam } = require('./util');
const { getTokenInCache, setTokenInCache } = require('./cache')
const config = require ('./config');

/**
 * getEchoToken: Fetch token for CMR requests
 * @returns {String} ECHO Token for CMR requests
 * @throws {Error} If no token is found. CMR will not return anything
 * if no token is supplied.
 */
const getEchoToken = async () => {
  console.log('Fetching Echo-Token [' + config.CMR_ENVIRONMENT + '] from store');
  let token = await getTokenInCache();
  if (!token) {
    token = await getSecureParam(
      `/${config.CMR_ENVIRONMENT}/browse-scaler/CMR_ECHO_SYSTEM_TOKEN`
    );

    if (!token) {
      throw new Error('ECHO Token not found. Please update config!');
    }

    setTokenInCache(token);
  }
  
  return token;
};

exports.getEchoToken = getEchoToken;

/**
 * fetchConceptFromCMR: Given a concept id, fetch the metadata supplied by
 * the elasticsearch JSON response
 * @param {String} conceptId A collection or granule concept-id
 * @param {String} cmrEndpoint The collection or granule search URL
 * @returns {JSON} the collection associated with the supplied id
 */
const fetchConceptFromCMR = async (conceptId, cmrEndpoint) => {
  const token = config.CMR_ECHO_TOKEN || await getEchoToken();
  const response = await fetch(cmrEndpoint + conceptId, {
    method: 'GET',
    headers: {
      'Echo-Token': token
    }
  })
      .then(res => res.json())
      .then(json => {
        if (json.errors) {
          throw new Error(`The following errors occurred: ${json.errors}`);
        } else {
          return json.feed.entry[0];
        }
      })
      .catch(error => {
        console.log(`Could not find concept ${conceptId}: ${error}`);
      });
  return response;
};

/**
 * getBrowseImageFromConcept: Given a CMR concept, marshall the JSON and
 * filter any associated links to find browse images associated with the concept
 * @param {JSON} concept the JSON metadata associated with a CMR concept
 * @returns {String} the image url if one is found, or null if none is found
 */
exports.getBrowseImageFromConcept = async concept => {
  if (!concept) {
    console.error ('No concept provided to getBrowseImageFromConcept');
    return;
  }
  try {
    const { links } = concept;
    const imgRegex = /\b(browse#)$/;
    const imgurl = links.filter(link => imgRegex.test(link.rel))[0];

    console.debug(`links from metadata ${JSON.stringify(links)}`);
    console.debug(`image link from metadata ${JSON.stringify(imgurl)}`);
    if (imgurl && imgurl.href) {
      return imgurl.href;
    }
  } catch (err) {
    console.error(`Could not get image from concept: ${err}`);
  }
};

/**
 * getGranuleLevelBrowseImage: Given a or collection id, get the first associated granule
 * @param {String} conceptId CMR concept-id. This can be a collection _or_ granule id
 * @returns {String} the first of any image links found. If a collection id is supplied, this will
 * return the first of any links found in the first granule associated with said collection
 */
exports.getGranuleLevelBrowseImage = async conceptId => {
  const granuleConcept = await fetchConceptFromCMR(conceptId, config.CMR_GRANULE_URL);
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
  const collectionConcept = await fetchConceptFromCMR(collectionId, config.CMR_COLLECTION_URL);
  console.log (collectionConcept);
  const collectionImagery = await this.getBrowseImageFromConcept(collectionConcept);
  if (collectionImagery) {
    return collectionImagery;
  }

  const firstGranuleFromCollection = await fetchConceptFromCMR(collectionId, config.CMR_GRANULE_URL);
  const granuleImagery = await this.getBrowseImageFromConcept(firstGranuleFromCollection);

  return granuleImagery;
};
