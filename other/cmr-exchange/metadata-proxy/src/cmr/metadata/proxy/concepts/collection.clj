(ns cmr.metadata.proxy.concepts.collection
  (:require
   [clojure.string :as string]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Collection API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def opendap-regex-tag
  "The tag used for associating collections to a regex to be used to construct the OPeNDAP URL."
  "cmr.earthdata.nasa.ous.datafile.replace")

(defn build-query
  "Returns the query string for a CMR collection query."
  [concept-id]
  (str "concept_id=" concept-id
       "&include_tags=" opendap-regex-tag))

(defn async-get-metadata
  "Given a data structure with :collection-id, get the metadata for the
  associated collection."
  [search-endpoint user-token params]
  (let [concept-id (:collection-id params)
        url (str search-endpoint
                 "/collections?"
                 (build-query concept-id))]
    (log/debug "Collection query to CMR:" url)
    (request/async-get
     url
     (-> {}
         (request/add-token-header user-token)
         (request/add-accept "application/json"))
     {}
     response/json-handler)))

(defn extract-metadata
  [promise]
  (let [results @promise]
    (log/trace "Got results from CMR granule collection:" results)
    (first (get-in results [:feed :entry]))))

(defn get-metadata
  [search-endpoint user-token params]
  (let [promise (async-get-metadata search-endpoint user-token params)]
    (extract-metadata promise)))

(defn extract-variable-ids
  [entry]
  (sort (get-in entry [:associations :variables])))

(defn extract-service-ids
  [entry]
  (sort (get-in entry [:associations :services])))
