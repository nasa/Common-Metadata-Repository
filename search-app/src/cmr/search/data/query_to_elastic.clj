(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojurewerkz.elastisch.query :as eq]
   [cmr.common-app.services.search.complex-to-simple :as c2s]
   [cmr.common-app.services.search.query-model :as q]
   [cmr.common-app.services.search.query-order-by-expense :as query-expense]
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.data.keywords-to-elastic :as k2e]
   [cmr.search.data.temporal-ranges-to-elastic :as temporal-to-elastic]
   [cmr.search.services.query-execution.temporal-conditions-results-feature :as temporal-conditions]
   [cmr.search.services.query-walkers.keywords-extractor :as keywords-extractor])
  (:require
   ;; require it so it will be available
   [cmr.search.data.query-order-by-expense]))

(defconfig use-doc-values-fields
  "Indicates whether search fields should use the doc-values fields or not. If false the field data
  cache fields will be used. This is a temporary configuration to toggle the feature off if there
  are issues."
  {:type Boolean
   :default true})

(defconfig sort-use-relevancy-score
  "Indicates whether in keyword search sorting if the community usage relevancy score should be used
  to sort collections. If true, consider the usage score as a tie-breaker when keyword relevancy
  scores or the same. If false, no tie-breaker is applied.
  This config is here to allow for the usage score to be turned off until elastic indexes are updated-since
  so keyword search will not be broken"
  {:type Boolean
   :default true})

(defconfig sort-use-temporal-relevancy
  "Indicates whether when searching using a temporal range if we should use temporal overlap
  relevancy to sort. If true, use the temporal overlap script in elastic. This config allows
  temporal overlap calculations to be turned off if needed for performance."
  {:type Boolean
   :default true})

(defn- doc-values-field-name
  "Returns the doc-values field-name for the given field."
  [field]
  (keyword (str (name field) "-doc-values")))

(def spatial-doc-values-field-mappings
  "The field mappings for the MBR and LR elasticsearch doc-values fields."
  (into {} (for [shape ["mbr" "lr"]
                 direction ["north" "south" "east" "west"]
                 :let [field (keyword (str shape "-" direction))]]
             [field (doc-values-field-name field)])))

(defmethod q2e/concept-type->field-mappings :collection
  [_]
  (let [default-mappings {:provider :provider-id
                          :version :version-id
                          :project :project-sn2
                          :project-sn :project-sn2
                          :project-h :project-sn.humanized2
                          :updated-since :revision-date2
                          :two-d-coordinate-system-name :two-d-coord-name
                          :platform :platform-sn
                          :platform-h :platform-sn.humanized2
                          :instrument :instrument-sn
                          :instrument-h :instrument-sn.humanized2
                          :sensor :sensor-sn
                          :data-center-h :organization.humanized2
                          :processing-level-id-h :processing-level-id.humanized2
                          :revision-date :revision-date2}]
    (if (use-doc-values-fields)
      (merge default-mappings spatial-doc-values-field-mappings)
      default-mappings)))

(def query-field->granule-doc-values-fields-map
  "Defines mappings for any query-fields to Elasticsearch doc-values field names. Note that this
  does not include lowercase field mappings for doc-values fields."
  (into spatial-doc-values-field-mappings
        (for [field [:provider-id :concept-seq-id :collection-concept-id
                     :collection-concept-seq-id :size :start-date :end-date :revision-date
                     :day-night :cloud-cover :orbit-start-clat :orbit-end-clat
                     :orbit-asc-crossing-lon :access-value :start-coordinate-1
                     :end-coordinate-1 :start-coordinate-2 :end-coordinate-2]]
          [field (doc-values-field-name field)])))

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
              :updated-since :revision-date-doc-values}
             query-field->granule-doc-values-fields-map)
      default-mappings)))

(defmethod q2e/concept-type->field-mappings :tag
  [_]
  {:tag-key :tag-key.lowercase
   :originator-id :originator-id.lowercase})

(defmethod q2e/elastic-field->query-field-mappings :collection
  [_]
  {:project-sn2 :project-sn
   :project-sn.humanized2 :project-h
   :two-d-coord-name :two-d-coordinate-system-name
   :platform-sn :platform
   :platform-sn.humanized2 :platform-h
   :instrument-sn :instrument
   :instrument-sn.humanized2 :instrument-h
   :sensor-sn :sensor
   :organization.humanized2 :data-center-h
   :processing-level-id.humanized2 :processing-level-id-h
   :revision-date2 :revision-date})

(defmethod q2e/elastic-field->query-field-mappings :granule
  [_]
  (merge {:platform-sn :platform
          :instrument-sn :instrument
          :sensor-sn :sensor
          :project-refs :project}
         (set/map-invert query-field->granule-doc-values-fields-map)))

