(ns cmr.system-int-test.data2.aql
  "Contains helper functions for converting parameters into aql string."
  (:require [clojure.data.xml :as x]
            [clj-time.core :as t]
            [cmr.common.util :as u]
            [cmr.common.date-time-parser :as p]))

(defn- generate-value-element
  "Returns the xml element for the given element value. It will be either value or textPattern."
  [ignore-case pattern value]
  (let [case-option (case ignore-case
                      true {:caseInsensitive "Y"}
                      false {:caseInsensitive "N"}
                      {})]
    (if pattern
      (x/element :textPattern case-option value)
      (x/element :value case-option value))))

(defn- generate-attr-value-element
  "Returns the attribute value xml element for the given name and value string"
  [elem-name value]
  (when value (x/element elem-name {:value (str value)})))

(defn generate-date-element
  "Returns the xml element for the given date string"
  [value]
  (when value
    (let [dt (p/try-parse-datetime value)]
      (x/element :Date {:YYYY (str (t/year dt))
                        :MM (str (t/month dt))
                        :DD (str (t/day dt))
                        :HH (str (t/hour dt))
                        :MI (str (t/minute dt))
                        :SS (str (t/second dt))}))))

(defn generate-named-date-element
  "Returns the xml element for the given name and value string"
  [elem-name value]
  (when value
    (x/element elem-name {}
               (generate-date-element value))))

(defn generate-date-range-value-element
  "Returns the xml element for date range value of start-date and stop-date"
  [start-date stop-date]
  (x/element "dateRange" {}
             (generate-named-date-element :startDate start-date)
             (generate-named-date-element :stopDate stop-date)))

(defn generate-range-element
  "Returns the xml element for the given range"
  ([range-value]
   (generate-range-element :range range-value))
  ([range-elem-name [min-val max-val]]
   (let [options (-> {:lower min-val :upper max-val}
                     u/remove-nil-keys)]
     (x/element range-elem-name options))))

(defn- generate-keyword-element
  "Returns the xml element for the given key, value and options"
  [key value ignore-case pattern]
  (when value (x/element key {}
                         (generate-value-element ignore-case pattern value))))

(defn- generate-science-keyword-element
  [science-keyword]
  (let [{:keys [category topic term variable-level-1 variable-level-2 variable-level-3
                detailed-variable any ignore-case pattern]} science-keyword]
    (x/element :scienceKeyword {}
               (generate-keyword-element :categoryKeyword category ignore-case pattern)
               (generate-keyword-element :topicKeyword topic ignore-case pattern)
               (generate-keyword-element :termKeyword term ignore-case pattern)
               (generate-keyword-element :variableLevel1Keyword variable-level-1 ignore-case pattern)
               (generate-keyword-element :variableLevel2Keyword variable-level-2 ignore-case pattern)
               (generate-keyword-element :variableLevel3Keyword variable-level-3 ignore-case pattern)
               (generate-keyword-element :detailedVariableKeyword detailed-variable ignore-case pattern)
               (generate-keyword-element :anyKeyword any ignore-case pattern))))

(defn generate-string-value-element
  "Returns the xml element for string type of values"
  [elem-value ignore-case pattern]
  (if (sequential? elem-value)
    ;; a list with at least one value
    (if pattern
      (x/element :patternList {}
                 (map (partial generate-value-element ignore-case pattern) elem-value))
      (x/element :list {}
                 (map (partial generate-value-element ignore-case pattern) elem-value)))
    ;; a single value
    (generate-value-element ignore-case pattern elem-value)))

(def element-key-type-mapping
  "A mapping of AQL element key to its type, only keys with a type other than string are listed."
  {:onlineOnly :boolean
   :browseOnly :boolean
   :temporal :temporal
   :equatorCrossingDate :date-range
   :equatorCrossingLongitude :range
   :cloudCover :range
   :orbitNumber :orbit-number
   :dayNightFlag :day-night
   :scienceKeywords :science-keywords
   :additionalAttributes :additional-attributes
   :TwoDCoordinateSystem :two-d
   :polygon :polygon
   :box :box
   :line :line
   :point :point})

