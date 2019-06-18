const fetch = require('isomorphic-unfetch');

/* CMR ENVIRONMENT VARIABLES */
const environment =
  process.env.CMR_ENVIRONMENT !== 'sit' && process.env.CMR_ENVIRONMENT !== 'uat'
    ? ''
    : `${process.env.CMR_ENVIRONMENT}.`;
const cmrRootUrl = `https://cmr.${environment}earthdata.nasa.gov/search/`;
const cmrCollectionUrl = `${cmrRootUrl}collections.json?concept_id=`;
const cmrGranuleUrl = `${cmrRootUrl}granules.json?collection_concept_id=`;

const fetchConceptFromCMR = async (conceptId, cmrEndpoint) => {
  return fetch(cmrEndpoint + conceptId)
    .then(response => response.json())
    .then(json => json.feed.entry[0]);
};

exports.getBrowseImageFromConcept = async concept => {
  try {
    const { links } = concept;
    const imgRegex = /\b(.png|.jpg|.gif|.jpeg)$/;
    const imgurl = links.filter(link => imgRegex.test(link.href))[0];

    if (imgurl) {
      return imgurl.href;
    }

    return null;
  } catch (err) {
    console.log(`Could not get image from concept: ${err}`);
    return null;
  }
};

exports.getGranuleLevelBrowseImage = async granuleId => {
  const granuleConcept = await fetchConceptFromCMR(granuleId, cmrGranuleUrl);
  const granuleImagery = await this.getBrowseImageFromConcept(granuleConcept);

  return granuleImagery;
};

exports.getCollectionLevelBrowseImage = async collectionId => {
  // When no browse imagery exists for a collection, pull the imagery from
  // the first available granule. If that does not exist, return null, which
  // would indicate that we should return the 'image-not-found' response

  const collectionConcept = await fetchConceptFromCMR(collectionId, cmrCollectionUrl).then;
  const collectionImagery = await this.getBrowseImageFromConcept(collectionConcept);
  if (collectionImagery) {
    return collectionImagery;
  }

  const granuleImagery = await this.getGranuleLevelBrowseImage(collectionId);

  return granuleImagery;
};