(defmethod q2e/elastic-field->query-field-mappings :tag
  [_]
  {:tag-key.lowercase :tag-key
   :originator-id.lowercase :originator-id})

(defmethod q2e/field->lowercase-field-mappings :collection
  [_]
  {:provider "provider-id.lowercase"
   :version "version-id.lowercase"
   :project "project-sn2.lowercase"
   :two-d-coordinate-system-name "two-d-coord-name.lowercase"
   :platform "platform-sn.lowercase"
   :instrument "instrument-sn.lowercase"
   :sensor "sensor-sn.lowercase"})

(defn- doc-values-lowercase-field-name
  "Returns the doc-values field-name for the given field."
  [field]
  (str (name field) "-lowercase-doc-values"))

(def query-field->lowercase-granule-doc-values-fields-map
  "Defines mappings from query-fields to Elasticsearch lowercase-doc-values fields."
  (into {:provider "provider-id.lowercase-doc-values"
         :platform "platform-sn.lowercase-doc-values"
         :instrument "instrument-sn.lowercase-doc-values"
         :sensor "sensor-sn.lowercase-doc-values"
         :project "project-refs.lowercase-doc-values"
         :version "version-id.lowercase-doc-values"}
        (for [field [:provider-id :entry-title :short-name :version-id]]
          [field (doc-values-lowercase-field-name field)])))

(defmethod q2e/field->lowercase-field-mappings :granule
  [_]
  (let [default-mappings
        {:granule-ur "granule-ur.lowercase2"
         :producer-gran-id "producer-gran-id.lowercase2"
         :producer-granule-id "producer-gran-id.lowercase2"
         :project "project-refs.lowercase"
         :version "version-id.lowercase"
         :provider "provider-id.lowercase"
         :platform "platform-sn.lowercase"
         :instrument "instrument-sn.lowercase"
         :sensor "sensor-sn.lowercase"}]
    (if (use-doc-values-fields)
      (merge default-mappings query-field->lowercase-granule-doc-values-fields-map)
      default-mappings)))

(defn- keywords-in-query
  "Returns a list of keywords if the query contains a keyword condition or nil if not.
  Used to set sort and use function score for keyword queries."
  [query]
  (keywords-extractor/extract-keywords query))

(defn- ^:pure get-max-kw-number-allowed
  "Returns the max number of keyword string with wildcards allowed, given the max length of 
   the keyword string with wildcards"
  [length]
  (cond
    (> length 241) 0
    (and (> length 121) (<= length 241)) 10
    (and (> length 61) (<= length 121)) 16
    (and (> length 41) (<= length 61)) 22
    (and (> length 21) (<= length 41)) 26
    (and (> length 7) (<= length 21)) 36
    (and (> length 5) (<= length 7)) 66
    (= length 5) 83
    (and (> length 0) (<= length 4)) 118))

(def KEYWORD_WILDCARD_NUMBER_MAX
  30)

