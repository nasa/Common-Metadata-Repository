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
   [cmr.common.concepts :as concepts]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.services.errors :as errors]
   [cmr.search.data.elastic-relevancy-scoring :as elastic-relevancy-scoring]
   [cmr.search.data.keywords-to-elastic :as k2e]
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

;; These mappings are for renames only, same name keys handled by default
;; create a matching value in q2e/elastic-field->query-field-mappings
(defmethod q2e/concept-type->field-mappings :collection
  [_]
  (let [default-mappings {:author :authors
                          :concept-seq-id :concept-seq-id-long
                          :consortium :consortiums
                          :data-center-h :organization-humanized
                          :doi :doi-stored
                          :granule-end-date :granule-end-date-stored
                          :granule-start-date :granule-start-date-stored
                          :granule-data-format-h :granule-data-format-humanized
                          :horizontal-data-resolution-range :horizontal-data-resolutions
                          :instrument :instrument-sn
                          :instrument-h :instrument-sn-humanized
                          :measurement :measurements
                          :platform :platform-sn
                          :platform-h :platform-sn-humanized
                          :platforms :platforms2 ;the old platforms has been depricated
                          :processing-level-id-h :processing-level-id-humanized
                          :project :project-sn
                          :project-h :project-sn-humanized
                          :provider :provider-id
                          :sensor :sensor-sn
                          :service-concept-id :service-concept-ids
                          :service-name :service-names
                          :service-type :service-types-lowercase
                          :tool-concept-id :tool-concept-ids
                          :tool-name :tool-names
                          :tool-type :tool-types-lowercase
                          :two-d-coordinate-system-name :two-d-coord-name
                          :updated-since :revision-date
                          :usage-score :usage-relevancy-score
                          :variable-concept-id :variable-concept-ids
                          :variable-name :variable-names
                          :variable-native-id :variable-native-ids
                          :version :version-id
                          :keyword :keyword2
                          :keyword-phrase :keyword2}]
    (if (use-doc-values-fields)
      (merge default-mappings spatial-doc-values-field-mappings)
      default-mappings)))

(def query-field->granule-doc-values-fields-map
  "Defines mappings for any query-fields to Elasticsearch doc-values field names. Note that this
  does not include lowercase field mappings for doc-values fields."
  (into spatial-doc-values-field-mappings
        (for [field [:access-value
                     :concept-seq-id-long
                     :collection-concept-id
                     :collection-concept-seq-id-long
                     :provider-id
                     :size
                     :start-date
                     :end-date
                     :revision-date
                     :day-night
                     :cloud-cover
                     :orbit-start-clat
                     :orbit-end-clat
                     :orbit-asc-crossing-lon
                     :start-coordinate-1
                     :end-coordinate-1
                     :start-coordinate-2
                     :end-coordinate-2]]
          [field (doc-values-field-name field)])))

(defmethod q2e/concept-type->field-mappings :granule
  [_]
  (let [default-mappings {:provider :provider-id
                          :concept-seq-id :concept-seq-id-long
                          :collection-concept-seq-id :collection-concept-seq-id-long
                          :native-id :native-id-stored
                          :revision-date :revision-date-stored-doc-values
                          :updated-since :revision-date-stored-doc-values
                          :producer-granule-id :producer-gran-id
                          :platform :platform-sn
                          :instrument :instrument-sn
                          :sensor :sensor-sn
                          :project :project-refs}]
    (if (use-doc-values-fields)
      (merge default-mappings
             {:provider :provider-id-doc-values
              :updated-since :revision-date-stored-doc-values}
             query-field->granule-doc-values-fields-map
             {:revision-date :revision-date-stored-doc-values})
      default-mappings)))

(defmethod q2e/concept-type->field-mappings :tag
  [_]
  {:tag-key :tag-key-lowercase
   :originator-id :originator-id-lowercase})

(defmethod q2e/concept-type->field-mappings :variable
  [_]
  {:provider :provider-id
   :concept-seq-id :concept-seq-id-long
   :name :variable-name})

(defmethod q2e/concept-type->field-mappings :service
  [_]
  {:provider :provider-id
   :name :service-name
   :type :service-type-lowercase})

(defmethod q2e/concept-type->field-mappings :tool
  [_]
  {:provider :provider-id
   :name :tool-name
   :type :tool-type-lowercase})

