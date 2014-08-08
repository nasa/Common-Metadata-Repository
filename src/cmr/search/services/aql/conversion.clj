(ns cmr.search.services.aql.conversion
  "Contains functions for parsing and converting aql to query conditions"
  (:require [clojure.string :as s]
            [clojure.data.xml :as x]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [cmr.common.xml :as cx]
            [cmr.common.util :as u]
            [cmr.common.services.errors :as errors]
            [cmr.search.services.messages.common-messages :as msg]
            [clj-time.core :as t]
            [cmr.search.models.query :as qm]
            [cmr.search.services.parameters.conversion :as p]))

(def aql-elem->converter-attrs
  "A mapping of aql element names to query condition types based on concept-type"
  {:collection {:dataCenterId {:name :provider-id :type :string}
                :shortName {:name :short-name :type :string}
                :versionId {:name :version-id :type :string}
                :CampaignShortName {:name :project :type :string}
                :dataSetId {:name :entry-title :type :string}
                :ECHOLastUpdate {:name :updated-since :type :date-range}
                :onlineOnly {:name :downloadable :type :boolean}
                :ECHOCollectionID {:name :concept-id :type :string}
                :processingLevel {:name :processing-level-id :type :string}
                :sensorName {:name :sensor :type :string}
                :sourceName {:name :platform :type :string}
                :instrumentShortName {:name :instrument :type :string}
                ;; TODO spatial
                :spatial {:name :spatial :type :spatial}
                :temporal {:name :temporal :type :temporal}
                :difEntryId {:name :dif-entry-id :type :dif-entry-id}
                :entry-id {:name :entry-id :type :string}
                :associated-difs {:name :associated-difs :type :string}
                :scienceKeywords {:name :science-keywords :type :string}
                :TwoDCoordinateSystemName {:name :two-d-coordinate-system-name :type :string}}
   :granule {:dataCenterId {:name :provider-id :type :collection-query}
             :GranuleUR {:name :granule-ur :type :string}
             :collectionShortName {:name :short-name :type :collection-query}
             :collectionVersionId {:name :version-id :type :collection-query}
             :browseOnly {:name :browsable :type :boolean}
             :CampaignShortName {:name :project :type :string}
             :cloudCover {:name :cloud-cover :type :num-range}
             :dataSetId {:name :entry-title :type :collection-query}
             :dayNightFlag {:name :day-night :type :string}
             :ECHOLastUpdate {:name :updated-since :type :date-range}
             :onlineOnly {:name :downloadable :type :boolean}
             :ECHOCollectionID {:name :collection-concept-id :type :string}
             :ECHOGranuleID {:name :concept-id :type :string}
             :ProducerGranuleID {:name :producer-gran-id :type :string}
             :sensorName {:name :sensor :type :string}
             :sourceName {:name :platform :type :string}
             :instrumentShortName {:name :instrument :type :string}
             ;; TODO spatial
             :spatial {:name :spatial :type :spatial}
             :temporal {:name :temporal :type :temporal}
             :additionalAttributes {:name :attribute :type :attribute}
             :orbitNumber {:name :orbit-number :type :orbit-number}
             :equatorCrossingLongitude {:name :equator-crossing-longitude :type :equator-crossing-longitude}
             :equatorCrossingDate {:name :equator-crossing-date :type :equator-crossing-date}
             :TwoDCoordinateSystemName {:name :two-d-coordinate-system-name :type :string}}})

(defn- elem-name->type
  "Returns the query condition type based on the given concept-type and aql element name."
  [concept-type elem-name]
  (get-in aql-elem->converter-attrs [concept-type elem-name :type]))

(defn- elem-name->condition-key
  "Returns the query condition key name based on the given concept-type and aql element name."
  [concept-type elem-name]
  (get-in aql-elem->converter-attrs [concept-type elem-name :name]))

(defn- inherited-condition?
  "Returns true if the given key of concept-type is inheritance"
  [concept-type condition-key]
  (let [key [concept-type condition-key]]
    (get {[:granule :platform] true
          [:granule :instrument] true
          [:granule :sensor] true} key)))

