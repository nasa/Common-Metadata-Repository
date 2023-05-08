// const axios = require('axios');

import axios from 'axios';
import fetch from 'node-fetch';
import { setValue, getValue } from './in-memory-cache.js';
import { getSecureParam } from './util.js';
import {
  CMR_ENVIRONMENT,
  CMR_ECHO_TOKEN,
  CMR_GRANULE_URL,
  CMR_COLLECTION_URL,
  CMR_ROOT_URL,
  TIMEOUT_INTERVAL
} from './config.js';

// const fetch = require('node-fetch');
// const { setValue, getValue } = require('./in-memory-cache');
// const { getSecureParam } = require('./util');
// const config = require('./config');
/**
 * getAuthorizationToken: Fetch token for CMR requests
 * @returns {String} ECHO Token for CMR requests
 * @throws {Error} If no token is found. CMR will not return anything
 * if no token is supplied.
 */
export const getAuthorizationToken = async () => {
  console.log(`Fetching Echo-Token [${CMR_ENVIRONMENT}] from store`);
  let authorizationToken = getValue('CMR_ECHO_SYSTEM_TOKEN');

  if (!authorizationToken) {
    authorizationToken = await getSecureParam(
      `/${CMR_ENVIRONMENT}/browse-scaler/CMR_ECHO_SYSTEM_TOKEN`
    );

    if (!authorizationToken) {
      throw new Error('ECHO Token not found. Please update config!');
    }

    setValue('CMR_ECHO_SYSTEM_TOKEN', authorizationToken);
  }

  return authorizationToken;
};

// exports.getAuthorizationToken = getAuthorizationToken;

/**
 * fetchConceptFromCMR: Given a concept id, fetch the metadata supplied by
 * the elasticsearch JSON response
 * @param {String} conceptId A collection or granule concept-id
 * @param {String} cmrEndpoint The collection or granule search URL
 * @returns {JSON} the collection associated with the supplied id
 */
const fetchConceptFromCMR = async (conceptId, cmrEndpoint) => {
  const token = CMR_ECHO_TOKEN || (await getAuthorizationToken());
  console.log('🚀 ~ file: cmr.js:55 ~ fetchConceptFromCMR ~ cmrEndpoint:', cmrEndpoint);
  console.log('🚀 ~ file: cmr.js:57 ~ fetchConceptFromCMR ~ conceptId:', conceptId);
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
  const { feed } = data;
  const { entry } = feed;
  const [granule] = entry;
  return granule;
};

/**
 * Fetch a single Granule record from CMR search
 * @param {String} conceptId Granule concept id from CMR
 * @returns [{JSON}] An array of json granule results
 */

const fetchCmrGranule = async conceptId => {
  const requestHeaders = {};
  const token = CMR_ECHO_TOKEN || (await getAuthorizationToken());
  if (token) {
    requestHeaders.Authorization = token;
  }

  let response;
  try {
    response = await axios({
      url: `${CMR_ROOT_URL}/search/granules.json?concept_id=${conceptId}`,
      method: 'GET',
      headers: requestHeaders,
      json: true,
      timeout: TIMEOUT_INTERVAL
    });
  } catch (error) {
    console.log(`Could not fetch granule ${conceptId} due to error: ${error}`);
    return null;
  }
  return parseJsonBody(response);
};

const fetchCmrCollection = async conceptId => {
  const requestHeaders = {};
  const token = CMR_ECHO_TOKEN || (await getAuthorizationToken());
  if (token) {
    requestHeaders.Authorization = token;
  }

  let response;
  try {
    response = await axios({
      url: `${CMR_ROOT_URL}/search/collections.json?concept_id=${conceptId}`,
      method: 'GET',
      headers: requestHeaders,
      json: true,
      timeout: TIMEOUT_INTERVAL
    });
  } catch (error) {
    console.log(`Could not fetch collection ${conceptId} due to error: ${error}`);
    return null;
  }
  return parseJsonBody(response);
};

/**
 * getBrowseImageFromConcept: Given a CMR concept, marshall the JSON and
 * filter any associated links to find browse images associated with the concept
 * @param {JSON} concept the JSON metadata associated with a CMR concept
 * @param {string} imageSrc optional argument that specifies which image should be chosen defaults to empty str
 * @returns {String} the image url if one is found, or null if none is found
 */
export const getBrowseImageFromConcept = async (concept, imageSrc) => {
  if (!concept) {
    console.error('No concept provided to getBrowseImageFromConcept');
    return;
  }
  try {
    const { links } = concept;
    const imgRegex = /\b(browse#)$/;
    const imageUrls = links.filter(link => imgRegex.test(link.rel));
    console.debug(`🚀 links from metadata ${JSON.stringify(links)}`);
    console.debug(`🚀 image link from metadata ${JSON.stringify(imageUrls)}`);
    const searchImage = imageUrls.find(image => image.href === imageSrc);

    if (searchImage) {
      console.log('The searched image was in the metadata', searchImage.href);
      // eslint-disable-next-line consistent-return
      return searchImage.href;
    }

    if (imageUrls) {
      // if the searched image was not found return 0th index image
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
 * @param {string} imageSrc optional argument that specifies which image should be chosen defaults to empty str
 * @returns {String} the first of any image links found. If a collection id is supplied, this will
 * return the first of any links found in the first granule associated with said collection
 */
export const getGranuleLevelBrowseImage = async (conceptId, imageSrc) => {
  const granuleConcept = await fetchCmrGranule(conceptId);
  console.log(
    '🚀 ~ file: cmr.js:187 ~ getGranuleLevelBrowseImage ~ granuleConcept:',
    granuleConcept
  );
  const granuleImagery = await getBrowseImageFromConcept(granuleConcept, imageSrc);
  console.log(
    '🚀 ~ file: cmr.js:189 ~ getGranuleLevelBrowseImage ~ granuleImagery:',
    granuleImagery
  );

  return granuleImagery;
};

/**
 * getCollectionLevelBrowseImage: When no browse imagery exists for a collection,
 * pull the imagery from the first available granule. If that does not exist, return
 * null, which would indicate that we should return the 'image-not-found' response
 * @param {String} collectionId CMR concept id
 * @returns {String} browse image url, if any are found. Return null if not
 */
export const getCollectionLevelBrowseImage = async collectionId => {
  const collectionConcept = await fetchConceptFromCMR(collectionId, CMR_COLLECTION_URL);
  const collectionImagery = await getBrowseImageFromConcept(collectionConcept);
  if (collectionImagery) {
    return collectionImagery;
  }

  const firstGranuleFromCollection = await fetchConceptFromCMR(collectionId, CMR_GRANULE_URL);
  // const firstGranuleFromCollection = await fetchCmrGranule(collectionId);
  const granuleImagery = await getBrowseImageFromConcept(firstGranuleFromCollection);

  return granuleImagery;
};
