(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require
   [clj-time.coerce :as time-coerce]
   [clj-time.core :as time]
   [clojure.java.io :as io]
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

(defconfig sort-bin-keyword-scores
  "When sorting by keyword score, set to true if we want to bin the keyword scores
  i.e. round to the nearest keyword-score-bin-size"
  {:type Boolean
   :default true})

(defconfig keyword-score-bin-size
  "When sort-bin-keyword-scores is true, the keyword score should
  be rounded to the nearest keyword-score-bin-size"
  {:type Double
   :default 0.2})

(def keyword-score-bin-script
  "Groovy script used by elastic to bin the keyword score based on bin-size"
  (slurp (io/resource "bin_keyword_score.groovy")))

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
                          :revision-date :revision-date2
                          :variable-name :variable-names
                          :variable-native-id :variable-native-ids
                          :measurement :measurements
                          :author :authors}]
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

(defmethod q2e/concept-type->field-mappings :variable
  [_]
  {:provider :provider-id
   :name :variable-name})

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
   :revision-date2 :revision-date
   :variable-names :variable-name
   :variable-native-ids :variable-native-id
   :measurements :measurement
   :authors :author})

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
   :sensor "sensor-sn.lowercase"
   :variable-name "variable-names.lowercase"
   :variable-native-id "variable-native-ids.lowercase"
   :measurement "measurements.lowercase"
   :author "authors.lowercase"})

(defmethod q2e/field->lowercase-field-mappings :variable
  [_]
  {:provider "provider-id.lowercase"
   :name "variable-name.lowercase"})

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

(defn- get-max-kw-number-allowed
  "Returns the max number of keyword string with wildcards allowed by elastic query,
   given the max length of the keyword string with wildcards."
  [length]
  (cond
    (> length 241) 0
    (and (> length 182) (<= length 241)) 5
    (and (> length 110) (<= length 182)) 10
    (and (> length 74) (<= length 110)) 13
    (and (> length 40) (<= length 74)) 16
    (and (> length 16) (<= length 40)) 22
    (and (> length 7) (<= length 16)) 36
    (and (> length 5) (<= length 7)) 60
    (= length 5) 75
    (and (> length 0) (<= length 4)) 115))

(def KEYWORD_WILDCARD_NUMBER_MAX
  "Maximum number of keyword strings with wildcards allowed by the CMR.
   This is the absolute maximum number which can not be exceeded.
   It takes precedence over the maximum number from the get-max-kw-number-allowed function."
  30)

(defn- ^:pure get-validate-keyword-wildcards-msg
  "Validates if the number of keyword strings with wildcards exceeds the max number allowed
   for the max length of the keyword strings. Returns validation message if it fails."
  [keywords]
  (when-let [kw-with-wild-cards (get (group-by #(or (.contains % "?") (.contains % "*")) keywords) true)]
    (let [max-kw-length (apply max (map count kw-with-wild-cards))
          kw-number (count kw-with-wild-cards)
          max-kw-number-allowed (get-max-kw-number-allowed max-kw-length)
          over-abs-max-msg (str "Max number of keywords with wildcard allowed is " KEYWORD_WILDCARD_NUMBER_MAX)
          over-rel-max-msg (str "The CMR permits a maximum of " max-kw-number-allowed
                                " keywords with wildcards in a search,"
                                " given the max length of the keyword being " max-kw-length
                                ". Your query contains " kw-number " keywords with wildcards")]
      (when (or (> kw-number KEYWORD_WILDCARD_NUMBER_MAX) (> kw-number max-kw-number-allowed))
        (cond
          (> kw-number KEYWORD_WILDCARD_NUMBER_MAX) over-abs-max-msg
          :else over-rel-max-msg)))))

(defn- validate-keyword-wildcards
  "Validates keyword with wildcards. If validation fails, throw bad-request error"
  [keywords]
  (when-let [msg (get-validate-keyword-wildcards-msg keywords)]
    (errors/throw-service-errors :bad-request (vector msg))))

(defmethod q2e/query->elastic :collection
  [query]
  (let [boosts (:boosts query)
        {:keys [concept-type condition]} (query-expense/order-conditions query)
        core-query (q2e/condition->elastic condition concept-type)]
    (let [keywords (keywords-in-query query)]
      (if-let [all-keywords (seq (concat (:keywords keywords) (:field-keywords keywords)))]
       (do
        (validate-keyword-wildcards all-keywords)
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
                             :filter core-query}}})))))

