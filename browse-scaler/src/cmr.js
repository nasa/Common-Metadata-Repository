const fetch = require('node-fetch');

/* CMR ENVIRONMENT VARIABLES */
const cmrRootUrl = `http://${process.env.CMR_ROOT}`;
const cmrCollectionUrl = `${cmrRootUrl}/search/collections.json?concept_id=`;
const cmrGranuleUrl = `${cmrRootUrl}/search/granules.json?concept_id=`;

const fetchConceptFromCMR = async (conceptId, cmrEndpoint) => {
  try {
    return fetch(cmrEndpoint + conceptId)
      .then(response => response.json())
      .then(json => json.feed.entry[0]);
  } catch (err) {
    console.log(`Could not find concept ${conceptId}: ${err}`);
    return null;
  }
};

exports.getBrowseImageFromConcept = async concept => {
  try {
    const { links } = concept;
    const imgRegex = /\b(.png|.jpg|.gif|.jpeg)$/;
    const imgurl = links.filter(link => imgRegex.test(link.href))[0];

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

exports.getGranuleLevelBrowseImage = async granuleId => {
  const granuleConcept = await fetchConceptFromCMR(granuleId, cmrGranuleUrl);
  const granuleImagery = await this.getBrowseImageFromConcept(granuleConcept);

  return granuleImagery;
};

exports.getCollectionLevelBrowseImage = async collectionId => {
  // When no browse imagery exists for a collection, pull the imagery from
  // the first available granule. If that does not exist, return null, which
  // would indicate that we should return the 'image-not-found' response

  const collectionConcept = await fetchConceptFromCMR(collectionId, cmrCollectionUrl);
  const collectionImagery = await this.getBrowseImageFromConcept(collectionConcept);
  if (collectionImagery) {
    return collectionImagery;
  }

  const firstGranuleFromCollection = await fetchConceptFromCMR(collectionId, cmrGranuleUrl);
  const granuleImagery = await this.getBrowseImageFromConcept(firstGranuleFromCollection);

  return granuleImagery;
};