(defmethod q2e/concept-type->field-mappings :subscription
  [_]
  {:provider :provider-id
   :name :subscription-name
   :type :subscription-type})

;; TODO Generic work - this would be nice to put into a configuration file.
(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (defmethod q2e/concept-type->field-mappings concept-type
    [_]
    {:provider :provider-id}))

(defmethod q2e/elastic-field->query-field-mappings :autocomplete
  [_]
  {:value :value
   :type :type})

;; These mappings are for renames only, same name keys handled by default
;; create a matching value in q2e/concept-type->field-mappings
(defmethod q2e/elastic-field->query-field-mappings :collection
  [_]
  {:authors :author
   :consortiums :consortium
   :doi-stored :doi
   :granule-end-date-stored :granule-end-date
   :granule-start-date-stored :granule-start-date
   :granule-data-format-humanized :granule-data-format-h
   :horizontal-data-resolutions :horizontal-data-resolution-range
   :instrument-sn :instrument
   :instrument-sn-humanized :instrument-h
   :measurements :measurement
   :organization-humanized :data-center-h
   :platform-sn :platform
   :platform-sn-humanized :platform-h
   :platforms2 :platforms
   :processing-level-id-humanized :processing-level-id-h
   :project-sn-humanized :project-h
   :sensor-sn :sensor
   :service-concept-ids :service-concept-id
   :service-names :service-name
   :service-types-lowercase :service-type
   :tool-concept-ids :tool-concept-id
   :tool-names :tool-name
   :tool-types-lowercase :tool-type
   :two-d-coord-name :two-d-coordinate-system-name
   :variable-concept-ids :variable-concept-id
   :variable-names :variable-name
   :variable-native-ids :variable-native-id})

(defmethod q2e/elastic-field->query-field-mappings :granule
  [_]
  (merge {:platform-sn :platform
          :instrument-sn :instrument
          :sensor-sn :sensor
          :project-refs :project
          :native-id-stored :native-id
          :revision-date-stored-doc-values :revision-date}
         (set/map-invert query-field->granule-doc-values-fields-map)))

(defmethod q2e/elastic-field->query-field-mappings :tag
  [_]
  {:tag-key-lowercase :tag-key
   :originator-id-lowercase :originator-id})

(defmethod q2e/field->lowercase-field-mappings :collection
  [_]
  {:provider "provider-id-lowercase"
   :version "version-id-lowercase"
   :project "project-sn-lowercase"
   :two-d-coordinate-system-name "two-d-coord-name-lowercase"
   :platform "platform-sn-lowercase"
   :instrument "instrument-sn-lowercase"
   :sensor "sensor-sn-lowercase"
   :variable-name "variable-names-lowercase"
   :variable-native-id "variable-native-ids-lowercase"
   :measurement "measurements-lowercase"
   :author "authors-lowercase"
   :latency "latency-lowercase"
   :consortium "consortiums-lowercase"
   :service-name "service-names-lowercase"
   :service-type "service-types-lowercase"
   :tool-name "tool-names-lowercase"
   :tool-type "tool-types-lowercase"})

(defmethod q2e/field->lowercase-field-mappings :variable
  [_]
  {:provider "provider-id-lowercase"
   :name "variable-name-lowercase"})

(defmethod q2e/field->lowercase-field-mappings :service
  [_]
  {:provider "provider-id-lowercase"
   :name "service-name-lowercase"
   :type "service-type-lowercase"})

(defmethod q2e/field->lowercase-field-mappings :tool
  [_]
  {:provider "provider-id-lowercase"
   :name "tool-name-lowercase"
   :type "tool-type-lowercase"})

(defmethod q2e/field->lowercase-field-mappings :subscription
  [_]
  {:provider "provider-id-lowercase"
   :name "subscription-name-lowercase"
   :type "subscription-type-lowercase"})

;; TODO Generic work - this would be nice to put into a configuration file.
(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (defmethod q2e/field->lowercase-field-mappings concept-type
    [_]
    {:provider :provider-id-lowercase
     :native-id :native-id-lowercase}))

(defn- doc-values-lowercase-field-name
  "Returns the doc-values field-name for the given field."
  [field]
  (str (name field) "-lowercase-doc-values"))