(defmethod q2e/concept-type->sort-key-map :collection
  [_]
  {:short-name :short-name.lowercase
   :version-id :parsed-version-id.lowercase ; Use parsed for sorting
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

(defmethod q2e/concept-type->sort-key-map :variable
  [_]
  {:variable-name :variable-name.lowercase
   :name :variable-name.lowercase
   :long-name :measurement.lowercase
   :provider :provider-id.lowercase})

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

(defn- all-revision-sub-sort-fields
  "This defines the sub sort fields of an all revisions search for the given concept type."
  [concept-type]
  [{(q2e/query-field->elastic-field :concept-seq-id concept-type) {:order "asc"}}
   {(q2e/query-field->elastic-field :revision-id concept-type) {:order "desc"}}])

(def collection-latest-sub-sort-fields
  "This defines the sub sort fields for a latest revision collection search. Short name and version id
   are included for better relevancy with search results where all the other sort keys were identical."
  [{(q2e/query-field->elastic-field :short-name :collection) {:order "asc"}}
   {(q2e/query-field->elastic-field :parsed-version-id.lowercase :collection) {:order "desc"}}
   {(q2e/query-field->elastic-field :concept-seq-id :collection) {:order "asc"}}
   {(q2e/query-field->elastic-field :revision-id :collection) {:order "desc"}}])

(def variable-latest-sub-sort-fields
  "This defines the sub sort fields for a latest revision variable search."
  [{(q2e/query-field->elastic-field :name :variable) {:order "asc"}}
   {(q2e/query-field->elastic-field :provider :variable) {:order "asc"}}])

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
         (if (sort-bin-keyword-scores)
           [{:_script {:params {:binSize (keyword-score-bin-size)}
                       :script keyword-score-bin-script
                       :type :number
                       :order :desc}}]
           [{:_score {:order :desc}}]))
       (when use-temporal-sort?
         [{:_script (temporal-to-elastic/temporal-overlap-sort-script query)}])
       ;; We only include this if one of the others is present
       (when (and (or use-temporal-sort? use-keyword-sort?)
                  (sort-use-relevancy-score))
         [{:usage-relevancy-score {:order :desc :missing 0}}])
       ;; If end-date is nil, collection is ongoing so use today so ongoing
       ;; collections will be at the top
       (when use-keyword-sort?
         [{:end-date {:order :desc
                      :missing (time-coerce/to-long (time/now))}}
          {:processing-level-id.lowercase.humanized {:order :desc}}])))))

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
                          (all-revision-sub-sort-fields :collection)
                          collection-latest-sub-sort-fields)
        ;; We want the specified sort then to sub-sort by the score.
        ;; Only if neither is present should it then go to the default sort.
        specified-score-combined (seq (concat specified-sort score-sort-order))]
    (concat (or specified-score-combined default-sort) sub-sort-fields)))

(defmethod q2e/query->sort-params :variable
  [query]
  (let [{:keys [concept-type sort-keys]} query
        specified-sort (q2e/sort-keys->elastic-sort concept-type sort-keys)
        default-sort (q2e/sort-keys->elastic-sort concept-type (q/default-sort-keys concept-type))
        sub-sort-fields (if (:all-revisions? query)
                          (all-revision-sub-sort-fields :variable)
                          variable-latest-sub-sort-fields)]
    (concat (or specified-sort default-sort) sub-sort-fields)))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.CollectionQueryCondition
  (reduce-query-condition
    [condition context]
    (update-in condition [:condition] c2s/reduce-query-condition context)))
