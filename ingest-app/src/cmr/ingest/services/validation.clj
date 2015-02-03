(ns cmr.ingest.services.validation
  "Provides functions to validate concept"
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [cmr.common.date-time-parser :as p]
            [cmr.common.services.errors :as err]
            [cmr.umm.mime-types :as umm-mime-types]
            [cmr.common.mime-types :as mt]
            [cmr.umm.core :as umm]
            [cmr.umm.validation.core :as umm-validation]
            [cmr.transmit.metadata-db :as mdb]))

(defn- format-validation
  "Validates the format of the concept."
  [concept]
  (let [content-type (:format concept)
        valid-types (umm-mime-types/concept-type->valid-mime-types (:concept-type concept))]
    (if (contains? valid-types content-type)
      []
      [(format "Invalid content-type: %s. Valid content-types: %s."
               content-type (s/join ", " valid-types))])))

(defn- metadata-length-validation
  "Validates the metadata length is not unreasonable."
  [concept]
  (if (> (count (:metadata concept)) 4)
    []
    ["XML content is too short."]))

(def concept-validations
  "A list of the functions that can validate concept."
  [format-validation
   metadata-length-validation])

(defn if-errors-throw
  "Throws an error if there are any errors."
  [errors]
  (when (seq errors)
    (err/throw-service-errors :bad-request errors)))

(defn validate-concept-request
  "Validates the initial request to ingest a concept. "
  [concept]
  (if-errors-throw (mapcat #(% concept) concept-validations)))

(defn validate-concept-xml
  "Validates the concept xml to ingest a concept. "
  [concept]
  (if-errors-throw (umm/validate-concept-xml concept)))

(defn validate-umm-record
  [metadata-format umm]
  (if-errors-throw (umm-validation/validate (mt/mime-type->format metadata-format) umm)))

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

(defn- entry-id-validation
  "Validates the native-id of existing concept for the entry-id must match the one of updated concept.
  Entry-id is only inate to DIF format which has its entry-id also as its short-name;
  For all the other formats, entry-id is a combination of short-name and version-id.
  So here, we will search for existing concept based on short-name only for the DIF format;
  For all the other formats, use short-name and version-id."
  [context concept]
  (let [{{:keys [entry-id]} :extra-fields
         provider-id :provider-id} concept
        params {:provider-id provider-id
                :entry-id entry-id}
        mdb-concepts (mdb/find-visible-collections context params)]
    (when (> (count mdb-concepts) 1)
      (err/internal-error!
        (format "Found multiple collections on entry-id search with parms: %s. Collections found: %s"
                (pr-str params) (pr-str mdb-concepts))))

    (when-let [mdb-concept (first mdb-concepts)]
      (when-not (= (:native-id concept) (:native-id mdb-concept))
        [(format "Failed entry-id validation: collection with native-id [%s] already exists for %s"
                 (:native-id mdb-concept) (pr-str params))]))))

(def business-rule-validations
  "A map of concept-type to the list of the functions that validates concept ingest business rules."
  {:collection [delete-time-validation
                concept-id-validation
                entry-id-validation]
   :granule []})

(defn validate-business-rules
  "Validates the concept against CMR ingest rules."
  [context concept]
  (if-errors-throw (mapcat #(% context concept)
                           (business-rule-validations (:concept-type concept)))))
