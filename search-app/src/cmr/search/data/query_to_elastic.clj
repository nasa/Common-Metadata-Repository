(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojure.string :as str]
            ;; require it so it will be available
            [cmr.search.data.query-order-by-expense]
            [cmr.common.services.errors :as errors]
            [cmr.search.data.keywords-to-elastic :as k2e]
            [clojurewerkz.elastisch.query :as eq]
            [cmr.common-app.services.search.query-model :as q]
            [cmr.common-app.services.search.query-order-by-expense :as query-expense]
            [cmr.common-app.services.search.query-to-elastic :as q2e]
            [cmr.common-app.services.search.complex-to-simple :as c2s]
            [cmr.common.config :refer [defconfig]]))

(defconfig use-doc-values-fields
  "Indicates whether search fields should use the doc-values fields or not. If false the field data
  cache fields will be used."
  {:type Boolean
   :default true})

(defmethod q2e/concept-type->field-mappings :collection
  [_]
  (let [default-mappings {:provider :provider-id
                          :version :version-id
                          :project :project-sn2
                          :project-sn :project-sn2
                          :updated-since :revision-date2
                          :two-d-coordinate-system-name :two-d-coord-name
                          :platform :platform-sn
                          :instrument :instrument-sn
                          :sensor :sensor-sn
                          :revision-date :revision-date2}]
    (if (use-doc-values-fields)
      (merge default-mappings
             {:mbr-north :mbr-north-doc-values
              :mbr-south :mbr-south-doc-values
              :mbr-east :mbr-east-doc-values
              :mbr-west :mbr-west-doc-values
              :lr-north :lr-north-doc-values
              :lr-south :lr-south-doc-values
              :lr-east :lr-east-doc-values
              :lr-west :lr-west-doc-values})
      default-mappings)))

(defmethod q2e/concept-type->field-mappings :granule
  [_]
  (let [default-mappings {:granule-ur.lowercase :granule-ur.lowercase2
                          :producer-gran-id.lowercase :producer-gran-id.lowercase2
                          :provider :provider-id
                          :updated-since :revision-date
                          :producer-granule-id :producer-gran-id
                          :platform :platform-sn
                          :instrument :instrument-sn
                          :sensor :sensor-sn
                          :project :project-refs}]
        (if (use-doc-values-fields)
          (merge default-mappings
                 {:provider :provider-id-doc-values
                  :provider-id :provider-id-doc-values
                  :concept-seq-id :concept-seq-id-doc-values
                  :collection-concept-id :collection-concept-id-doc-values
                  :collection-concept-seq-id :collection-concept-seq-id-doc-values
                  :size :size-doc-values
                  :start-date :start-date-doc-values
                  :end-date :end-date-doc-values
                  :revision-date :revision-date-doc-values
                  :updated-since :revision-date-doc-values
                  :day-night :day-night-doc-values
                  :cloud-cover :cloud-cover-doc-values
                  :mbr-north :mbr-north-doc-values
                  :mbr-south :mbr-south-doc-values
                  :mbr-east :mbr-east-doc-values
                  :mbr-west :mbr-west-doc-values
                  :lr-north :lr-north-doc-values
                  :lr-south :lr-south-doc-values
                  :lr-east :lr-east-doc-values
                  :lr-west :lr-west-doc-values
                  :orbit-start-clat :orbit-start-clat-doc-values
                  :orbit-end-clat :orbit-end-clat-doc-values
                  :orbit-asc-crossing-lon :orbit-asc-crossing-lon-doc-values
                  :access-value :access-value-doc-values
                  :start-coordinate-1 :start-coordinate-1-doc-values
                  :end-coordinate-1 :end-coordinate-1-doc-values
                  :start-coordinate-2 :start-coordinate-2-doc-values
                  :end-coordinate-2 :end-coordinate-2-doc-values})
          default-mappings)))