(defn- update-values [m f & args]
  "update values in a map by applying the given function and args"
  (reduce (fn [r [k v]]
            (assoc r k (apply f v args)))
          {}
          m))

(defn- inheritance-condition
  "Returns the inheritance condition for the given key value and options"
  [key value case-sensitive? pattern?]
  (let [ignore-case (when (= true case-sensitive?) false)
        options (-> (u/remove-nil-keys {:ignore-case ignore-case :pattern pattern?})
                    (update-values str))
        options {key options}]
    (p/parameter->condition :granule key value options)))

(defn- string-value-elem->condition
  "Converts a string value element to query condition"
  ([concept-type key elem]
   (string-value-elem->condition concept-type key elem false))
  ([concept-type key elem pattern?]
   (let [value (first (:content elem))
         value (if pattern?
                 (-> value
                     (s/replace #"([^\\])(%)" "$1*")
                     (s/replace #"([^\\])(_)" "$1?")
                     (s/replace #"^%(.*)" "*$1")
                     (s/replace #"^_(.*)" "?$1"))
                 value)
         case-insensitive (get-in elem [:attrs :caseInsensitive])
         case-sensitive? (if (and case-insensitive (= "N" (s/upper-case case-insensitive))) true false)
         case-sensitive? (if (some? (p/always-case-sensitive key)) true case-sensitive?)]
     (if (inherited-condition? concept-type key)
       (inheritance-condition key value case-sensitive? pattern?)
       (qm/string-condition key value case-sensitive? pattern?)))))

(defn- string-pattern-elem->condition
  "Converts a string value element to query condition"
  [concept-type key elem]
  (string-value-elem->condition concept-type key elem true))

(defn- date-time-from-strings
  "Returns date-time from strings of year, month, day, etc. Returns nil if all strings are nil."
  [year month day hour minute sec]
  (when (or year month day hour minute sec)
    (let [units (map (fn [^String unit] (if unit (Long. unit) 0)) [year month day hour minute sec])]
      (apply t/date-time units))))

(defn parse-date-range-element
  "Returns start-date and stop-date in a vector by parsing the given data range element"
  [element]
  (let [{year :YYYY month :MM day :DD hour :HH minute :MI sec :SS}
        (cx/attrs-at-path element [:startDate :Date])
        start-date (date-time-from-strings year month day hour minute sec)
        {year :YYYY month :MM day :DD hour :HH minute :MI sec :SS}
        (cx/attrs-at-path element [:stopDate :Date])
        stop-date (date-time-from-strings year month day hour minute sec)]
    [start-date stop-date]))

(defn- element->num-range
  [concept-type element]
  (let [string-double-fn (fn [n] (when n (Double. n)))
        range-val (-> (cx/attrs-at-path element [:range])
                      (set/rename-keys {:lower :min-value :upper :max-value})
                      (update-in [:min-value] string-double-fn)
                      (update-in [:max-value] string-double-fn))]
    range-val))

(defmulti element->condition
  "Converts a aql element into a condition"
  (fn [concept-type elem]
    (elem-name->type concept-type (:tag elem))))

(defn string-element->condition
  ([concept-type element]
   (let [condition-key (elem-name->condition-key concept-type (:tag element))
         operator (get-in element [:attrs :operator])]
     (string-element->condition concept-type condition-key operator (first (:content element)))))
  ([concept-type key operator element]
   (let [elem-type (:tag element)]
     (case elem-type
       :value (string-value-elem->condition concept-type key element)
       :textPattern (string-pattern-elem->condition concept-type key element)
       ;; list and patternList can be processed the same way below
       (if (= "AND" operator)
         (qm/and-conds
           (map (partial string-element->condition concept-type key operator) (:content element)))
         (qm/or-conds
           (map (partial string-element->condition concept-type key operator) (:content element))))))))

(defmethod element->condition :string
  [concept-type element]
  (string-element->condition concept-type element))

(defmethod element->condition :dif-entry-id
  [concept-type element]
  (qm/or-conds
    [(element->condition concept-type (assoc element :tag :entry-id))
     (element->condition concept-type (assoc element :tag :associated-difs))]))

(defmethod element->condition :date-range
  [concept-type element]
  (let [condition-key (elem-name->condition-key concept-type (:tag element))
        [start-date stop-date] (parse-date-range-element element)]
    (qm/map->DateRangeCondition
      {:field condition-key
       :start-date start-date
       :end-date stop-date})))

(defmethod element->condition :equator-crossing-date
  [concept-type element]
  (let [[start-date stop-date] (parse-date-range-element (cx/element-at-path element [:dateRange] ))]
    (qm/map->EquatorCrossingDateCondition {:start-date start-date
                                           :end-date stop-date})))

(defmethod element->condition :boolean
  [concept-type element]
  (let [condition-key (elem-name->condition-key concept-type (:tag element))
        value (get-in element [:attrs :value] "Y")]
    (qm/map->BooleanCondition {:field condition-key
                               :value (= "Y" value)})))

(defmethod element->condition :orbit-number
  [concept-type element]
  (if-let [value (cx/double-at-path element [:value])]
    (qm/map->OrbitNumberValueCondition {:value value})
    (qm/map->OrbitNumberRangeCondition (element->num-range concept-type element))))

(defmethod element->condition :equator-crossing-longitude
  [concept-type element]
  (qm/map->EquatorCrossingLongitudeCondition (element->num-range concept-type element)))

(defmethod element->condition :num-range
  [concept-type element]
  (let [condition-key (elem-name->condition-key concept-type (:tag element))
        numeric-range-map (assoc (element->num-range concept-type element) :field condition-key)]
    (qm/map->NumericRangeCondition numeric-range-map)))

(def aql-query-type->concept-type
  "Mapping of AQL query type to search concept type"
  {"collections" :collection
   "granules" :granule})

(defn validate-aql
  "Validates the XML against the AQL schema."
  [xml]
  (when-let [errors (cx/validate-xml (io/resource "schema/IIMSAQLQueryLanguage.xsd")
                                     (cx/remove-xml-processing-instructions xml))]
    (errors/throw-service-errors :invalid-data (map msg/invalid-aql errors))))

(defn- condition-elem-group->conditions
  "Convert a collectionCondition|granuleCondition element group to conditions"
  [concept-type condition-elems]
  (map (partial element->condition concept-type) condition-elems))

(defn- get-concept-type
  "Parse and returns the concept-type from the parsed AQL xml struct"
  [xml-struct]
  (let [aql-query-type (:value (cx/attrs-at-path xml-struct [:for]))]
    (aql-query-type->concept-type aql-query-type)))

(defn- xml-struct->data-center-condition
  "Parse and returns the data-center-condition from the parsed AQL xml struct"
  [concept-type xml-struct]
  (when (nil? (cx/content-at-path xml-struct [:dataCenterId :all]))
    (element->condition concept-type (cx/element-at-path xml-struct [:dataCenterId]))))

(defn- xml-struct->where-conditions
  "Parse and returns the where conditions from the parsed AQL xml struct"
  [concept-type xml-struct]
  (let [condition-key (if (= :collection concept-type) :collectionCondition :granuleCondition)
        condition-groups (cx/contents-at-path xml-struct [:where condition-key])]
    (mapcat (partial condition-elem-group->conditions concept-type) condition-groups)))

(defn- xml-struct->query-condition
  "Parse and returns the query condition from the parsed AQL xml struct"
  [concept-type xml-struct]
  (let [data-center-condition (xml-struct->data-center-condition concept-type xml-struct)
        where-conditions (xml-struct->where-conditions concept-type xml-struct)
        conditions (if data-center-condition
                     (cons data-center-condition where-conditions)
                     where-conditions)]
    (when (seq conditions) (qm/and-conds conditions))))

(defn aql->query
  "Converts aql into a query model."
  [params aql]
  (validate-aql aql)
  (let [page-size (Integer. (get params :page-size qm/default-page-size))
        page-num (Integer. (get params :page-num qm/default-page-num))
        pretty (get params :pretty false)
        result-format (:result-format params)
        xml-struct (x/parse-str aql)
        concept-type (get-concept-type xml-struct)
        condition (xml-struct->query-condition concept-type xml-struct)]
    (qm/query {:concept-type concept-type
               :page-size page-size
               :page-num page-num
               :pretty pretty
               :condition condition
               :result-format result-format})))

