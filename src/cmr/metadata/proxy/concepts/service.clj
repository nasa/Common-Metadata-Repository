(ns cmr.metadata.proxy.concepts.service
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.results.core :as results]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.metadata.proxy.results.errors :as metadata-errors]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

(defn build-query
  [service-ids]
  (string/join
   "&"
   (conj
    (map #(str (codec/url-encode "concept_id[]")
               "=" %)
         service-ids)
    (str "page_size=" (count service-ids)))))

(defn async-get-metadata
  "Given a service-id, get the metadata for the associate service."
  [search-endpoint user-token service-ids]
  (if (seq service-ids)
    (let [url (str search-endpoint "/services")
          payload (build-query service-ids)]
      (log/debug "Getting service metadata for:" service-ids)
      (log/debug "Services query CMR URL:" url)
      (log/debug "Services query CMR payload:" payload)
      (request/async-post
       url
       (-> {}
           (request/add-token-header user-token)
           (request/add-accept "application/vnd.nasa.cmr.umm+json")
           (request/add-form-ct)
           (request/add-payload payload))
       {}
       response/json-handler))
    (deliver (promise) [])))

(defn extract-metadata
  [promise]
  (let [rslts @promise]
    (if (errors/erred? rslts)
      (do
        (log/error metadata-errors/service-metadata)
        rslts)
      (do
        (log/trace "Got results from CMR service search:"
                   (results/elided rslts))
        (log/trace "Remaining results:" (results/remaining-items rslts))
        (:items rslts)))))

(defn get-metadata
  [search-endpoint user-token service-ids]
  (let [promise (async-get-metadata search-endpoint user-token service-ids)]
    (extract-metadata promise)))

(defn match-opendap
  [service-data]
  (= "opendap" (string/lower-case (:Type service-data))))
