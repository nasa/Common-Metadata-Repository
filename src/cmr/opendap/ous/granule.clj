(ns cmr.opendap.ous.granule
  (:require
   [clojure.string :as string]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.util :as util]
   [taoensso.timbre :as log]))

(defn build-include
  [gran-ids]
  (string/join "&" (map #(str "concept_id\\[\\]=" %) gran-ids)))

(defn build-exclude
  [gran-ids]
  (string/join "&" (map #(str "exclude\\[echo_granule_id\\]\\[\\]=" %)
                        gran-ids)))

(defn build-query
  "Build the query string for querying granles, bassed upon the options
  passed in the parameters."
  [params]
  (let [coll-id (:collection-id params)
        gran-ids (util/remove-empty (:granules params))
        exclude? (:exclude-granules params)]
    (str "collection_concept_id=" coll-id
         (when (seq gran-ids)
          (str "&"
               (if exclude?
                 (build-exclude gran-ids)
                 (build-include gran-ids)))))))

(defn get-metadata
  "Given a data structure with :collection-id, :granules, and :exclude-granules
  keys, get the metadata for the desired granules.

  Which granule metadata is returned depends upon the values of :granules and
  :exclude-granules"
  [search-endpoint user-token params]
  (let [granule-query (build-query params)
        url (str search-endpoint
                 "/granules?"
                 (build-query params))
        results (request/async-get url
                 (-> {}
                     (request/add-token-header user-token)
                     (request/add-accept "application/json"))
                 response/json-handler)]
    (log/debug "Got results from CMR search:" results)
    (get-in @results [:feed :entry])))

