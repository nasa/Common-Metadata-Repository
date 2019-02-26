(ns cmr.indexer.data.concepts.collection.collection-util
  "Contains util functions for collection indexing")

(defn parse-version-id
  "Safely parse the version-id to an integer and then return as a string. This
  is so that collections with the same short name and differently formatted
  versions (i.e. 1 vs. 001) can be more accurately sorted. If the version
  cannot be parsed to an integer, return the original version-id"
  [version-id]
  (try
    (str (Integer/parseInt version-id))
    (catch Exception _ version-id)))

(defn opendap-url?
  "Determins if the related-url is a OPeNDAP service url."
  [related-url]
  (if (and (= (:URLContentType related-url)  "DistributionURL")
           (= (:Type related-url) "USE SERVICE API")
           (= (:Subtype related-url) "OPENDAP DATA"))
    true
    false))