(defmethod q2e/elastic-field->query-field-mappings :collection
  [_]
  {:project-sn2 :project-sn
   :two-d-coord-name :two-d-coordinate-system-name
   :platform-sn :platform
   :instrument-sn :instrument
   :sensor-sn :sensor
   :revision-date2 :revision-date})

(defmethod q2e/elastic-field->query-field-mappings :granule
  [_]
  {:provider-id-doc-values :provider-id
   :collection-concept-id-doc-values :collection-concept-id
   :collection-concept-seq-id-doc-values :collection-concept-seq-id
   :concept-seq-id-doc-values :concept-seq-id
   :size-doc-values :size
   :start-date-doc-values :start-date
   :end-date-doc-values :end-date
   :revision-date-doc-values :revision-date
   :platform-sn :platform
   :instrument-sn :instrument
   :sensor-sn :sensor
   :project-refs :project
   :day-night-doc-values :day-night
   :cloud-cover-doc-values :cloud-cover
   :orbit-start-clat-doc-values :orbit-start-clat
   :orbit-end-clat-doc-values :orbit-end-clat
   :orbit-asc-crossing-lon-doc-values :orbit-asc-crossing-lon
   :access-value-doc-values :access-value
   :start-coordinate-1-doc-values :start-coordinate-1
   :end-coordinate-1-doc-values :end-coordinate-1
   :start-coordinate-2-doc-values :start-coordinate-2
   :end-coordinate-2-doc-values :end-coordinate-2})

(defmethod q2e/field->lowercase-field-mappings :collection
  [_]
  {:provider "provider-id.lowercase"
   :version "version-id.lowercase"
   :project "project-sn2.lowercase"
   :two-d-coordinate-system-name "two-d-coord-name.lowercase"
   :platform "platform-sn.lowercase"
   :instrument "instrument-sn.lowercase"
   :sensor "sensor-sn.lowercase"})

(defmethod q2e/field->lowercase-field-mappings :granule
  [_]
  (let [default-mappings
        {:granule-ur "granule-ur.lowercase2"
         :producer-gran-id "producer-gran-id.lowercase2"
         :producer-granule-id "producer-gran-id.lowercase2"
         :version "version-id.lowercase"}]
    (if (use-doc-values-fields)
      (merge default-mappings {:provider "provider-id.lowercase-doc-values"
                               :provider-id "provider-id.lowercase-doc-values"
                               :platform "platform-sn.lowercase-doc-values"
                               :instrument "instrument-sn.lowercase-doc-values"
                               :sensor "sensor-sn.lowercase-doc-values"
                               :project "project-refs.lowercase-doc-values"
                               :entry-title "entry-title.lowercase-doc-values"
                               :short-name "short-name.lowercase-doc-values"
                               :version "version-id.lowercase-doc-values"
                               :version-id "version-id.lowercase-doc-values"})
      default-mappings)))