(def query-field->lowercase-granule-doc-values-fields-map
  "Defines mappings from query-fields to Elasticsearch lowercase-doc-values fields."
  (into {:provider "provider-id-lowercase-doc-values"
         :platform "platform-sn-lowercase-doc-values"
         :instrument "instrument-sn-lowercase-doc-values"
         :sensor "sensor-sn-lowercase-doc-values"
         :project "project-refs-lowercase-doc-values"
         :version "version-id-lowercase-doc-values"}
        (for [field [:provider-id :entry-title :short-name :version-id]]
          [field (doc-values-lowercase-field-name field)])))

(defmethod q2e/field->lowercase-field-mappings :granule
  [_]
  (let [default-mappings
        {:granule-ur "granule-ur-lowercase"
         :producer-gran-id "producer-gran-id-lowercase"
         :producer-granule-id "producer-gran-id-lowercase"
         :project "project-refs-lowercase"
         :version "version-id-lowercase"
         :provider "provider-id-lowercase"
         :platform "platform-sn-lowercase"
         :instrument "instrument-sn-lowercase"
         :sensor "sensor-sn-lowercase"}]
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

(defn- remove-quotes-from-keyword-query-string
  "When keyword query string is double quoted string in a condition, we want to remove the quotes,
  add wildcard and change the field from :keyword to :keyword-phrase. Both :keyword and :keyword-phrase
  search against the same index, but through different query types."
  [condition]
  (let [query-str (:query-str condition)
        trimmed-query-str (when query-str
                            (str/trim query-str))
        ;;literal double quotes are passed in as \\\"
        query-str-without-literal-quotes (when query-str
                                           (str/replace trimmed-query-str #"\\\"" ""))
        ;;\" is reserved for phrase boundaries.
        count-of-double-quotes (if query-str
                                 (count (re-seq #"\"" query-str-without-literal-quotes))
                                 0)]

    ;;We want to reject the query if it's a mix of keyword and keyword-phrase, or
    ;;if it's a multiple keyword-phrase search, which are not supported.
    ;;We also want to reject it if the keyword phrase contains only one \".
    (when (and (= :keyword (:field condition))
               (or (> count-of-double-quotes 2) ;;multi keyword-phrase case
                   ;;mix of keyword and keyword-phrase case, including the case when one \" is missing.
                   (and (str/includes? query-str-without-literal-quotes "\"")
                        (not (and (str/starts-with? trimmed-query-str "\"")
                                  (str/ends-with? trimmed-query-str "\""))))))
      (errors/throw-service-errors
       :bad-request
       [(str "keyword phrase mixed with keyword, or another keyword-phrase are not supported. "
             "keyword phrase has to be enclosed by two escaped double quotes.")]))

    (if (and query-str
             (= :keyword (:field condition))
             (str/starts-with? trimmed-query-str "\"")
             (str/ends-with? trimmed-query-str "\""))
      (assoc condition :query-str (-> trimmed-query-str
                                      (str/replace #"^\"" "* ")
                                      (str/replace #"\"$" " *")
                                      (str/replace #"\\\"" "\""))
                       :field :keyword-phrase)
      (if (and query-str
               (str/includes? trimmed-query-str "\\\""))
        (assoc condition :query-str (str/replace trimmed-query-str #"\\\"" "\""))
        condition))))

(defmethod q2e/query->elastic :collection
  [query]
  (let [unquoted-query (update-in query [:condition :conditions]
                                  #(map remove-quotes-from-keyword-query-string %))
        boosts (:boosts unquoted-query)
        {:keys [concept-type condition]} (query-expense/order-conditions unquoted-query)
        core-query (q2e/condition->elastic condition concept-type)]
    ;;Need the original query here because when the query-str is quoted, it's processed differently.
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
                                  :query {:bool {:must (eq/match-all)
                                                 :filter core-query}}}}})
       (if boosts
         (errors/throw-service-errors :bad-request ["Relevance boosting is only supported for keyword queries"])
         {:query {:bool {:must (eq/match-all)
                         :filter core-query}}})))))

;; this only needs to map overrides, defaults to one-to-one mappings
(defmethod q2e/concept-type->sort-key-map :collection
  [_]
  {:short-name :short-name-lowercase
   :version-id :parsed-version-id-lowercase ; Use parsed for sorting
   :entry-title :entry-title-lowercase
   :entry-id :entry-id-lowercase
   :provider :provider-id-lowercase
   :platform :platform-sn-lowercase
   :platforms :platforms2 ; the old "platforms" has been depricated
   :instrument :instrument-sn-lowercase
   :sensor :sensor-sn-lowercase
   :score :_score
   :usage-score :usage-relevancy-score})

(defmethod q2e/concept-type->sort-key-map :tag
  [_]
  {:tag-key :tag-key-lowercase})

(defmethod q2e/concept-type->sort-key-map :variable
  [_]
  {:variable-name :variable-name-lowercase
   :name :variable-name-lowercase
   :long-name :measurement-lowercase
   :full-path :full-path-lowercase
   :provider :provider-id-lowercase})

(defmethod q2e/concept-type->sort-key-map :service
  [_]
  {:service-name :service-name-lowercase
   :name :service-name-lowercase
   :type :service-type-lowercase
   :long-name :long-name-lowercase
   :provider :provider-id-lowercase})

(defmethod q2e/concept-type->sort-key-map :tool
  [_]
  {:tool-name :tool-name-lowercase
   :name :tool-name-lowercase
   :type :tool-type-lowercase
   :long-name :long-name-lowercase
   :provider :provider-id-lowercase})

(doseq [doseq-concept-type (concepts/get-generic-concept-types-array)]
  (defmethod q2e/concept-type->sort-key-map doseq-concept-type
    [_]
    {:name :name-lowercase
     :provider :provider-id-lowercase}))

(defmethod q2e/concept-type->sort-key-map :subscription
  [_]
  {:subscription-name :subscription-name-lowercase
   :name :subscription-name-lowercase
   :collection-concept-id :collection-concept-id-lowercase
   :provider :provider-id-lowercase})

(defmethod q2e/concept-type->sort-key-map :granule
  [_]
  (let [default-mappings {:provider :provider-id-lowercase
                          :provider-id :provider-id-lowercase
                          :data-size :size
                          :entry-title :entry-title-lowercase
                          :short-name :short-name-lowercase
                          :version :version-id-lowercase
                          :granule-ur :granule-ur-lowercase
                          :producer-granule-id :producer-gran-id-lowercase
                          :readable-granule-name :readable-granule-name-sort
                          :platform :platform-sn-lowercase
                          :instrument :instrument-sn-lowercase
                          :sensor :sensor-sn-lowercase
                          :project :project-refs-lowercase}]
    (if (use-doc-values-fields)
      (merge default-mappings {:provider :provider-id-lowercase-doc-values
                               :provider-id :provider-id-lowercase-doc-values
                               :size :size-doc-values
                               :data-size :size-doc-values
                               :platform :platform-sn-lowercase-doc-values
                               :instrument :instrument-sn-lowercase-doc-values
                               :sensor :sensor-sn-lowercase-doc-values
                               :project :project-refs-lowercase-doc-values
                               :start-date :start-date-doc-values
                               :end-date :end-date-doc-values
                               :revision-date :revision-date-stored-doc-values
                               :entry-title :entry-title-lowercase-doc-values
                               :short-name :short-name-lowercase-doc-values
                               :version :version-id-lowercase-doc-values
                               :version-id :version-id-lowercase-doc-values
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
   {(q2e/query-field->elastic-field :parsed-version-id-lowercase :collection) {:order "desc"}}
   {(q2e/query-field->elastic-field :concept-seq-id :collection) {:order "asc"}}
   {(q2e/query-field->elastic-field :revision-id :collection) {:order "desc"}}])

(def variable-latest-sub-sort-fields
  "This defines the sub sort fields for a latest revision variable search."
  [{(q2e/query-field->elastic-field :name :variable) {:order "asc"}}
   {(q2e/query-field->elastic-field :provider :variable) {:order "asc"}}])

(def service-all-revision-sub-sort-fields
  "Defines the sub sort fields for an all revisions service search."
  [{(q2e/query-field->elastic-field :concept-id :service) {:order "asc"}}
   {(q2e/query-field->elastic-field :revision-id :service) {:order "desc"}}])

(def service-latest-sub-sort-fields
  "This defines the sub sort fields for a latest revision service search."
  [{(q2e/query-field->elastic-field :name :service) {:order "asc"}}
   {(q2e/query-field->elastic-field :provider :service) {:order "asc"}}])

(def tool-all-revision-sub-sort-fields
  "Defines the sub sort fields for an all revisions tool search."
  [{(q2e/query-field->elastic-field :concept-id :tool) {:order "asc"}}
   {(q2e/query-field->elastic-field :revision-id :tool) {:order "desc"}}])

(def tool-latest-sub-sort-fields
  "This defines the sub sort fields for a latest revision tool search."
  [{(q2e/query-field->elastic-field :name :tool) {:order "asc"}}
   {(q2e/query-field->elastic-field :provider :tool) {:order "asc"}}])

(def subscription-all-revision-sub-sort-fields
  "Defines the sub sort fields for an all revisions subscription search."
  [{(q2e/query-field->elastic-field :concept-id :subscription) {:order "asc"}}
   {(q2e/query-field->elastic-field :revision-id :subscription) {:order "desc"}}])

(def subscription-latest-sub-sort-fields
  "This defines the sub sort fields for a latest revision subscription search."
  [{(q2e/query-field->elastic-field :name :subscription) {:order "asc"}}
   {(q2e/query-field->elastic-field :provider :subscription) {:order "asc"}}])

(defmethod q2e/concept-type->sub-sort-fields :granule
  [_]
  [{(q2e/query-field->elastic-field :concept-seq-id :granule) {:order "asc"}}])

;; Collections will default to the keyword sort if they have no sort specified and search by keywords
(defmethod q2e/query->sort-params :collection
  [query]
  (let [{:keys [concept-type sort-keys]} query
        ;; If the sort keys are given as parameters then keyword-sort will not be used.
        score-sort-order (elastic-relevancy-scoring/score-sort-order query)
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

(defmethod q2e/query->sort-params :service
  [query]
  (let [{:keys [concept-type sort-keys]} query
        specified-sort (q2e/sort-keys->elastic-sort concept-type sort-keys)
        default-sort (q2e/sort-keys->elastic-sort concept-type (q/default-sort-keys concept-type))
        sub-sort-fields (if (:all-revisions? query)
                          service-all-revision-sub-sort-fields
                          service-latest-sub-sort-fields)]
    (concat (or specified-sort default-sort) sub-sort-fields)))

(defmethod q2e/query->sort-params :tool
  [query]
  (let [{:keys [concept-type sort-keys]} query
        specified-sort (q2e/sort-keys->elastic-sort concept-type sort-keys)
        default-sort (q2e/sort-keys->elastic-sort concept-type (q/default-sort-keys concept-type))
        sub-sort-fields (if (:all-revisions? query)
                          tool-all-revision-sub-sort-fields
                          tool-latest-sub-sort-fields)]
    (concat (or specified-sort default-sort) sub-sort-fields)))

(defmethod q2e/query->sort-params :subscription
  [query]
  (let [{:keys [concept-type sort-keys]} query
        specified-sort (q2e/sort-keys->elastic-sort concept-type sort-keys)
        default-sort (q2e/sort-keys->elastic-sort concept-type (q/default-sort-keys concept-type))
        sub-sort-fields (if (:all-revisions? query)
                          subscription-all-revision-sub-sort-fields
                          subscription-latest-sub-sort-fields)]
    (concat (or specified-sort default-sort) sub-sort-fields)))

(defmethod q2e/query->sort-params :autocomplete
  [query]
  (let [{:keys [concept-type sort-keys]} query
        specified-sort (q2e/sort-keys->elastic-sort concept-type sort-keys)
        default-sort (q2e/sort-keys->elastic-sort concept-type (q/default-sort-keys concept-type))]
    (or specified-sort default-sort)))

(doseq [doseq-concept-type (concepts/get-generic-concept-types-array)]
  (defmethod q2e/query->sort-params doseq-concept-type
    [query]
    (let [{:keys [concept-type sort-keys]} query
          specified-sort (q2e/sort-keys->elastic-sort concept-type sort-keys)
          default-sort (q2e/sort-keys->elastic-sort concept-type (q/default-sort-keys concept-type))
          sub-sort-fields (if (:all-revisions? query)
                            [{(q2e/query-field->elastic-field :concept-id doseq-concept-type) {:order "asc"}}
                             {(q2e/query-field->elastic-field :revision-id doseq-concept-type) {:order "desc"}}]
                            [{(q2e/query-field->elastic-field :name doseq-concept-type) {:order "asc"}}
                             {(q2e/query-field->elastic-field :provider doseq-concept-type) {:order "asc"}}])]
      (concat (or specified-sort default-sort) sub-sort-fields))))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.CollectionQueryCondition
  (reduce-query-condition
    [condition context]
    (update-in condition [:condition] c2s/reduce-query-condition context)))
