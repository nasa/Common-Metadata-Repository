(ns cmr.ingest.services.ingest-service.collection
  (:require
   [clojure.string :as string]
   [cmr.common.util :refer [defn-timed]]
   [cmr.ingest.services.ingest-service.util :as util]
   [cmr.ingest.validation.validation :as v]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.umm-spec.umm-spec-core :as spec]
   [cmr.umm.collection.entry-id :as eid]))

(defn add-extra-fields-for-collection
  "Returns collection concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [context concept collection]
  (let [{short-name :ShortName
         version-id :Version
         entry-title :EntryTitle} collection
        entry-id (eid/entry-id short-name version-id)
        delete-time (first (map :Date (filter #(= "DELETE" (:Type %)) (:DataDates collection))))]
    (assoc concept :extra-fields {:entry-title entry-title
                                  :entry-id entry-id
                                  :short-name short-name
                                  :version-id version-id
                                  :delete-time (when delete-time (str delete-time))})))

(defn validate-and-parse-collection-concept
  "Validates a collection concept and parses it. Returns the UMM record and any warnings from
  validation."
  [context collection-concept validation-options]
  (v/validate-concept-request collection-concept)
  (v/validate-concept-metadata collection-concept)
  (let [{:keys [format metadata]} collection-concept
        collection (spec/parse-metadata context :collection format metadata {:sanitize? false})
        sanitized-collection (spec/parse-metadata context :collection format metadata)
        ;; Throw errors for validation on sanitized collection
        _ (v/umm-spec-validate-collection sanitized-collection validation-options context false)
        ;; Return warnings for schema validation errors going from xml -> UMM
        warnings (v/validate-collection-umm-spec-schema collection validation-options)
        ;; Return warnings for validation errors on collection without sanitization
        collection-warnings (concat
                             (v/umm-spec-validate-collection collection validation-options context true)
                             (v/umm-spec-validate-collection-warnings
                              collection validation-options context))
        collection-warnings (map #(str (:path %) " " (string/join " " (:errors %)))
                                 collection-warnings)
        warnings (concat warnings collection-warnings)]
    ;; The sanitized UMM Spec collection is returned so that ingest does not fail
    {:collection sanitized-collection
     :warnings warnings}))

(defn-timed validate-and-prepare-collection
  "Validates the collection and adds extra fields needed for metadata db. Throws a service error
  if any validation issues are found and errors are enabled, otherwise returns errors as warnings."
  [context concept validation-options]
  (let [concept (update-in concept [:format] util/fix-ingest-concept-format)
        {:keys [collection warnings]} (validate-and-parse-collection-concept context
                                                                             concept
                                                                             validation-options)
        ;; Add extra fields for the collection
        coll-concept (add-extra-fields-for-collection context concept collection)]
    ;; Validate ingest business rules through umm-spec-lib
    (v/validate-business-rules
     context (assoc coll-concept :umm-concept collection))
    {:concept coll-concept
     :warnings warnings}))

(defn-timed save-collection
  "Store a concept in mdb and indexer.
   Return entry-titile, concept-id, revision-id, and warnings."
  [context concept validation-options]
  (let [{:keys [concept warnings]} (validate-and-prepare-collection context
                                                                    concept
                                                                    validation-options)]
    (let [{:keys [concept-id revision-id]} (mdb/save-concept context concept)
          entry-title (get-in concept [:extra-fields :entry-title])]
      {:entry-title entry-title
       :concept-id concept-id
       :revision-id revision-id
       :warnings warnings})))
