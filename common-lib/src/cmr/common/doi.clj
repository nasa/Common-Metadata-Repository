(ns cmr.common.doi
  "Functions for handling DOIs within the CMR.")

(def doi-base-url
  "The base DOI URL."
  "https://doi.org")

(defn doi->url
  "Converts a DOI into a URL if it is not already a URL."
  [doi]
  (if (re-matches #"http.*" doi)
    doi
    (format "%s/%s" doi-base-url doi)))

(defn get-cmr-landing-page
  "Given a CMR host and a concept ID, return the collection landing page for
  the given id."
  [cmr-base-url concept-id]
  (format "%sconcepts/%s.html" cmr-base-url concept-id))

(defn get-landing-page
  "Returns the landing page for a collection. If the collection has a DOI use that, otherwise
  use the CMR HTML page for the collection."
  [cmr-base-url item]
  (if-let [doi (:doi item)]
    (doi->url doi)
    (get-cmr-landing-page cmr-base-url (:concept-id item))))
