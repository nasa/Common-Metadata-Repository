(ns cmr.opendap.ous.granule
  (:require
   [clojure.string :as string]
   [cmr.opendap.const :as const]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.util :as util]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

(defn build-include
  [gran-ids]
  (string/join "&" (map #(str (codec/url-encode "concept_id[])=")
                              %)
                        gran-ids)))

(defn build-exclude
  [gran-ids]
  (string/join "&" (map #(str (codec/url-encode "exclude[echo_granule_id][]=")
                              %)
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
  (let [url (str search-endpoint
                 "/granules?"
                 (build-query params))
        results (request/async-get url
                 (-> {}
                     (request/add-token-header user-token)
                     (request/add-accept "application/json"))
                 response/json-handler)]
    (log/debug "Got results from CMR search:" results)
    (get-in @results [:feed :entry])))

;; XXX This logic was copied from the prototype; it is generally viewed by the
;;     CMR Team & the Metadata Tools Team that this approach is flawed, and
;;     that adding support for this approach to UMM-S was a short-term hack.
(defn match-datafile-link
  "The criteria defined in the prototype was to iterate through the links,
  only examining those links that were not 'inherited', and find the one
  whose :rel value matched a particular string.

  It is currently unclear what the best criteria for this decision is."
  [link-data]
  (let [rel (:rel link-data)]
    (and (not (:inherited link-data))
              (= const/datafile-link-rel rel))))

(defn extract-datafile-link
  [granule-entry]
  (let [link (->> (:links granule-entry)
                  (filter match-datafile-link)
                  first)]
    {:granule-id (:id granule-entry)
     :link-rel (:rel link)
     :link-href (:href link)}))
