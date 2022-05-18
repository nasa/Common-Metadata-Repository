(ns cmr.ingest.validation.business-rule-validation
  "Provides functions to validate the ingest business rules"
  (:require
    [clj-time.core :as t]
    [clojure.string :as string]
    [cmr.common.date-time-parser :as p]
    [cmr.common.time-keeper :as tk]
    [cmr.ingest.validation.additional-attribute-validation :as aa]
    [cmr.ingest.validation.instrument-validation :as instrument-validation]
    [cmr.ingest.validation.platform-validation :as platform-validation]
    [cmr.ingest.validation.project-validation :as pv]
    [cmr.ingest.validation.spatial-validation :as sv]
    [cmr.ingest.validation.temporal-validation :as tv]
    [cmr.ingest.validation.tiling-validation :as tiling-validation]
    [cmr.transmit.metadata-db :as mdb]
    [cmr.transmit.search :as search]
    [cmr.umm-spec.umm-spec-core :as spec]))

(defn- version-is-not-nil-validation
  "Validates that the version is not nil"
  [_ concept _]
  (when (nil? (get-in concept [:extra-fields :version-id]))
    ["Version is required."]))

(defn delete-time-validation
  "Validates the concept delete-time.
  Returns error if the delete time exists and is before one minute from the current time."
  ([_ concept]
   (delete-time-validation nil concept nil))
  ([_ concept _]
   (let [delete-time (get-in concept [:extra-fields :delete-time])]
     (when (some-> delete-time
                   p/parse-datetime
                   (t/before? (t/plus (tk/now) (t/minutes 1))))
       [(format "DeleteTime %s is before the current time." delete-time)]))))

(defn- concept-id-validation
  "Validates the concept-id if provided matches the metadata-db concept-id for the concept native-id"
  [context concept _]
  (let [{:keys [concept-type provider-id native-id concept-id]} concept]
    (when concept-id
      (let [mdb-concept-id (mdb/get-concept-id context concept-type provider-id native-id false)]
        (when (and mdb-concept-id (not= concept-id mdb-concept-id))
          [(format "Concept-id [%s] does not match the existing concept-id [%s] for native-id [%s]"
                   concept-id mdb-concept-id native-id)])))))

(def collection-update-searches
  "Defines a list of functions that take the context, concept-id, updated UMM concept and the
   previous UMM concept, and return search maps used to validate that a collection was not updated
   in a way that invalidates granules. Each search map contains a :params key of the parameters to
   use to execute the search and an :error-msg to return if the search finds any hits."
  [aa/additional-attribute-searches
   pv/deleted-project-searches
   instrument-validation/deleted-parent-instrument-searches
   instrument-validation/deleted-child-instrument-searches
   platform-validation/deleted-platform-searches
   tiling-validation/deleted-tiling-searches
   tv/out-of-range-temporal-searches
   sv/spatial-param-change-searches])

(defn- has-granule-search-error
  "Execute the given has-granule search, returns the error message if there are granules found
  by the search."
  [context search-map]
  (let [{:keys [params error-msg]} search-map
        hits (search/find-granule-hits context params)]
    (when (pos? hits)
      (str error-msg (format " Found %d granules." hits)))))

(defn- collection-update-validation
  "Validate collection update does not invalidate any existing granules."
  [context concept prev-concept]
  (let [{:keys [umm-concept]} concept]
    (when prev-concept
      (let [prev-umm-concept (spec/parse-metadata context prev-concept)
            has-granule-searches (mapcat
                                  #(% context (:concept-id prev-concept) umm-concept prev-umm-concept)
                                  collection-update-searches)
            search-errors (->> has-granule-searches
                               (map (partial has-granule-search-error context))
                               (remove nil?))]
        (when (seq search-errors)
          search-errors)))))

(defn standard-product-is-nasa-provider-validation
  "Validate if the standard product is true then the provider must be a NASA provider - meaning 
  that the provider's consortium values must include EOSDIS. If validation fails an error is thrown."
  [context concept _]
  (let [provider-id (:provider-id concept)
        collection (:umm-concept concept)
        standard-product (:StandardProduct collection)
        consortiums-str (some #(when (= provider-id (:provider-id %)) (:consortiums %))
                              (mdb/get-providers context))
        consortiums (when consortiums-str
                      (remove empty? (string/split (string/upper-case consortiums-str) #" ")))]
    (when (and (= true standard-product)
               (not (some #(= "EOSDIS" %) consortiums)))
      [(format (str "Standard product validation failed: "
                    "Standard Product designation is only allowed for NASA data products. This collection is "
                    "being ingested using a non-NASA provider which means the record is not a NASA record. "
                    "Please remove the StandardProduct element from the record."))])))

(defn standard-product-not-real-time-validation
  "Validate if the standard product is true then the CollectionDataType is not NEAR_REAL_TIME, LOW_LATENCY, 
  or EXPEDITED. If validation fails an error is thrown."
  [_ concept _]
  (let [collection (:umm-concept concept)
        standard-product (:StandardProduct collection)
        collection-data-type (:CollectionDataType collection)]
    (when (and (= true standard-product)
               (some #(= collection-data-type %) ["NEAR_REAL_TIME" "LOW_LATENCY" "EXPEDITED"]))
     [(format (str "Standard product validation failed: "
                   "Standard Product cannot be true with the CollectionDataType being one of the following values: "
                   "NEAR_REAL_TIME, LOW_LATENCY, or EXPEDITED. The CollectionDataType is [%s].")
              collection-data-type)])))

(def business-rule-validations
  "A map of concept-type to the list of the functions that validates concept ingest business rules."
  {:collection [delete-time-validation
                concept-id-validation
                version-is-not-nil-validation
                collection-update-validation
                standard-product-is-nasa-provider-validation
                standard-product-not-real-time-validation]
   :granule [delete-time-validation]
   :variable []})
