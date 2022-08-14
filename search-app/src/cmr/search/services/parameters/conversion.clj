(ns cmr.search.services.parameters.conversion
  "Contains functions for parsing and converting query parameters to query conditions"
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.parameters.converters.nested-field :as nf]
   [cmr.common-app.services.search.params :as common-params]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common.concepts :as cc]
   [cmr.common.date-time-parser :as parser]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.models.query :as qm]
   [cmr.search.services.parameters.legacy-parameters :as lp]))

;; Note: the suffix "-h" on parameters denotes the humanized version of a parameter

(defmethod common-params/param-mappings :autocomplete
  [_]
  {:q :string
   :type :string})

(defmethod common-params/param-mappings :collection
  [_]
  {:archive-center :string
   :attribute :attribute
   :author :string
   :bounding-box :bounding-box
   :browsable :boolean
   :collection-data-type :collection-data-type
   :concept-id :string
   :created-at :multi-date-range
   :data-center :string
   :data-center-h :humanizer ;; Searches UMM orgs of any type (:archive-center, :data-center, ...)
   :doi :string
   :downloadable :boolean
   :entry-id :string
   :entry-title :string
   :exclude :exclude
   :granule-data-format :string
   :granule-data-format-h :humanizer
   :horizontal-data-resolution-range :range-facet
   :has-granules :has-granules
   :has-granules-or-cwic :has-granules-or-cwic
   :has-granules-or-opensearch :has-granules-or-opensearch
   :has-granules-created-at :multi-date-range
   :has-granules-revised-at :multi-date-range
   :has-opendap-url :boolean
   :cloud-hosted :boolean
   :standard-product :boolean
   :instrument :string
   :instrument-h :humanizer
   :keyword :keyword
   :line :line
   :native-id :string
   :platform :string
   :platform-h :humanizer
   :platforms :platforms ;; v2 facet support
   :platforms-h :platforms ;; v2 facet support
   :point :point
   :polygon :polygon
   :circle :circle
   :processing-level-id :string
   :processing-level-id-h :humanizer
   :project :string
   :project-h :humanizer
   :latency :string
   :consortium :string
   :provider :string
   :revision-date :multi-date-range
   :science-keywords :science-keywords
   :science-keywords-h :science-keywords
   :sensor :string
   :short-name :string
   :spatial-keyword :string
   :temporal :temporal
   :two-d-coordinate-system :two-d-coordinate-system
   :two-d-coordinate-system-name :string
   :updated-since :updated-since
   :version :string
   :facets-size :string
   :shapefile :shapefile
   :simplify-shapefile :boolean

   ;; Tag parameters
   :tag-data :tag-query
   :tag-key :tag-query
   :tag-originator-id :tag-query

   ;; Variable parameters
   :measurement :string
   :variable-name :string
   :variable-concept-id :string
   :variable-native-id :string
   :variables-h :variables-h

   ;; service parameters
   :service-name :string
   :service-type :string
   :service-concept-id :string

   ;; tool parameters
   :tool-name :string
   :tool-type :string
   :tool-concept-id :string})

(defmethod common-params/param-mappings :granule
  [_]
  {:attribute :attribute
   :bounding-box :bounding-box
   :browsable :boolean
   :cloud-cover :num-range
   :collection-concept-id :collection-query
   :concept-id :granule-concept-id
   :created-at :multi-date-range
   :day-night :string
   :downloadable :boolean
   :entry-title :collection-query
   :equator-crossing-date :equator-crossing-date
   :equator-crossing-longitude :equator-crossing-longitude
   :exclude :exclude
   :granule-ur :string
   :instrument :inheritance
   :line :line
   :native-id :string
   :orbit-number :orbit-number
   :platform :inheritance
   :point :point
   :polygon :polygon
   :circle :circle
   :producer-granule-id :string
   :production-date :multi-date-range
   :project :string
   :feature-id :string
   :crid-id :string
   :provider :collection-query
   :readable-granule-name :readable-granule-name
   :revision-date :multi-date-range
   :sensor :inheritance
   :short-name :collection-query
   :spatial :spatial
   :temporal :temporal
   :temporal-facet :temporal-facet
   :two-d-coordinate-system :two-d-coordinate-system
   :updated-since :updated-since
   :version :collection-query
   :cycle :int
   :passes :passes
   :shapefile :shapefile
   :simplify-shapefile :boolean})