(defn condition->element-name
  "Returns the AQL element name of the given condition"
  [condition]
  (first (remove #{:ignore-case :pattern :or :and} (keys condition))))

(defn- condition->element-type
  "Returns the element type of the condition"
  [condition]
  (let [elem-key (condition->element-name condition)]
    (get element-key-type-mapping elem-key :string)))

(defn condition->operator-option
  "Returns the operator option of the condition"
  [condition]
  (let [operator (cond
                   (:or condition) "OR"
                   (:and condition) "AND"
                   :else nil)]
    (if operator {:operator operator} {})))

(defn- generate-two-d-coord-element
  "Returns the two d coordinate element for the given index and coordinate"
  [idx coord]
  (let [elem-key (keyword (str "coordinate" idx))
        coord (if coord coord "")]
    (if (sequential? coord)
      (x/element elem-key {}
                 (generate-range-element :range coord))
      (x/element elem-key {}
                 (x/element :value {} coord)))))

(defmulti generate-element
  "Returns the xml element for the given element condition"
  (fn [condition]
    (condition->element-type condition)))

(defmethod generate-element :string
  [condition]
  (let [elem-key (condition->element-name condition)
        elem-value (elem-key condition)
        {:keys [ignore-case pattern]} condition
        operator-option (condition->operator-option condition)]
    (x/element elem-key operator-option
               (generate-string-value-element elem-value ignore-case pattern))))

(defmethod generate-element :science-keywords
  [condition]
  (let [elem-key (condition->element-name condition)
        science-keywords (elem-key condition)
        operator-option (condition->operator-option condition)]
    (x/element elem-key operator-option
               (map generate-science-keyword-element science-keywords))))

(defmethod generate-element :two-d
  [condition]
  (let [elem-key (condition->element-name condition)
        two-d (elem-key condition)
        {:keys [name coord-1 coord-2 ignore-case]} two-d]
    (x/element elem-key {}
               (x/element :TwoDCoordinateSystemName {}
                          (generate-value-element ignore-case false name))
               (generate-two-d-coord-element 1 coord-1)
               (generate-two-d-coord-element 2 coord-2))))

(defn point-elem
  "Creates a AQL point element from a lon lat tuple"
  [[lon lat]]
  (x/element :IIMSPoint {:long lon :lat lat}))

(defmethod generate-element :polygon
  [condition]
  (let [ords (:polygon condition)
        point-pairs (partition 2 ords)]
    (x/element :spatial {}
               (x/element :IIMSPolygon {}
                          (x/element :IIMSLRing {}
                                     (map point-elem point-pairs))))))

(defmethod generate-element :line
  [condition]
  (let [ords (:line condition)
        point-pairs (partition 2 ords)]
    (x/element :spatial {}
               (x/element :IIMSLine {}
                          (map point-elem point-pairs)))))

(defmethod generate-element :box
  [condition]
  (let [[w n e s] (:box condition)]
    (x/element :spatial {}
               (x/element :IIMSBox {}
                          ;; lower left
                          (point-elem [w s])
                          ;; upper right
                          (point-elem [e n])))))

(defmethod generate-element :point
  [condition]
  (let [point-pair (:point condition)]
    (x/element :spatial {} (point-elem point-pair))))

(defmethod generate-element :boolean
  [condition]
  (let [elem-key (condition->element-name condition)
        elem-value (elem-key condition)]
    (case elem-value
      true (x/element elem-key {:value "Y"})
      nil (x/element elem-key {})
      nil)))

(defmethod generate-element :temporal
  [condition]
  (let [elem-key (condition->element-name condition)
        {:keys [start-date stop-date start-day end-day]} (elem-key condition)]
    (x/element elem-key {}
               (generate-named-date-element :startDate start-date)
               (generate-named-date-element :stopDate stop-date)
               (generate-attr-value-element :startDay start-day)
               (generate-attr-value-element :endDay end-day))))

(defmethod generate-element :date-range
  [condition]
  (let [elem-key (condition->element-name condition)
        {:keys [start-date stop-date]} (elem-key condition)]
    (x/element elem-key {}
               (generate-date-range-value-element start-date stop-date))))

(defmethod generate-element :orbit-number
  [condition]
  (let [elem-key (condition->element-name condition)
        value (elem-key condition)]
    (x/element elem-key {}
               (if (sequential? value)
                 (generate-range-element value)
                 (x/element :value {} (str value))))))

(defmethod generate-element :day-night
  [condition]
  (let [elem-key (condition->element-name condition)
        value (elem-key condition)
        value-option (if (empty? value) {} {:value value})]
    (x/element elem-key value-option)))

(defmethod generate-element :range
  [condition]
  (let [elem-key (condition->element-name condition)
        value (elem-key condition)]
    (x/element elem-key {}
               (generate-range-element value))))

(defn- generate-data-center
  "Returns the dataCenter element for the data center condition"
  [condition]
  (if (empty? (:dataCenterId condition))
    (x/element :dataCenterId {}
               (x/element :all {}))
    (generate-element condition)))

(defn generate-aql
  "Returns aql search string from input data-center-condition and conditions.
  data-center-condition is either nil or a map with possible keys of :dataCenter :ignore-case and :pattern,
  conditions is a vector of conditions that will populate the where conditions."
  [concept-type data-center-condition conditions]
  (let [condition-elem-name (if (= :collection concept-type) :collectionCondition :granuleCondition)]
    (x/emit-str
      (x/element :query {}
                 (x/element :for {:value (format "%ss" (name concept-type))})
                 (generate-data-center data-center-condition)
                 (x/element :where {}
                            (map (fn [condition]
                                   (x/element condition-elem-name {}
                                              (generate-element condition)))
                                 conditions))))))

