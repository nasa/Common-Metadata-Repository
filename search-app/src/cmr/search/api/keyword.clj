(ns cmr.search.api.keyword
  "Defines the HTTP URL routes for the keyword endpoint in the search application."
  (:require [compojure.core :refer :all]
            [cheshire.core :as json]
            [camel-snake-kebab.core :as csk]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.transmit.kms :as kms]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]
            [cmr.search.services.query-execution.facets-results-feature :as frf]
            [clojure.string :as str]))

(def gcmd-keyword-scheme-mapping
  "Maps the keyword scheme name used by the CMR to the keyword scheme name used by GCMD."
  {:archive-centers :providers
   :providers :providers
   :science-keywords :science-keywords
   :platforms :platforms
   :instruments :instruments})

(def cmr-keyword-scheme-mapping
  {:archive-centers :archive-centers
   :providers :archive-centers
   :science-keywords :science-keywords
   :platforms :platforms
   :instruments :instruments})

(defn- validate-keyword-scheme
  "Throws a service error if the provided keyword-scheme is invalid."
  [keyword-scheme]
  (let [valid-keywords (keys gcmd-keyword-scheme-mapping)]
    (when-not (contains? (set valid-keywords) keyword-scheme)
      (errors/throw-service-error
        :bad-request (format "The keyword scheme [%s] is not supported. Valid schemes are: %s, and %s."
                             (name keyword-scheme)
                             (str/join
                               ", " (map #(csk/->snake_case (name %)) (rest valid-keywords)))
                             (-> valid-keywords first name csk/->snake_case))))))

(defn- is-leaf?
  "Determines if we are at the leaf point within the hierarchy for the provided keyword."
  [remaining-hierarchy k-word]
  (not (some? (seq (keep #(% k-word) remaining-hierarchy)))))

(defn- get-leaf-values-to-uuids
  "Returns a map of values for the given field to the UUID for that value. If the provided subfield
  is not at the leaf level for a keyword, the value will not be included in the map."
  [keyword-hierarchy field keywords]
  (into {} (keep (fn [k-word]
                   (when (is-leaf? (rest keyword-hierarchy) k-word)
                     [(field k-word) (:uuid k-word)]))
                 keywords)))

(defn- get-subfields-for-keyword
  "Figure out the set of all subfields which are directly below the current field for the provided
  keywords. It is possible the next field in the hierarchy is nil for a keyword, but further down
  the chain there is a non-nil field."
  [keyword-hierarchy keywords field value]
  (loop [remaining-fields keyword-hierarchy
         matching-keywords (filter #(= value (field %)) keywords)
         all-subfields nil]
    (let [next-field (first remaining-fields)
          keywords-below-with-next-field (when next-field (keep next-field matching-keywords))
          keywords-with-nil-next-field (filter #(nil? (next-field %)) matching-keywords)
          all-subfields (if (seq keywords-below-with-next-field)
                          (conj all-subfields next-field)
                          all-subfields)]
      (if (seq (rest remaining-fields))
        (recur (rest remaining-fields) keywords-with-nil-next-field all-subfields)
        all-subfields))))

(defn- get-hierarchy-from-field
  "Returns all of the fields in the hierarchy starting from the provided field and including all
  fields after."
  [keyword-hierarchy field]
  (let [field-index (.indexOf keyword-hierarchy field)]
    (filter #(<= field-index (.indexOf keyword-hierarchy %)) keyword-hierarchy)))

(defn- filter-keywords-with-non-nil-values
  "Filters keywords such that any keywords which have values for any subfields between the start
  of the keyword hierarchy and the provided subfield-name.

  For example (filter-keywords [:a :b :c :d] :d keywords) would filter out any keywords which have
  a non-nil value for :b or :c."
  [keyword-hierarchy subfield-name keywords]
  {:pre (contains? keyword-hierarchy subfield-name)}
  (loop [filtered-keywords keywords
         keyword-hierarchy keyword-hierarchy]
    (let [current-field (first keyword-hierarchy)]
      (if (= current-field subfield-name)
        filtered-keywords
        (recur (filter #(nil? (current-field %)) keywords)
               (rest keyword-hierarchy))))))

(defn- parse-hierarchical-keywords
  "Returns keywords in a hierarchical fashion based on the provided keyword hierarchy and keywords."
  [keyword-hierarchy keywords]
  (when-let [field (first keyword-hierarchy)]
    (let [unique-values (distinct (keep field keywords))
          values-to-uuids (get-leaf-values-to-uuids keyword-hierarchy field keywords)]
      (util/remove-nil-keys
        {field
         (seq (for [value unique-values
                    :let [uuid (get values-to-uuids value)
                          all-subfield-names (get-subfields-for-keyword (rest keyword-hierarchy)
                                                                        keywords field value)
                          subfield-maps (util/remove-nil-keys
                                          (into {}
                                                (reverse
                                                  (for [subfield-name all-subfield-names]
                                                    (parse-hierarchical-keywords
                                                      (get-hierarchy-from-field
                                                        keyword-hierarchy subfield-name)
                                                      (filter-keywords-with-non-nil-values
                                                        (rest keyword-hierarchy)
                                                        subfield-name
                                                        (filter #(= value (field %)) keywords)))))))]]
                (util/remove-nil-keys
                  (merge subfield-maps
                         {:subfields (seq (map name (keys subfield-maps)))
                          :uuid uuid
                          :value value}))))}))))

(def keyword-api-routes
  (context "/keywords" []
    ;; Return a list of keywords for the given scheme
    (GET "/:keyword-scheme" {{:keys [keyword-scheme]} :params
                             request-context :request-context}
      (let [orig-keyword-scheme (csk/->kebab-case-keyword keyword-scheme)]
        (validate-keyword-scheme orig-keyword-scheme)
        (let [cmr-keyword-scheme (orig-keyword-scheme cmr-keyword-scheme-mapping)
              gcmd-keyword-scheme (orig-keyword-scheme gcmd-keyword-scheme-mapping)
              keywords (vals (gcmd-keyword-scheme (kf/get-gcmd-keywords-map request-context)))
              hierarchical-keywords (parse-hierarchical-keywords
                                      (cmr-keyword-scheme frf/nested-fields-mappings) keywords)]
          {:staus 200
           :headers {"Content-Type" (mt/format->mime-type :json)}
           :body (json/generate-string hierarchical-keywords)})))))

(comment

  (def keywords
    (vals (kms/get-keywords-for-keyword-scheme
            {:system (cmr.indexer.system/create-system)} :science-keywords)))

  (parse-hierarchical-keywords (:science-keywords frf/nested-fields-mappings) keywords)

  )


