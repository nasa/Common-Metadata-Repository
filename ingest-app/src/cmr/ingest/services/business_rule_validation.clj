(ns cmr.ingest.services.business-rule-validation
  "Provides functions to validate the ingest business rules"
  (:require [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [cmr.common.date-time-parser :as p]
            [cmr.umm.core :as umm]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.transmit.search :as search]
            [cmr.ingest.services.helper :as h]
            [cmr.ingest.services.additional-attribute-validation :as aa]))

(defn- delete-time-validation
  "Validates the concept delete-time.
  Returns error if the delete time exists and is before one minute from the current time."
  [_ concept]
  (let [delete-time (get-in concept [:extra-fields :delete-time])]
    (when (some-> delete-time
                  p/parse-datetime
                  (t/before? (t/plus (tk/now) (t/minutes 1))))
      [(format "DeleteTime %s is before the current time." delete-time)])))

(defn- concept-id-validation
  "Validates the concept-id if provided matches the metadata-db concept-id for the concept native-id"
  [context concept]
  (let [{:keys [concept-type provider-id native-id concept-id]} concept]
    (when concept-id
      (let [mdb-concept-id (mdb/get-concept-id context concept-type provider-id native-id false)]
        (when (and mdb-concept-id (not= concept-id mdb-concept-id))
          [(format "Concept-id [%s] does not match the existing concept-id [%s] for native-id [%s]"
                   concept-id mdb-concept-id native-id)])))))

(defn- has-granule?
  "Execute the given has-granule search, returns the error message if there are granules found
  by the search."
  [context search-map]
  (let [{:keys [params error-msg]} search-map
        hits (search/find-granule-hits context params)]
    (when (> hits 0)
      (str error-msg (format " Found %d granules." hits)))))

(def collection-update-searches
  "Defines a list of functions to construct the collection update searches in terms of search params
  and error message to return when the search found invalidated granules. All functions take two
  arguments: the UMM concept and the previous UMM concept."
  [aa/additional-attribute-searches])

(defn- collection-update-validation
  [context concept]
  (let [{:keys [provider-id extra-fields umm-concept]} concept
        {:keys [entry-title]} extra-fields
        prev-concept (first (h/find-visible-collections context {:provider-id provider-id
                                                                 :entry-title entry-title}))]
    (when prev-concept
      (let [prev-umm-concept (umm/parse-concept prev-concept)
            has-granule-searches (mapcat #(% umm-concept prev-umm-concept) collection-update-searches)
            search-errors (->> has-granule-searches
                               (map (partial has-granule? context))
                               (remove nil?))]
        (when (seq search-errors)
          search-errors)))))

(def business-rule-validations
  "A map of concept-type to the list of the functions that validates concept ingest business rules."
  {:collection [delete-time-validation
                concept-id-validation
                collection-update-validation]
   :granule []})

