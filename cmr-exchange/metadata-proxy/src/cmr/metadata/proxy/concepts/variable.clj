(ns cmr.metadata.proxy.concepts.variable
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.results.core :as results]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.metadata.proxy.results.errors :as metadata-errors]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX We can pull this from configuration once async-get-metadata function
;;     signatures get updated to accept the system data structure as an arg.
(def variables-api-path "/variables")
(def pinned-variable-schema-version "1.7")
(def results-content-type "application/vnd.nasa.cmr.umm_results+json")
(def charset "charset=utf-8")
(def accept-format "%s; version=%s; %s")
(def accept-header (format accept-format
                           results-content-type
                           pinned-variable-schema-version
                           charset))
(def id-search-constraint "concept_id[]")
(def max-page-size 2000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Core Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-query
  "Build variable search query based on variable-info, which will be
  list of concept-ids."
  [variable-info search-constraint]
  (string/join
    "&"
    (conj
      (map #(str (codec/url-encode search-constraint) "=" %) variable-info)
      (str "page_size=" max-page-size))))

(defn async-get-metadata
  "Given variable-info, which will be a list of concept-ids,
  and the search-constraint, returns the metadata for the related variables."
  [search-endpoint user-token variable-info search-constraint]
  (if (seq variable-info)
    (let [url (str search-endpoint variables-api-path)
          payload (build-query variable-info search-constraint)]
      (log/debug "Variables query CMR URL:" url)
      (log/debug "Variables query CMR payload:" payload)
      (request/async-post
       url
       (-> {}
           (request/add-token-header user-token)
           (request/add-accept accept-header)
           (request/add-form-ct)
           (request/add-payload payload)
           ((fn [x] (log/trace "Full request:" x) x)))
       {}
       response/json-handler))
    (deliver (promise) [])))

(defn async-get-metadata-by-ids
  "Get variables by ids"
  [search-endpoint user-token ids]
  (async-get-metadata search-endpoint user-token ids id-search-constraint))

(defn extract-metadata
  [promise]
  (let [rslts @promise]
    (if (errors/erred? rslts)
      (do
        (log/error metadata-errors/variable-metadata)
        (log/error rslts)
        rslts)
      (do
        (log/trace "Got results from CMR variable search:"
                   (results/elided rslts))
        (log/trace "Remaining results:" (results/remaining-items rslts))
        (:items rslts)))))

(defn get-metadata
  [search-endpoint user-token params]
  (let [promise-by-ids (when-let [ids (:variables params)]
                         (async-get-metadata-by-ids search-endpoint user-token ids))
        variables-by-ids (when promise-by-ids
                           (extract-metadata promise-by-ids))]
    variables-by-ids))
