(ns cmr.bootstrap.data.metadata-retrieval.metadata-transformer
  "Contains functions for converting collection concept metadata into other formats."
  (:require
   [cheshire.core :as json]
   [cmr.common.log :as log :refer (info)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as c-util]
   [cmr.common.xml :as cx]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

;; dynamic is here only for testing purposes to test failure cases.
(defn ^:dynamic transform-strategy
  "Determines which transformation strategy should be used to convert the given concept to the target
   format"
  [concept target-format]
  ;;throw exception if target format is native. That should be handled elsewhere.
  {:pre [(not= :native target-format)]}

  (let [concept-mime-type (:format concept)]
    (cond
      ;; No conversion is required - same format and version.
      (= (mt/mime-type->format concept-mime-type) target-format)
      :current-format

      (and (= :umm-json (mt/format-key concept-mime-type))
           (= :umm-json (mt/format-key target-format)))
      :migrate-umm-json

      :else
      :umm-spec)))

(defn- transform-with-strategy-umm-spec
  [context concept _ target-formats]
  (let [{concept-mime-type :format, metadata :metadata} concept
        ummc (umm-spec/parse-metadata context (:concept-type concept) concept-mime-type metadata)]
    (reduce (fn [translated-map target-format]
              (assoc translated-map target-format
                     (umm-spec/generate-metadata context ummc target-format)))
            {}
            target-formats)))

(defn transform-with-strategy-migrate-umm-json-to-target-format
  "Adds to the translated-map the concept migrated to the passed in target-format."
  [context concept-type source-version metadata translated-map target-format]
  (assoc translated-map target-format
         (umm-json/umm->json
          (c-util/remove-nils-empty-maps-seqs
           (vm/migrate-umm context
                           concept-type
                           source-version
                           (umm-spec/umm-json-version concept-type
                                                      target-format)
                           (json/decode metadata true))))))

(defn- transform-with-strategy-migrate-umm-json
  [context concept _ target-formats]
  (let [{concept-mime-type :format, metadata :metadata, concept-type :concept-type} concept
        source-version (umm-spec/umm-json-version concept-type concept-mime-type)
        [t result] (c-util/time-execution (reduce #(transform-with-strategy-migrate-umm-json-to-target-format
                                                    context
                                                    concept-type
                                                    source-version
                                                    metadata
                                                    %1
                                                    %2)
                                                  {}
                                                  target-formats))]
    (info "transform-with-strategy migrate-umm-json: "
          "time: " t
          "concept-mime-type: " concept-mime-type
          "concept-type: " concept-type
          "parent request num-concepts: " (:num-concepts concept)
          "target-formats: " target-formats
          "source version: " source-version
          "provider: " (:provider-id concept)
          "metadata length: " (count metadata))
    result))

(defn transform-with-strategy
  "Depending on the transformation strategy pick the correct function to call to translate
  the concept to the target format."
  [context concept strategy target-formats]
  (case strategy
    :current-format
    {(mt/mime-type->format (:format concept))
     (cx/remove-xml-processing-instructions (:metadata concept))}

    :migrate-umm-json
    (transform-with-strategy-migrate-umm-json context concept strategy target-formats)

    :umm-spec
    (transform-with-strategy-umm-spec context concept strategy target-formats)

    (errors/internal-error!
     (format "Unexpected transform strategy [%s] from concept of type [%s] to [%s]"
             strategy (:format concept) (pr-str target-formats)))))

(defn transform-to-multiple-formats-with-strategy
  "Transforms a concept into a map of formats dictated by the passed target-formats list."
  [context concept ignore-exceptions? [k v]]
  (if ignore-exceptions?
    (try
      (transform-with-strategy context concept k v)
      (catch Throwable e
        (log/error
         e
         (str "Ignoring exception while trying to transform metadata for concept "
              (:concept-id concept) " with revision " (:revision-id concept) " error: "
              (.getMessage e)))))
    (transform-with-strategy context concept k v)))

(defn transform-to-multiple-formats
  "Transforms the concept into multiple different formats. Returns a map of target format to metadata."
  [context concept target-formats ignore-exceptions?]
  {:pre [(not (:deleted concept))]}
  (->> target-formats
       (group-by #(transform-strategy concept %))
       (keep #(transform-to-multiple-formats-with-strategy context concept ignore-exceptions? %))
       (reduce into {})))
