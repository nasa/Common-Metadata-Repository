(ns cmr.ingest.services.ingest-service.collection
  (:require
   [clojure.string :as string]
   [cmr.common.api.context :as common-context]
   [cmr.common.log :as log :refer (warn)]
   [cmr.common.util :refer [defn-timed]]
   [cmr.ingest.services.helper :as ingest-helper]
   [cmr.ingest.services.ingest-service.util :as util]
   [cmr.ingest.validation.validation :as v]
   [cmr.transmit.kms :as transmit-kms]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.umm-spec.umm-spec-core :as spec]
   [cmr.umm.collection.entry-id :as eid]))

(defn add-extra-fields-for-collection
  "Returns collection concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [_ concept collection]
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
  ([context collection-concept validation-options]
   (validate-and-parse-collection-concept context collection-concept nil validation-options))
  ([context collection-concept prev-concept validation-options]
   (v/validate-concept-request collection-concept)
   (when-not (:bulk-update? validation-options)
     (v/validate-concept-metadata collection-concept))
   (let [{:keys [format metadata]} collection-concept
         collection (spec/parse-metadata context :collection format metadata {:sanitize? false})
         sanitized-collection (spec/parse-metadata context :collection format metadata)
         sanitized-prev-collection (when prev-concept
                                     (spec/parse-metadata
                                      context
                                      :collection
                                      (:format prev-concept)
                                      (:metadata prev-concept)))
         _ (def sc sanitized-collection)
         _ (def scp sanitized-prev-collection)
         _ (def vo validation-options)
         _ (def c1 context)

         ;; context + validation-options are identical for every validation below,
         ;; so build the keyword validation rules once and thread them through.
         kw-rules (v/keyword-validation-rules context validation-options)

         {existing-errors :errors has-keyword-error? :has-keyword-error?}
         (v/umm-spec-validate-collection
          sanitized-collection sanitized-prev-collection kw-rules validation-options context false)
         _ (def off-func-exist-errors existing-errors)
         existing-errors (map #(str (:path %) " " (string/join " " (:errors %)))
                              existing-errors)

         ;; Return warnings for schema validation errors going from xml -> UMM
         collection-schema-warnings (v/validate-collection-umm-spec-schema collection validation-options)

         ;; Return warnings for validation errors on collection without sanitization
         {non-sanitized-errors :errors} (v/umm-spec-validate-collection
                                         collection nil kw-rules validation-options context true)
         {warning-errors :errors warning-has-keyword-error? :has-keyword-error?}
         (v/umm-spec-validate-collection-warnings
          collection validation-options context)
         has-keyword-error? (or has-keyword-error? warning-has-keyword-error?)
         collection-warnings (concat non-sanitized-errors warning-errors)
         collection-warnings (map #(str (:path %) " " (string/join " " (:errors %)))
                                  collection-warnings)
         warnings (concat collection-schema-warnings collection-warnings)]
     (def cw collection-warnings)
     (def hke has-keyword-error?)

     ;; The sanitized UMM Spec collection is returned so that ingest does not fail
     {:collection sanitized-collection
      :warnings warnings
      :existing-errors existing-errors
      :has-keyword-error? has-keyword-error?})))

(defn-timed validate-and-prepare-collection
  "Validates the collection and adds extra fields needed for metadata db. Throws a service error
  if any validation issues are found and errors are enabled, otherwise returns errors as warnings."
  [context concept validation-options]
  (let [concept (update-in concept [:format] (partial util/fix-ingest-concept-format :collection))
        {:keys [provider-id native-id]} concept
        prev-concept (first (ingest-helper/find-visible-collections context {:provider-id provider-id
                                                                             :native-id native-id}))
        {:keys [collection warnings existing-errors has-keyword-error?]} (validate-and-parse-collection-concept
                                                                          context
                                                                          concept
                                                                          prev-concept
                                                                          validation-options)
        ;; Add extra fields for the collection
        coll-concept (assoc (add-extra-fields-for-collection context concept collection)
                            :umm-concept collection)]
    ;; progressive update doesn't apply to business rules validation.
    (v/validate-business-rules context coll-concept prev-concept)
    (def hke has-keyword-error?)
    (tap> {:locaiton "validate-and-prepare-collection" :body has-keyword-error?})
    {:concept coll-concept
     :warnings warnings
     :existing-errors existing-errors
     :has-keyword-error? has-keyword-error?}))

(defn-timed save-collection
  "Store a concept in mdb and indexer.
   Return entry-title, concept-id, revision-id, and warnings."
  [context concept validation-options]
  (def c1 context)
  (def con1 concept)
  (let [{:keys [concept warnings existing-errors has-keyword-error?]} (validate-and-prepare-collection context
                                                                                                       concept
                                                                                                       validation-options)
        {:keys [concept-id revision-id]} (mdb/save-concept context concept)
        entry-title (get-in concept [:extra-fields :entry-title])]
    (def hke1 has-keyword-error?)
    (def ee existing-errors)
    (def w warnings)
      ;; if ingested with existing errors, log the existing errors and warnings for the collection
      ;; and the user
    (when (seq existing-errors)
      (warn "Ingest with existing errors info:  "
            (format "Collection[%s] has the existing errors: %s and warnings: %s by user: [%s]"
                    concept-id (pr-str existing-errors) (pr-str warnings)
                    (if (:token context)
                      (common-context/context->user-id context)
                      "unknown user"))))
      (when (and has-keyword-error?
                 (or (seq existing-errors) (seq warnings)))
            (tap> "About to go send data to kms fixer")   
            (transmit-kms/send-to-kms-metadata-fixer-test concept-id))
    {:entry-title entry-title
     :concept-id concept-id
     :revision-id revision-id
     :warnings warnings
     :existing-errors existing-errors}))

(comment
  (v/umm-spec-validate-collection sc nil vo c1 true)
  (v/umm-spec-validate-collection
   sc nil (v/keyword-validation-rules c1 vo) vo c1 false)
;; The conditional statements
(when (and hke1
           (or (seq ee) (seq w))))

(when true
      (transmit-kms/send-to-kms-metadata-fixer c1 "C1200000001-PROV1"))

   (when (and hke1
               (or (seq ee) (seq w)))
      (transmit-kms/send-to-kms-metadata-fixer-test c1))
  :rcf)
;; (v/umm-spec-validate-collection
;;    collection nil kw-rules validation-options context true))
