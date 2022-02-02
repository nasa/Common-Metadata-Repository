(ns cmr.search.api.keyword
  "Defines the HTTP URL routes for the keyword endpoint in the search application.

  The keyword endpoint is used to retrieve the keywords for each of the controlled vocabulary
  fields. The controlled vocabulary is cached within CMR, but the actual source is the GCMD KMS
  system. Users of this endpoint are interested in knowing what the CMR considers as the current
  controlled vocabulary, since it is the cached CMR values that will eventually be enforced on CMR
  ingest.

  The keywords are returned in a hierarchical format. The response format is such that the caller
  does not need to know the hierarchy, but it can be inferred from the results. Keywords are not
  guaranteed to have values for every subfield in the hierarchy, so the response will indicate
  the next subfield below the current field in the hierarchy which has a value. It is possible for
  the keywords to have multiple potential subfields below it for different keywords with the same
  value for the current field in the hierarchy. When this occurs the subfields property will
  include each of the subfields.

  See the unit tests in cmr.search.test.api.keyword for detailed examples of the responses.

  Commonly used parameters in this namespace are:
  keyword-scheme     The type of keyword. For example :platforms or :instruments.
  keyword-hierarchy  An ordered array of subfields for the given keyword scheme. For example,
  [:basis :category :sub-category :short-name :long-name] for the :platforms keyword scheme.
  keyword-map        A single GCMD keyword with each of its subfields as a key in a map. If the
                     keyword does not have a value for a subfield, that key will not be present in
                     the map."
  (:require [compojure.core :refer :all]
            [cheshire.core :as json]
            [camel-snake-kebab.core :as csk]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]
            [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
            [clojure.string :as str]
            [clojure.set :as set]
            [cmr.transmit.kms :as kms]))

(def sorted-hierarchical-map
  "A map that sorts the keys of the hierarchical map so it is presented in a pleasing way to Users
  of the API. The nested hierarchical maps API can be hard to understand if the maps are ordered
  randomly"
  (util/key-sorted-map [:value :uuid :subfields]))

(defn- validate-keyword-scheme
  "Throws a service error if the provided keyword-scheme is invalid."
  [keyword-scheme]
  (let [valid-keywords (concat (keys kms/keyword-scheme->field-names)
                               (keys kms/cmr-to-gcmd-keyword-scheme-aliases))]
    (when-not (contains? (set valid-keywords) keyword-scheme)
      (errors/throw-service-error
        :bad-request
        (format "The keyword scheme [%s] is not supported. Valid schemes are: %s, and %s."
                (name keyword-scheme)
                (str/join
                  ", " (map #(csk/->snake_case (name %)) (rest valid-keywords)))
                (-> valid-keywords first name csk/->snake_case))))))

(defn- keyword->hierarchy
  "Takes a flat keyword map and converts it to a hierarchical map."
  [keyword-map keyword-hierarchy]
  (let [[current-subfield & remaining-subfields] keyword-hierarchy
        current-subfield-snake-case (csk/->snake_case current-subfield)
        current-value (get keyword-map current-subfield)

        subfield-hierarchy (when (seq remaining-subfields)
                             (keyword->hierarchy keyword-map remaining-subfields))]
    (when (or current-value subfield-hierarchy)
      (if subfield-hierarchy
        {current-subfield-snake-case #{(merge
                                         subfield-hierarchy
                                         {:value current-value})}}
        {current-subfield-snake-case #{{:value current-value
                                        :uuid (:uuid keyword-map)}}}))))

(defn- merge-hierarchical-maps
  "Takes two hierarchical maps and merges them together."
  [hm1 hm2]
  ;; Merge the two maps without their values
  (let [merged-map (merge-with
                     (fn [existing-values new-values]
                       ;; Find the values in common by using map-by and then merge those maps.
                       ;; The merge-with here recursively calls into the same function
                       (->> (merge-with merge-hierarchical-maps
                                        (util/map-by :value existing-values)
                                        (util/map-by :value new-values))
                            vals
                            set))
                     (dissoc hm1 :value :uuid)
                     (dissoc hm2 :value :uuid))]
    ;; Add value and uuid back in if it was there originally
    (as-> merged-map hm
          (if (contains? hm1 :value)
            (assoc hm :value (:value hm1))
            hm)
          (if-let [uuid (or (:uuid hm1) (:uuid hm2))]
            (assoc hm :uuid uuid)
            hm))))