(defmethod common-params/param-mappings :tag
  [_]
  {:tag-key :string
   :originator-id :string})

(defmethod common-params/param-mappings :variable
  [_]
  {:variable-name :string
   :alias :string
   :name :string
   :full-path :string
   :measurement :string
   :instrument :string
   :provider :string
   :native-id :string
   :concept-id :string
   :keyword :keyword
   :measurement-identifiers :measurement-identifiers})

(defmethod common-params/param-mappings :service
  [_]
  {:name :string
   :type :string
   :provider :string
   :native-id :string
   :concept-id :string
   :keyword :keyword})

(defmethod common-params/param-mappings :tool
  [_]
  {:name :string
   :provider :string
   :native-id :string
   :concept-id :string
   :keyword :keyword})

(defmethod common-params/param-mappings :subscription
  [_]
  {:subscription-name :string
   :name :string
   :type :string
   :subscriber-id :string
   :collection-concept-id :string
   :provider :string
   :native-id :string
   :concept-id :string
   :keyword :keyword})

(defmethod common-params/always-case-sensitive-fields :collection
  [_]
  #{:concept-id :variable-concept-id :service-concept-id :tool-concept-id})

(defmethod common-params/always-case-sensitive-fields :granule
  [_]
  #{:concept-id :collection-concept-id})

(defmethod common-params/parameter->condition :keyword
  [_ _ _ value _]
  (cqm/text-condition :keyword (string/lower-case value)))

(def collection-only-params
  "List of parameters that are valid in collection query models, but not in granule query models."
  (let [granule-param-mappings (common-params/param-mappings :granule)
        collection-param-mappings (common-params/param-mappings :collection)
        do-not-exist-for-granules (set/difference (set (keys collection-param-mappings))
                                                  (set (keys granule-param-mappings)))
        collection-query-params (keep (fn [[k v]]
                                        (when (= v :collection-query)
                                          k))
                                      granule-param-mappings)]
    (concat do-not-exist-for-granules collection-query-params)))

(def collection-to-granule-params
  "Mapping of parameter names in a collection query to the parameter name to use in the granule
  query."
  {:concept-id :collection-concept-id
   :has-granules-created-at :created-at
   :has-granules-revised-at :revision-date-stored-doc-values})

(def granule-param-names
  "Set of granule search parameter names."
  (set (keys (common-params/param-mappings :granule))))

(defmulti tag-param->condition
  "Convert tag param and value into query condition"
  (fn [param value pattern?]
    param))

(defmethod tag-param->condition :tag-key
  [param value pattern?]
  (nf/parse-nested-condition :tags {param value} false pattern?))

(defmethod tag-param->condition :tag-originator-id
  [param value pattern?]
  (nf/parse-nested-condition :tags {:originator-id value} false pattern?))

(defmethod tag-param->condition :tag-data
  [param value pattern?]
  (let [conditions (for [[tag-key tag-value] value]
                     (nf/parse-nested-condition :tags {:tag-key (name tag-key)
                                                       :tag-value tag-value} false pattern?))]
    (gc/and-conds conditions)))

(defmethod common-params/parameter->condition :tag-query
  [_context concept-type param value options]
  (let [;; tag-key defaults to pattern true
        pattern? (if (= :tag-key param)
                   (not= "false" (get-in options [param :pattern]))
                   (common-params/pattern-field? concept-type param options))]
    (tag-param->condition param value pattern?)))

(defmethod common-params/parameter->condition :variables-h
  [_context concept-type param value options]
  (let [case-sensitive? (common-params/case-sensitive-field? concept-type param options)
        pattern? (common-params/pattern-field? concept-type param options)
        group-operation (common-params/group-operation param options :and)
        target-field (keyword (string/replace (name param) #"-h$" ""))]

    (if (map? (first (vals value)))
      ;; If multiple variables are passed in like the following
      ;;  -> variables-h[0][measurement]=foo&variables-h[1][measurement]=bar
      ;; then this recurses back into this same function to handle each separately
      (gc/group-conds
       group-operation
       (map #(common-params/parameter->condition _context concept-type param % options)
            (vals value)))
      ;; Creates the  variable condition for a group of variable fields and values.
      (nf/parse-nested-condition target-field value case-sensitive? pattern?))))

;; Special case handler for concept-id. Concept id can refer to a granule or collection.
;; If it's a granule query with a collection concept id then we convert the parameter to :collection-concept-id
(defmethod common-params/parameter->condition :granule-concept-id
  [context concept-type param value options]
  (let [values (if (sequential? value) value [value])
        {granule-concept-ids :granule
         collection-concept-ids :collection} (group-by (comp :concept-type cc/parse-concept-id) values)
        collection-cond (when (seq collection-concept-ids)
                          (qm/->CollectionQueryCondition
                           (common-params/parameter->condition
                            context :collection :concept-id value options)))
        granule-cond (when (seq granule-concept-ids)
                       (let [provider-ids (distinct
                                           (map (comp :provider-id cc/parse-concept-id)
                                                granule-concept-ids))]
                         (gc/and-conds [(qm/->CollectionQueryCondition
                                         (common-params/parameter->condition
                                          context :collection :provider provider-ids {}))
                                        (common-params/string-parameter->condition
                                         concept-type :concept-id granule-concept-ids options)])))]
    (if (and collection-cond granule-cond)
      (gc/and-conds [collection-cond granule-cond])
      (or collection-cond granule-cond))))

;; Construct an inheritance query condition for granules.
;; This will find granules which either have explicitly specified a value
;; or have not specified any value for the field and inherit it from their parent collection.
(defmethod common-params/parameter->condition :inheritance
  [context concept-type param value options]
  (let [field-condition (common-params/parameter->condition context :collection param value options)
        exclude-collection (= "true" (get-in options [param :exclude-collection]))
        collection-cond (gc/and-conds
                         [(qm/->CollectionQueryCondition field-condition)
                          (cqm/map->MissingCondition {:field param})])]
    (if exclude-collection
      field-condition
      (gc/or-conds
       [field-condition
        collection-cond]))))

(defmethod common-params/parameter->condition :updated-since
  [_context concept-type param value options]
  (cqm/map->DateRangeCondition
   {:field param
    :start-date (parser/parse-datetime
                 (if (sequential? value) (first value) value))
    :end-date nil}))

(defmethod common-params/parameter->condition :multi-date-range
  [context concept-type param value options]
  (if (sequential? value)
    (if (= "true" (get-in options [param :and]))
      (gc/and-conds
       (map #(common-params/parameter->condition context concept-type param % options) value))
      (gc/or-conds
       (map #(common-params/parameter->condition context concept-type param % options) value)))
    (let [[start-date end-date] (map string/trim (string/split value #","))]
      (cqm/map->DateRangeCondition
       {:field param
        :start-date (when-not (string/blank? start-date) (parser/parse-datetime start-date))
        :end-date (when-not (string/blank? end-date) (parser/parse-datetime end-date))}))))

(defmethod common-params/parameter->condition :readable-granule-name
  [context concept-type param value options]
  (if (sequential? value)
    (if (= "true" (get-in options [param :and]))
      (gc/and-conds
       (map #(common-params/parameter->condition context concept-type param % options) value))
      (gc/or-conds
       (map #(common-params/parameter->condition context concept-type param % options) value)))
    (let [case-sensitive (common-params/case-sensitive-field? concept-type :readable-granule-name options)
          pattern (common-params/pattern-field? concept-type :readable-granule-name options)]
      (gc/or-conds
       [(cqm/string-condition :granule-ur value case-sensitive pattern)
        (cqm/string-condition :producer-granule-id value case-sensitive pattern)]))))

(defmethod common-params/parameter->condition :has-granules
  [_ _ _ value _]
  (if (= "unset" value)
    cqm/match-all
    (qm/->HasGranulesCondition (= "true" (string/lower-case value)))))

(defmethod common-params/parameter->condition :has-granules-or-cwic
  [_ _ _ value _]
  (qm/->HasGranulesOrCwicCondition (= "true" (string/lower-case value))))

(defmethod common-params/parameter->condition :has-granules-or-opensearch
  [_ _ _ value _]
  (qm/->HasGranulesOrOpenSearchCondition (= "true" (string/lower-case value))))

(defn- collection-data-type-matches-science-quality?
  "Convert the collection-data-type parameter with wildcards to a regex. This function
  does not fully handle escaping special characters and is only intended for handling
  the special case of SCIENCE_QUALITY."
  [param case-sensitive]
  (let [param (if case-sensitive
                (string/upper-case param)
                param)
        pattern (-> param
                    (string/replace #"\?" ".")
                    (string/replace #"\*" ".*")
                    re-pattern)]
    (re-find pattern "SCIENCE_QUALITY")))

(defmethod common-params/parameter->condition :collection-data-type
  [context concept-type param value options]
  (if (sequential? value)
    (if (= "true" (get-in options [param :and]))
      (gc/and-conds
       (map #(common-params/parameter->condition context concept-type param % options) value))
      (gc/or-conds
       (map #(common-params/parameter->condition context concept-type param % options) value)))
    (let [case-sensitive (common-params/case-sensitive-field? concept-type :collection-data-type options)
          pattern (common-params/pattern-field? concept-type :collection-data-type options)]
      (if (or (= "SCIENCE_QUALITY" value)
              (and (= "SCIENCE_QUALITY" (string/upper-case value))
                   (not= "false" (get-in options [:collection-data-type :ignore-case])))
              (and pattern
                   (collection-data-type-matches-science-quality? value case-sensitive)))
        ; SCIENCE_QUALITY collection-data-type should match concepts with SCIENCE_QUALITY
        ; or the ones missing collection-data-type field
        (gc/or-conds
         [(cqm/string-condition :collection-data-type value case-sensitive pattern)
          (cqm/map->MissingCondition {:field :collection-data-type})])
        (cqm/string-condition :collection-data-type value case-sensitive pattern)))))

;; Converts variable measurement identifiers parameter and values into conditions
(defmethod common-params/parameter->condition :measurement-identifiers
  [context concept-type param value options]
  (let [case-sensitive? (common-params/case-sensitive-field? concept-type param options)
        pattern? (common-params/pattern-field? concept-type param options)
        group-operation (common-params/group-operation param options :and)]
    (if (map? (first (vals value)))
      ;; If multiple measurement-identifiers are passed in like the following
      ;; -> measurement-identifiers[0][contextmedium]=foo&measurement-identifiers[1][contextmedium]=bar
      ;; then this recurses back into this same function to handle each separately
      (gc/group-conds
       group-operation
       (map #(common-params/parameter->condition context concept-type param % options)
            (vals value)))
      ;; Creates the nested condition for a group of measurement-identifiers fields and values.
      (nf/parse-nested-condition param value case-sensitive? pattern?))))

(defn- reverse-has-granules-sort
  "The has-granules sort is the opposite of natural sorting. Collections with granules are first then
   collections without sorting. This reverses the order of that sort key in the query attributes if
   it is present."
  [query-attribs]
  (update query-attribs :sort-keys
          (fn [sort-keys]
            (seq (for [{:keys [field order] :as sort-key} sort-keys]
                   (if (or (= field :has-granules)
                           (= field :has-granules-or-cwic)
                           (= field :has-granules-or-opensearch))
                     {:field field :order (if (= order :asc) :desc :asc)}
                     sort-key))))))

(defmethod common-params/parse-query-level-params :collection
  [concept-type params]
  (let [[params query-attribs] (common-params/default-parse-query-level-params
                                 :collection params lp/param-aliases)
        query-attribs (reverse-has-granules-sort query-attribs)
        {:keys [begin-tag end-tag snippet-length num-snippets]} (get-in params [:options :highlights])
        result-features (concat (when (= (:include-granule-counts params) "true")
                                  [:granule-counts])
                                (when (= (:include-has-granules params) "true")
                                  [:has-granules])
                                (when (= (:include-facets params) "true")
                                  (if (= "true" (:hierarchical-facets params))
                                    [:hierarchical-facets]
                                    [:facets]))
                                (when (= "v2" (util/safe-lowercase (:include-facets params)))
                                  [:facets-v2])
                                (when (= (:include-highlights params) "true")
                                  [:highlights])
                                (when (:has-granules-created-at params)
                                  [:has-granules-created-at])
                                (when (:has-granules-revised-at params)
                                  [:has-granules-revised-at])
                                (when-not (string/blank? (:include-tags params))
                                  [:tags])
                                ;; Always include temporal, the processor will see if any temporal
                                ;; conditions exist
                                [:temporal-conditions])
        keywords (when (:keyword params)
                   (string/split (string/lower-case (:keyword params)) #" "))
        params (if keywords (assoc params :keyword (string/join " " keywords)) params)
        simplify-shapefile? (= "true" (:simplify-shapefile params))]
    [(dissoc params
             :boosts :include-granule-counts :include-has-granules :include-facets :echo-compatible
             :hierarchical-facets :include-highlights :include-tags :all-revisions :facets-size
             :simplify-shapefile)
     (-> query-attribs
         (merge {:boosts (:boosts params)
                 :result-features (seq result-features)
                 :echo-compatible? (= "true" (:echo-compatible params))
                 :all-revisions? (= "true" (:all-revisions params))
                 :simplify-shapefile? (when simplify-shapefile? simplify-shapefile?)
                 :facets-size (:facets-size params)
                 :result-options (merge (when-not (string/blank? (:include-tags params))
                                          {:tags (map string/trim (string/split (:include-tags params) #","))})
                                        (when (or begin-tag end-tag snippet-length num-snippets)
                                          {:highlights
                                           {:begin-tag begin-tag
                                            :end-tag end-tag
                                            :snippet-length (when snippet-length (Integer. snippet-length))
                                            :num-snippets (when num-snippets (Integer. num-snippets))}}))})
         util/remove-nil-keys)]))

(defmethod common-params/parse-query-level-params :granule
  [concept-type params]
  (let [[params query-attribs] (common-params/default-parse-query-level-params
                                :granule params lp/param-aliases)
        result-features (when (= "v2" (util/safe-lowercase (:include-facets params)))
                          [:facets-v2])
        regular-params (dissoc params :echo-compatible :include-facets :simplify-shapefile)
        {:keys [page-size offset]} query-attribs
        concept-id (:concept-id regular-params)
        concept-ids (when concept-id
                      (if (sequential? concept-id)
                          concept-id
                          [concept-id]))
        only-concept-id-params? (nil?
                                 (some granule-param-names
                                       (keys (dissoc regular-params :concept-id))))
        no-collection-in-concept-id? (nil? (some #(string/starts-with? % "C") concept-ids))
        gran-specific-items-query? (if (and (:concept-id regular-params)
                                            only-concept-id-params?
                                            no-collection-in-concept-id?
                                            (= 0 offset)
                                            (or (= page-size :unlimited)
                                                (>= page-size (count concept-ids))))
                                     true
                                     false)]
    [regular-params
     (merge query-attribs
            {:echo-compatible? (= "true" (:echo-compatible params))
             :simplify-shapefile? (= "true" (:simplify-shapefile params))
             :result-features result-features
             :gran-specific-items-query? gran-specific-items-query?})]))

(defmethod common-params/parse-query-level-params :variable
  [concept-type params]
  (let [[params query-attribs] (common-params/default-parse-query-level-params
                                 :variable params)]
    [(dissoc params :all-revisions)
     (merge query-attribs {:all-revisions? (= "true" (:all-revisions params))})]))

(defmethod common-params/parse-query-level-params :service
  [concept-type params]
  (let [[params query-attribs] (common-params/default-parse-query-level-params
                                 :service params)]
    [(dissoc params :all-revisions)
     (merge query-attribs {:all-revisions? (= "true" (:all-revisions params))})]))

(defmethod common-params/parse-query-level-params :tool
  [concept-type params]
  (let [[params query-attribs] (common-params/default-parse-query-level-params
                                 :tool params)]
    [(dissoc params :all-revisions)
     (merge query-attribs {:all-revisions? (= "true" (:all-revisions params))})]))

(defmethod common-params/parse-query-level-params :subscription
  [concept-type params]
  (let [[params query-attribs] (common-params/default-parse-query-level-params
                                 :subscription params)]
    [(dissoc params :all-revisions)
     (merge query-attribs {:all-revisions? (= "true" (:all-revisions params))})]))

(defn timeline-parameters->query
  "Converts parameters from a granule timeline request into a query."
  [context params]
  (let [{:keys [interval start-date end-date]} params
        query (common-params/parse-parameter-query
               context
               :granule
               (dissoc params :interval :start-date :end-date))]
    ;; Add timeline request fields to the query so that they can be used later
    ;; for processing the timeline results.
    (assoc query
           :interval (keyword interval)
           :start-date (parser/parse-datetime start-date)
           :end-date (parser/parse-datetime end-date)
           ;; Indicate the result feature of timeline so that we can preprocess
           ;; the query and add aggregations and make other changes.
           :result-features [:timeline]
           :result-format :timeline)))
