(ns cmr.ingest.validation.variable-validation
  "Provides functions to validate the variable during ingest"
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.validations.core :as v]
   [cmr.ingest.config :as config]
   [cmr.ingest.services.messages :as msg]
   [cmr.ingest.validation.validation :as validation]
   [cmr.transmit.search :as transmit-search]
   [cmr.umm-spec.validation.umm-spec-validation-core :as umm-spec-validation]))

(defn- measurement-validation
  "A validation that checks that the measurement matches a known KMS field. Takes the following arguments:

  * context - The request context that holds the cached keywords.
  * msg-fn - A function taking the invalid measurements and returning the error to return to the user
    if it doesn't match."
  [context msg-fn]
  (v/every
   (fn [field-path value]
     (when-let [invalid-measurements (cmr.common-app.services.kms-lookup/lookup-by-measurement context value)]
       {field-path [(msg-fn invalid-measurements)]}))))

(defn- variable-keyword-validations
  "Creates validations that check various collection fields to see if they match KMS keywords."
  [context]
  {:MeasurementIdentifiers (measurement-validation
                            context msg/measurements-not-matches-kms-keywords)
   :RelatedURLs (v/every [{:Format (validation/match-kms-keywords-validation-single
                                    context
                                    :granule-data-format
                                    msg/getdata-format-not-matches-kms-keywords)}])})

(defn- variable-keyword-validations-warnings
  "Creates validations that check Mimetypes to see if they match KMS keywords that will be
   returned as warnings."
  [context]
  {:RelatedURLs (v/every [{:MimeType (validation/match-kms-keywords-validation-single
                                      context
                                      :mime-type
                                      msg/mime-type-not-matches-kms-keywords)}])})

(defn- variable-keyword-validations-unignorable
  "Creates validations that check various collection fields to see if they match
  KMS keywords. These validations must always pass regardless of the warn? flag."
  [context]
  {:RelatedURLs [(validation/match-related-url-kms-keywords-validations context)]})

(defn umm-spec-validate-variable
  "Validate variable through umm-spec validation functions. If warn? flag is
   true and umm-spec-validation is off, log warnings and return messages, otherwise throw errors."
  [variable context warn?]
  (when-let [non-ignorable (seq (umm-spec-validation/validate-variable-with-no-defaults
                                 variable
                                 [(variable-keyword-validations-unignorable context)]))]
    (errors/throw-service-errors :invalid-data non-ignorable))
  (let [err-messages (seq (umm-spec-validation/validate-variable
                           variable
                           [(variable-keyword-validations context)]))
        warning-messages (seq (umm-spec-validation/validate-variable
                               variable
                               [(variable-keyword-validations-warnings context)]))]
    (if (or (config/return-umm-spec-validation-errors)
            (not warn?))
      ;;when we are not supposed to return error as warnings
      (if err-messages
        ;; throw errors when it exists
        (errors/throw-service-errors :invalid-data err-messages)
        ;; throw warnings when error doesn't exist.
        (when warning-messages
          (cmr.common.log/warn "UMM-Var UMM Spec Validation Errors: " (pr-str (vec warning-messages)))
          warning-messages))
      ;; when we are supposed to return errors as warnings as well,
      ;; return both errors and warnings as warnings.
      (when-let [all-warning-messages (seq (umm-spec-validation/validate-variable
                                            variable
                                            [(variable-keyword-validations context)
                                             (variable-keyword-validations-warnings context)]))]
        (cmr.common.log/warn "UMM-Var UMM Spec Validation Errors: " (pr-str (vec all-warning-messages)))
        all-warning-messages))))

(defn validate-variable-associated-collection
  "Validate the collection being associated to is accessible."
  [context coll-concept-id coll-revision-id]
  (when coll-concept-id
    (let [params (if coll-revision-id
                   {:concept-id coll-concept-id
                    :all-revisions true}
                   {:concept-id coll-concept-id})
          response (transmit-search/search-for-collections
                    context
                    params
                    {:raw? true
                     :http-options {:accept mt/umm-json}})
          items (-> response
                    :body
                    (json/parse-string true)
                    :items)
          revision-info (->> items
                             (map :meta)
                             (map #(select-keys % [:revision-id :deleted])))]
      (if (seq revision-info)
        (when coll-revision-id
          (if-let [revision (->> revision-info
                                 (filter #(= (edn/read-string coll-revision-id)
                                             (:revision-id %)))
                                 first)]
            (when (:deleted revision)
              (errors/throw-service-error
               :invalid-data
               (format "Collection [%s] revision [%s] is deleted"
                       coll-concept-id coll-revision-id)))
            (errors/throw-service-error
             :invalid-data
             (format "Collection [%s] revision [%s] does not exist"
                     coll-concept-id coll-revision-id))))
        (errors/throw-service-error
         :invalid-data
         (format "Collection [%s] does not exist or is not visible."
                 coll-concept-id))))))
