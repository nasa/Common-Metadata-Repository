(ns cmr.opendap.ous.collection.core
  (:require
   [clojure.string :as string]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [taoensso.timbre :as log]))

(defn build-query
  [params]
  (str "concept_id=" (:collection-id params)))

(defn async-get-metadata
  "Given a data structure with :collection-id, get the metadata for the
  associated collection."
  [search-endpoint user-token params]
  (let [url (str search-endpoint
                 "/collections?"
                 (build-query params))]
    (request/async-get
     url
     (-> {}
         (request/add-token-header user-token)
         (request/add-accept "application/json"))
     response/json-handler)))

(defn get-metadata
  [search-endpoint user-token params]
  (let [results @(async-get-metadata search-endpoint user-token params)]
    (log/debug "Got results from CMR collection search:" results)
    (first (get-in results [:feed :entry]))))

(defn extract-variable-ids
  [entry]
  (get-in entry [:associations :variables]))

(defn extract-service-ids
  [entry]
  (get-in entry [:associations :services]))
