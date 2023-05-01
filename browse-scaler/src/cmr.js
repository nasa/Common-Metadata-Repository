const axios = require('axios');
const fetch = require('node-fetch');
const { getSecureParam } = require('./util');
const config = require('./config');
const { setValue, getValue } = require('./in-memory-cache');

/**
 * getAuthorizationToken: Fetch token for CMR requests
 * @returns {String} ECHO Token for CMR requests
 * @throws {Error} If no token is found. CMR will not return anything
 * if no token is supplied.
 */
const getAuthorizationToken = async () => {
  console.log(`Fetching Echo-Token [${config.CMR_ENVIRONMENT}] from store`);
  let authorizationToken = getValue('CMR_ECHO_SYSTEM_TOKEN');

  if (!authorizationToken) {
    authorizationToken = await getSecureParam(
      `/${config.CMR_ENVIRONMENT}/browse-scaler/CMR_ECHO_SYSTEM_TOKEN`
    );

    if (!authorizationToken) {
      throw new Error('ECHO Token not found. Please update config!');
    }

    setValue('CMR_ECHO_SYSTEM_TOKEN', authorizationToken);
  }

  return authorizationToken;
};

exports.getAuthorizationToken = getAuthorizationToken;

/**
 * fetchConceptFromCMR: Given a concept id, fetch the metadata supplied by
 * the elasticsearch JSON response
 * @param {String} conceptId A collection or granule concept-id
 * @param {String} cmrEndpoint The collection or granule search URL
 * @returns {JSON} the collection associated with the supplied id
 */
const fetchConceptFromCMR = async (conceptId, cmrEndpoint) => {
  const token = config.CMR_ECHO_TOKEN || (await getAuthorizationToken());
  const response = await fetch(cmrEndpoint + conceptId, {
    method: 'GET',
    headers: {
      Authorization: token
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
 * Parse and return the array of data from the nested response body
 * @param {Object} jsonResponse HTTP response from the CMR endpoint
 */
const parseJsonBody = jsonResponse => {
  const { data } = jsonResponse;
  console.log('🚀 ~ file: cmr.js:72 ~ parseJsonBody ~ data:', data);

  const { feed } = data;
  console.log('🚀 ~ file: cmr.js:75 ~ parseJsonBody ~ feed:', feed);

  const { entry } = feed;
  console.log('🚀 ~ file: cmr.js:78 ~ parseJsonBody ~ entry:', entry);

  const [granule] = entry;
  console.log('🚀 ~ file: cmr.js:81 ~ parseJsonBody ~ granule:', granule);

  return granule;
};

/**
 * Fetch a single collection from CMR search
 * @param {String} conceptId Collection concept id from CMR
 * @param {String} token An optional Echo Token
 * @returns [{JSON}] An array of UMM JSON collection results
 */

const fetchCmrGranule = async conceptId => {
  const requestHeaders = {};
  const token = config.CMR_ECHO_TOKEN || (await getAuthorizationToken());
  if (token) {
    requestHeaders.Authorization = token;
  }

  let response;
  try {
    console.log(`🍕 ${config.CMR_ROOT_URL}/search/granules.json?concept_id=${conceptId}`);
    response = await axios({
      url: `${config.CMR_ROOT_URL}/search/granules.json?concept_id=${conceptId}`,
      method: 'GET',
      headers: requestHeaders,
      json: true,
      timeout: config.TIMEOUT_INTERVAL
    });
  } catch (error) {
    console.log(`Could not fetch granule ${conceptId} due to error: ${error}`);
    return null;
  }
  return parseJsonBody(response);
  // console.log('🚀 ~ file: cmr.js:97 ~ fetchCmrGranule ~ response:', response);
  // const { feed = {} } = response;
  // console.log('🚀 ~ file: cmr.js:92 ~ fetchCmrGranule ~ feed:', feed);
  // const { entry = {} } = feed;
  // console.log('🚀 ~ file: cmr.js:93 ~ fetchCmrGranule ~ entry:', entry);
  // return entry;
};

/**
 * getBrowseImageFromConcept: Given a CMR concept, marshall the JSON and
 * filter any associated links to find browse images associated with the concept
 * @param {JSON} concept the JSON metadata associated with a CMR concept
 * @returns {String} the image url if one is found, or null if none is found
 */
exports.getBrowseImageFromConcept = async (concept, imageSrc) => {
  if (!concept) {
    console.error('No concept provided to getBrowseImageFromConcept');
    return;
  }
  try {
    console.log('🚀 ~ file: cmr.js:127 ~ exports.getBrowseImageFromConcept= ~ imageSrc:', imageSrc)
    const { links } = concept;
    const imgRegex = /\b(browse#)$/;
    const newImageRegex = /\bhttps?:\/\/\S+\.(?:png|jpe?g|gif|bmp)\b/;
    // todo
    const imageUrls = links.filter(link => imgRegex.test(link.rel));
    console.debug(`🍥 all links from metadata ${JSON.stringify(imageUrls)}`);

    // const imgurl = links.filter(link => imgRegex.test(link.rel))[0];

    // console.debug(`🐸links from metadata ${JSON.stringify(links)}`);
    // console.debug(`❤️ image link from metadata ${JSON.stringify(imgurl)}`);
    // console.log('≈ ~ file: cmr.js:83 ~ imgurl.href:', imgurl.href);

    // if (imageUrls.includes(imageSrc)) {
    //   const specifiedImage = imageUrls[imageUrls.indexOf(imageSrc)];
    //   console.log(`🏋️ ${specifiedImage}`);
    //   // eslint-disable-next-line consistent-return
    //   return specifiedImage;
    // }

    const searchImage = imageUrls.find(image => image.href === imageSrc);

    if (searchImage) {
      console.log(
        ' we found 🚀 ~ file: cmr.js:157 ~ exports.getBrowseImageFromConcept= ~ searchImage:',
        searchImage.href
      );
      // eslint-disable-next-line consistent-return
      return searchImage.href;
    }

    // // eslint-disable-next-line consistent-return
    // imageUrls.forEach(image => {
    //   if (image.href === imageSrc) {
    //     return image.href;
    //   }
    // });

    // if (imgurl && imgurl.href) {
    //   // eslint-disable-next-line consistent-return
    //   return imgurl.href;
    // }
    if (imageUrls) {
      // if no image was specified return 0th index
      // eslint-disable-next-line consistent-return
      return imageUrls[0].href;
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
exports.getGranuleLevelBrowseImage = async (conceptId, imageSrc) => {
  // const granuleConcept = await fetchConceptFromCMR(conceptId, config.CMR_GRANULE_URL);
  const granuleConcept = await fetchCmrGranule(conceptId);
  const granuleImagery = await this.getBrowseImageFromConcept(granuleConcept, imageSrc);

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
  console.log('🚀 ~ file: cmr.js:112 ~ collectionConcept:', collectionConcept);
  const collectionImagery = await this.getBrowseImageFromConcept(collectionConcept);
  if (collectionImagery) {
    return collectionImagery;
  }

  const firstGranuleFromCollection = await fetchConceptFromCMR(
    collectionId,
    config.CMR_GRANULE_URL
  );
  const granuleImagery = await this.getBrowseImageFromConcept(firstGranuleFromCollection);

  return granuleImagery;
};
