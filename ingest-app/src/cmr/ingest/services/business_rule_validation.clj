(ns cmr.ingest.services.business-rule-validation
  "Provides functions to validate the ingest business rules"
  (:require [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [cmr.common.date-time-parser :as p]
            [cmr.common.services.errors :as err]
            [cmr.transmit.metadata-db :as mdb]))

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

(defn- entry-id-unique-validation
  "Validates the native-id of existing concept for the entry-id must match the one of updated concept.
  Entry-id is only inate to DIF format which has its entry-id also as its short-name;
  For all the other formats, entry-id is a combination of short-name and version-id.
  So here, we will search for existing concept based on short-name only for the DIF format;
  For all the other formats, use short-name and version-id."
  [context concept]
  (let [{{:keys [entry-id]} :extra-fields
         provider-id :provider-id
         native-id :native-id} concept
        params {:provider-id provider-id
                :entry-id entry-id}
        mdb-concepts (mdb/find-visible-collections context params)]
    (when (> (count mdb-concepts) 1)
      (err/internal-error!
        (format "Found multiple collections on entry-id search with params: %s. Collections found: %s"
                (pr-str params) (pr-str mdb-concepts))))

    (when-let [mdb-concept (first mdb-concepts)]
      (when-not (= native-id (:native-id mdb-concept))
        [(format "The EntryId was not unique. The collection with native id [%s] in provider [%s] had the same EntryId [%s]."
                 native-id provider-id entry-id)]))))

(def business-rule-validations
  "A map of concept-type to the list of the functions that validates concept ingest business rules."
  {:collection [delete-time-validation
                concept-id-validation
                entry-id-unique-validation]
   :granule []})


