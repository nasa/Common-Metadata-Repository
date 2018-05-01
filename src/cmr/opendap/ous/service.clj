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
  (string/join "&" (map #(str (codec/url-encode "concept_id[]")
                              "=" %)
                        service-ids)))

(defn get-metadata
  "Given a service-id, get the metadata for the associate service."
  [search-endpoint user-token service-ids]
  (log/debug "Getting service metadata for:" service-ids)
  (let [url (str search-endpoint
                 "/services?"
                 (build-query service-ids))
        results (request/async-get url
                 (-> {}
                     (request/add-token-header user-token)
                     (request/add-accept "application/vnd.nasa.cmr.umm+json"))
                 response/json-handler)]
    (log/debug "Got results from CMR service search:" results)
    (:items @results)))

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
