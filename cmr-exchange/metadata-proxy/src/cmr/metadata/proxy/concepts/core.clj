(ns cmr.metadata.proxy.concepts.core
  (:require
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [taoensso.timbre :as log]))

(defn async-get-metadata
  "Given a data structure with :concept-id, get the nativemetadata for the
  associated concept."
  [search-endpoint user-token params]
  (let [concept-id (:concept-id params)
        url (format "%s/concepts/%s" search-endpoint concept-id)]
    (log/debug "Concept query to CMR:" url)
    (request/async-get
     url (request/add-token-header {} user-token)
     {}
     response/identity-handler)))

(defn extract-metadata
  [promise]
  (let [results @promise]
    (log/trace "Got results from CMR granule collection:" results)
    results))

(defn get-metadata
  [search-endpoint user-token params]
  (let [promise (async-get-metadata search-endpoint user-token params)]
    (extract-metadata promise)))
