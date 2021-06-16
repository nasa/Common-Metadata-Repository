const conceptTypes = {
  C: 'collection',
  G: 'granule',
  S: 'service',
  V: 'variable'
}

/**
 * Given a concept id, determine and return CMR concept type
 * @param {String} conceptId Collection concept id from CMR
 * @returns {String} concept type
 */
export const getConceptType = (conceptId) => {
  const conceptKey = conceptId[0]

  return conceptTypes[conceptKey]
}