(defn- seq-set
  "Converts a collection to a set or returns nil if the collection is empty."
  [coll]
  (when (seq coll)
    (set coll)))

(defn- collapse-hierarchical-map*
  "Removes intermediate nil values in the hierarchical map."
  [hm]
  (let [collapsed-map (reduce
                        (fn [new-hm [k values]]
                          (let [values-by-value (util/map-by :value values)]
                            ;; Determine if one of the values was nil
                            (if-let [nil-value-map (get values-by-value nil)]
                              (merge new-hm
                                     ;; If it's nil then we should collapse it and then merge in its
                                     ;; contents into the new map. This is what actually does the
                                     ;; collapsing
                                     (collapse-hierarchical-map*
                                       (dissoc nil-value-map :value))
                                     ;; Add on the other values within the original key
                                     {k (seq-set (map collapse-hierarchical-map*
                                                      (vals (dissoc values-by-value nil))))})
                              ;; There's no nil values so collapse the inner values and associate
                              ;; it with the original key
                              (assoc new-hm k (seq-set (map collapse-hierarchical-map* values))))))
                        sorted-hierarchical-map
                        (dissoc hm :value :uuid))
        ;; Remove the empty subfields so we can get the correct list of subfields
        subfields (-> collapsed-map
                      util/remove-nil-keys (dissoc :value :uuid :subfields) keys seq-set)]
    (util/remove-nil-keys
      (assoc collapsed-map
             :subfields subfields
             :uuid (:uuid hm)
             :value (:value hm)))))

(defn- collapse-hierarchical-map
  "Takes a hierarchical map and collapses it so that any subfields with nil values are removed
  from the map. Also adds a subfields key to indicate the subfields directly below a given field.
  The subfields key is not included at the topmost level."
  [hm]
  (dissoc (collapse-hierarchical-map* hm) :subfields))

(defn- flat-keywords->hierarchical-keywords
  "Converts flat keywords into hierarchical keywords."
  [flat-keywords keyword-hierarchy]
  (->> flat-keywords
       (map #(keyword->hierarchy % keyword-hierarchy))
       (reduce merge-hierarchical-maps sorted-hierarchical-map)
       collapse-hierarchical-map))

(defn- get-hierarchical-keywords
  "Returns hierarchical keywords for the provided keyword scheme. Returns a 400 error if the
  keyword scheme is invalid."
  [context keyword-scheme]
  (let [orig-keyword-scheme (csk/->kebab-case-keyword keyword-scheme)]
    (validate-keyword-scheme orig-keyword-scheme)
    (let [cmr-keyword-scheme (kms/translate-keyword-scheme-to-cmr orig-keyword-scheme)
          gcmd-keyword-scheme (kms/translate-keyword-scheme-to-gcmd orig-keyword-scheme)
          keywords (gcmd-keyword-scheme (kf/get-kms-index context))
          keyword-hierarchy (cmr-keyword-scheme kf/nested-fields-mappings)
          hierarchical-keywords (flat-keywords->hierarchical-keywords keywords keyword-hierarchy)]
      {:staus 200
       :headers {"Content-Type" (mt/with-utf-8 mt/json)}
       :body (json/generate-string hierarchical-keywords)})))

(def keyword-api-routes
  (context "/keywords" []
    ;; Return a list of keywords for the given scheme
    (GET "/:keyword-scheme" {params :params
                             request-context :request-context}
      (let [keyword-scheme (:keyword-scheme params)
            search-params (dissoc params :keyword-scheme)
            non-empty-search-params (util/remove-map-keys empty? search-params)]
        (if (> (count (keys non-empty-search-params)) 0)
          (errors/throw-service-error
           :bad-request
           (format "Search parameter filters are not supported: [%s]" non-empty-search-params))
          (get-hierarchical-keywords request-context keyword-scheme))))))