(defn- keywords-in-condition
  "Returns a list of keywords if the condition contains a keyword condition or nil if not."
  [condition]
  (when (not= (type condition) cmr.common_app.services.search.query_model.NegatedCondition)
    (or (when (= :keyword (:field condition))
          (str/split (str/lower-case (:query-str condition)) #" "))
        ;; Call this function recursively on nested conditions, e.g., AND or OR conditions.
        (when-let [conds (:conditions condition)]
          (some #(keywords-in-condition %1) conds))
        ;; Call this function recursively for a single nested condition.
        (when-let [con (:condition condition)] (keywords-in-condition con)))))

(defn- keywords-in-query
  "Returns a list of keywords if the query contains a keyword condition or nil if not.
  Used to set sort and use function score for keyword queries."
  [query]
  (keywords-in-condition (:condition query)))

(defmethod q2e/query->elastic :collection
  [query]
  (let [boosts (:boosts query)
        {:keys [concept-type condition keywords]} (query-expense/order-conditions query)
        core-query (q2e/condition->elastic condition concept-type)]
    (if-let [keywords (keywords-in-query query)]
      ;; function_score query allows us to compute a custom relevance score for each document
      ;; matched by the primary query. The final document relevance is given by multiplying
      ;; a boosting term for each matching filter in a set of filters.
      {:function_score {:score_mode :multiply
                        :functions (k2e/keywords->boosted-elastic-filters keywords boosts)
                        :query {:filtered {:query (eq/match-all)
                                           :filter core-query}}}}
      (if boosts
        (errors/throw-service-errors :bad-request ["Relevance boosting is only supported for keyword queries"])
        {:filtered {:query (eq/match-all)
                    :filter core-query}}))))

(defmethod q2e/concept-type->sort-key-map :collection
  [_]
  {:entry-title :entry-title.lowercase
   :entry-id :entry-id.lowercase
   :provider :provider-id.lowercase
   :platform :platform-sn.lowercase
   :instrument :instrument-sn.lowercase
   :sensor :sensor-sn.lowercase
   :score :_score
   :revision-date :revision-date2})

(defmethod q2e/concept-type->sort-key-map :tag
  [_]
  {:namespace :namespace.lowercase
   :value :value.lowercase})

(defmethod q2e/concept-type->sort-key-map :granule
  [_]
  (let [default-mappings {:provider :provider-id.lowercase
                          :provider-id :provider-id.lowercase
                          :data-size :size
                          :entry-title :entry-title.lowercase
                          :short-name :short-name.lowercase
                          :version :version-id.lowercase
                          :granule-ur :granule-ur.lowercase2
                          :producer-granule-id :producer-gran-id.lowercase2
                          :readable-granule-name :readable-granule-name-sort2
                          :platform :platform-sn.lowercase
                          :instrument :instrument-sn.lowercase
                          :sensor :sensor-sn.lowercase
                          :project :project-refs.lowercase}]
    (if (use-doc-values-fields)
      (merge default-mappings {:provider :provider-id.lowercase-doc-values
                               :provider-id :provider-id.lowercase-doc-values
                               :size :size-doc-values
                               :data-size :size-doc-values
                               :platform :platform-sn.lowercase-doc-values
                               :instrument :instrument-sn.lowercase-doc-values
                               :sensor :sensor-sn.lowercase-doc-values
                               :project :project-refs.lowercase-doc-values
                               :start-date :start-date-doc-values
                               :end-date :end-date-doc-values
                               :revision-date :revision-date-doc-values
                               :entry-title :entry-title.lowercase-doc-values
                               :short-name :short-name.lowercase-doc-values
                               :version :version-id.lowercase-doc-values
                               :version-id :version-id.lowercase-doc-values
                               :day-night :day-night-doc-values
                               :cloud-cover :cloud-cover-doc-values})
      default-mappings)))

(defmethod q2e/concept-type->sub-sort-fields :collection
  [_]
  [{(q2e/query-field->elastic-field :concept-seq-id :collection) {:order "asc"}}
   {(q2e/query-field->elastic-field :revision-id :collection) {:order "desc"}}])

(defmethod q2e/concept-type->sub-sort-fields :granule
  [_]
  [{(q2e/query-field->elastic-field :concept-seq-id :granule) {:order "asc"}}])

;; Collections will default to the keyword sort if they have no sort specified and search by keywords
(defmethod q2e/query->sort-params :collection
  [query]
  (let [{:keys [concept-type sort-keys]} query
        ;; If the sort keys are given as parameters then keyword-sort will not be used.
        keyword-sort (when (keywords-in-query query)
                       [{:_score {:order :desc}}])
        specified-sort (q2e/sort-keys->elastic-sort concept-type sort-keys)
        default-sort (q2e/sort-keys->elastic-sort concept-type (q/default-sort-keys concept-type))]
    (concat (or specified-sort keyword-sort default-sort) (q2e/concept-type->sub-sort-fields concept-type))))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.CollectionQueryCondition
  (reduce-query-condition
    [condition context]
    (update-in condition [:condition] c2s/reduce-query-condition context)))
