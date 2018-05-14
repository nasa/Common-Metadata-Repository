(ns cmr.opendap.ous.service
  (:require
   [clojure.string :as string]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
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
  (log/debug "Getting service metadata for:" service-ids)
  (let [url (str search-endpoint
                 "/services?"
                 (build-query service-ids))]
    (log/debug "Services query to CMR:" url)
    (request/async-get
     url
     (-> {}
         (request/add-token-header user-token)
         (request/add-accept "application/vnd.nasa.cmr.umm+json"))
     response/json-handler)))

(defn extract-metadata
  [promise]
  (let [results @promise]
    (log/trace "Got results from CMR service search:" results)
    (:items results)))

(defn get-metadata
  [search-endpoint user-token service-ids]
  (let [promise (async-get-metadata search-endpoint user-token service-ids)]
    (extract-metadata promise)))

(defn match-opendap
  [service-data]
  (= "opendap" (string/lower-case (:Type service-data))))

(defn extract-pattern-info
  [service-entry]
  (let [umm (:umm service-entry)]
    (when (match-opendap umm)
      {:service-id (get-in service-entry [:meta :concept-id])
       ;; XXX WARNING!!! The regex's saved in the UMM data are broken!
       ;;                We're manually hacking the regex to fix this ...
       ;;                this makes things EXTREMELY FRAGILE!
       :pattern-match (str "(" (:OnlineAccessURLPatternMatch umm) ")(.*)")
       :pattern-subs (:OnlineAccessURLPatternSubstitution umm)})))