(defn- ^:pure validate-keyword-wildcards
  "Validates if the number of keyword strings with wildcards exceeds the max number allowed
   for the max length of the keyword strings."
  [keywords]
  (when-let [kw-with-wild-cards (get (group-by #(.contains % "*") keywords) true)]  
    (let [max-kw-length (apply max (map count kw-with-wild-cards))
          kw-number (count kw-with-wild-cards)
          max-kw-number-allowed (get-max-kw-number-allowed max-kw-length)
          msg (str "Max number of keyword strings with wildcard allowed is: " max-kw-number-allowed
                   " given the max length of the keyword strings being: " max-kw-length)]
      (when (or (> kw-number KEYWORD_WILDCARD_NUMBER_MAX) (> kw-number max-kw-number-allowed))
        (let [msg (cond 
                    (> kw-number KEYWORD_WILDCARD_NUMBER_MAX) "Max # of keyword strings with * allowed is 30"
                    :else msg)]  
          (errors/throw-service-errors :bad-request (vector msg)))))))
 
(defmethod q2e/query->elastic :collection
  [query]
  (let [boosts (:boosts query)
        {:keys [concept-type condition]} (query-expense/order-conditions query)
        core-query (q2e/condition->elastic condition concept-type)]
    (if-let [keywords (keywords-in-query query)]
      (let [_ (validate-keyword-wildcards keywords)] 
        ;; Forces score to be returned even if not sorting by score.
        {:track_scores true
         ;; function_score query allows us to compute a custom relevance score for each document
         ;; matched by the primary query. The final document relevance is given by multiplying
         ;; a boosting term for each matching filter in a set of filters.
         :query {:function_score {:score_mode :multiply
                                  :functions (k2e/keywords->boosted-elastic-filters keywords boosts)
                                  :query {:filtered {:query (eq/match-all)
                                                     :filter core-query}}}}}) 
      (if boosts
        (errors/throw-service-errors :bad-request ["Relevance boosting is only supported for keyword queries"])
        {:query {:filtered {:query (eq/match-all)
                              :filter core-query}}}))))

(defmethod q2e/concept-type->sort-key-map :collection
  [_]
  {:short-name :short-name.lowercase
   :version-id :version-id.lowercase
   :entry-title :entry-title.lowercase
   :entry-id :entry-id.lowercase
   :provider :provider-id.lowercase
   :platform :platform-sn.lowercase
   :instrument :instrument-sn.lowercase
   :sensor :sensor-sn.lowercase
   :score :_score
   :revision-date :revision-date2
   :usage-score :usage-relevancy-score})

(defmethod q2e/concept-type->sort-key-map :tag
  [_]
  {:tag-key :tag-key.lowercase})

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

(def collection-all-revision-sub-sort-fields
  "This defines the sub sort fields for an all revisions search."
  [{(q2e/query-field->elastic-field :concept-seq-id :collection) {:order "asc"}}
   {(q2e/query-field->elastic-field :revision-id :collection) {:order "desc"}}])

(def collection-latest-sub-sort-fields
  "This defines the sub sort fields for a latest revision collection search. Short name and version id
   are included for better relevancy with search results where all the other sort keys were identical."
  [{(q2e/query-field->elastic-field :short-name :collection) {:order "asc"}}
   {(q2e/query-field->elastic-field :version-id :collection) {:order "desc"}}
   {(q2e/query-field->elastic-field :concept-seq-id :collection) {:order "asc"}}
   {(q2e/query-field->elastic-field :revision-id :collection) {:order "desc"}}])

(defn- score-sort-order
  "Determine the keyword sort order based on the sort-use-relevancy-score config and the presence
   of temporal range parameters in the query"
  [query]
  (let [use-keyword-sort? (keywords-extractor/contains-keyword-condition? query)
        use-temporal-sort? (and (temporal-conditions/contains-temporal-conditions? query)
                                (sort-use-temporal-relevancy))]
    (seq
     (concat
       (when use-keyword-sort?
         [{:_score {:order :desc}}])
       (when use-temporal-sort?
         [{:_script (temporal-to-elastic/temporal-overlap-sort-script query)}])
       ;; We only include this if one of the others is present
       (when (and (or use-temporal-sort? use-keyword-sort?)
                  (sort-use-relevancy-score))
         [{:usage-relevancy-score {:order :desc :missing 0}}])))))

(defn- temporal-sort-order
  "If there are temporal ranges in the query and temporal relevancy sorting is turned on,
  define the temporal sort order based on the sort-usage-relevancy-score. If sort-use-temporal-relevancy
  is turned off, return nil so the default sorting gets used."
  [query]
  (when (and (temporal-conditions/contains-temporal-conditions? query)
             (sort-use-temporal-relevancy))
    (if (sort-use-relevancy-score)
      [{:_script (temporal-to-elastic/temporal-overlap-sort-script query)}
       {:usage-relevancy-score {:order :desc :missing 0}}]
      [{:_script (temporal-to-elastic/temporal-overlap-sort-script query)}])))

(defmethod q2e/concept-type->sub-sort-fields :granule
  [_]
  [{(q2e/query-field->elastic-field :concept-seq-id :granule) {:order "asc"}}])

;; Collections will default to the keyword sort if they have no sort specified and search by keywords
(defmethod q2e/query->sort-params :collection
  [query]
  (let [{:keys [concept-type sort-keys]} query
        ;; If the sort keys are given as parameters then keyword-sort will not be used.
        score-sort-order (score-sort-order query)
        specified-sort (q2e/sort-keys->elastic-sort concept-type sort-keys)
        default-sort (q2e/sort-keys->elastic-sort concept-type (q/default-sort-keys concept-type))
        sub-sort-fields (if (:all-revisions? query)
                          collection-all-revision-sub-sort-fields
                          collection-latest-sub-sort-fields)
        ;; We want the specified sort then to sub-sort by the score.
        ;; Only if neither is present should it then go to the default sort.
        specified-score-combined (seq (concat specified-sort score-sort-order))]
    (concat (or specified-score-combined default-sort) sub-sort-fields)))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.CollectionQueryCondition
  (reduce-query-condition
    [condition context]
    (update-in condition [:condition] c2s/reduce-query-condition context)))
