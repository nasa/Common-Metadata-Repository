(ns cmr.search.services.parameters.parameter-validation
  "Contains functions for validating query parameters"
  (:require
   [camel-snake-kebab.core :as csk]
   [clj-time.core :as t]
   [clojure.set :as set]
   [clojure.string :as s]
   [cmr.common-app.services.search.messages :as d-msg]
   [cmr.common-app.services.search.parameter-validation :as cpv]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common.concepts :as cc]
   [cmr.common.mime-types :as mt]
   [cmr.common.date-time-parser :as dt-parser]
   [cmr.common.parameter-parser :as parser]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.data.keywords-to-elastic :as k2e]
   [cmr.search.services.messages.attribute-messages :as attrib-msg]
   [cmr.search.services.messages.common-messages :as msg]
   [cmr.search.services.messages.orbit-number-messages :as on-msg]
   [cmr.search.services.parameters.converters.attribute :as attrib]
   [cmr.search.services.parameters.validation.track :as track]
   [cmr.search.services.parameters.validation.util :as validation-util]
   [cmr.spatial.codec :as spatial-codec]
   [cmr.spatial.validation :as sv])
  (:import
   (clojure.lang ExceptionInfo)
   (java.lang Integer Long)))

(defmethod cpv/params-config :collection
  [_]
  (cpv/merge-params-config
   cpv/basic-params-config
   {:single-value #{:keyword :echo-compatible :include-granule-counts :include-has-granules
                    :include-facets :hierarchical-facets :include-highlights :include-tags
                    :all-revisions :shapefile :simplify-shapefile}
    :multiple-value #{:short-name :instrument :instrument-h :two-d-coordinate-system-name
                      :collection-data-type :consortium :project :project-h :entry-id :version :provider
                      :entry-title :doi :native-id :platform :platform-h :processing-level-id
                      :processing-level-id-h :sensor :data-center-h :measurement :variable-name
                      :variable-concept-id :variable-native-id :author :service-name :service-type
                      :service-concept-id :tool-name :tool-type :tool-concept-id :granule-data-format
                      :granule-data-format-h :horizontal-data-resolution-range :latency}
    :always-case-sensitive #{:echo-collection-id}
    :disallow-pattern #{:echo-collection-id}}))

(defmethod cpv/params-config :granule
  [_]
  (cpv/merge-params-config
   cpv/basic-params-config
   {:single-value #{:echo-compatible :include-facets :shapefile :simplify-shapefile}
    :multiple-value #{:granule-ur :short-name :instrument :collection-concept-id
                      :producer-granule-id :project :version :native-id :provider :entry-title
                      :platform :sensor :feature-id :crid-id :cycle}
    :always-case-sensitive #{:echo-granule-id}
    :disallow-pattern #{:echo-granule-id}}))

(defmethod cpv/params-config :tag
  [_]
  (cpv/merge-params-config
   cpv/basic-params-config
   {:single-value #{}
    :multiple-value #{:tag-key :originator-id}
    :always-case-sensitive #{}
    :disallow-pattern #{}}))

;; CMR-4408 measurement is listed as a parameter here, but is currently only a placeholder.
(defmethod cpv/params-config :variable
  [_]
  (cpv/merge-params-config
   cpv/basic-params-config
   {:single-value #{:keyword :all-revisions}
    :multiple-value #{:name :variable-name :alias :full-path :measurement :instrument :provider :native-id :concept-id}
    :always-case-sensitive #{}
    :disallow-pattern #{}}))

(defmethod cpv/params-config :service
  [_]
  (cpv/merge-params-config
   cpv/basic-params-config
   {:single-value #{:keyword :all-revisions}
    :multiple-value #{:name :type :provider :native-id :concept-id}
    :always-case-sensitive #{}
    :disallow-pattern #{}}))

(defmethod cpv/params-config :tool
  [_]
  (cpv/merge-params-config
   cpv/basic-params-config
   {:single-value #{:keyword :all-revisions}
    :multiple-value #{:name :provider :native-id :concept-id}
    :always-case-sensitive #{}
    :disallow-pattern #{}}))

(defmethod cpv/params-config :subscription
  [_]
  (cpv/merge-params-config
   cpv/basic-params-config
   {:single-value #{:keyword :all-revisions}
    :multiple-value #{:name :subscription-name :subscriber-id :collection-concept-id :provider :native-id :concept-id}
    :always-case-sensitive #{}
    :disallow-pattern #{}}))

(defmethod cpv/params-config :autocomplete
  [_]
  (cpv/merge-params-config
   cpv/basic-params-config
   {:single-value #{}
    :multiple-value #{}
    :always-case-sensitive #{}
    :disallow-pattern #{}}))

(def exclude-params
  "Map of concept-type to parameters which can be used to exclude items from results."
  {:collection #{:tag-key}
   :granule #{:concept-id}})

(def exclude-plus-or-option #{:exclude-collection :or :exclude-boundary})
(def exclude-plus-and-or-option #{:exclude-boundary :and :or})
(def spatial-options #{:ignore-br :every})
(def highlights-option #{:begin-tag :end-tag :snippet-length :num-snippets})

(defmethod cpv/valid-parameter-options :collection
  [_]
  {:archive-center cpv/string-param-options
   :attribute exclude-plus-or-option
   :author cpv/string-plus-and-options
   :bounding-box cpv/and-or-option
   :campaign cpv/string-plus-and-options
   :circle cpv/and-or-option
   :collection-data-type cpv/string-param-options
   :created-at cpv/and-option
   :data-center cpv/string-plus-and-options
   :data-center-h cpv/string-plus-and-options
   :dataset-id cpv/pattern-option
   :doi cpv/string-plus-and-options
   :entry-id cpv/string-plus-and-options
   :entry-title cpv/string-plus-and-options
   :granule-data-format cpv/string-plus-and-options
   :granule-data-format-h cpv/string-plus-and-options
   :grid cpv/string-param-options
   :highlights highlights-option
   :instrument cpv/string-plus-and-options
   :instrument-h cpv/string-plus-and-options
   :keyword cpv/pattern-option
   :line cpv/and-or-option
   :native-id cpv/string-param-options
   :platform cpv/string-plus-and-options
   :platform-h cpv/string-plus-and-options
   :platforms cpv/string-plus-or-options ;; for facet v2 apply links
   :platforms-h cpv/string-plus-or-options ;; for facet v2 apply links
   :point cpv/and-or-option
   :polygon cpv/and-or-option
   :project cpv/string-plus-and-options
   :project-h cpv/string-plus-and-options
   :consortium cpv/string-plus-and-options
   :latency cpv/string-param-options
   :provider cpv/string-param-options
   :revision-date cpv/and-option
   :science-keywords cpv/string-plus-or-options
   :science-keywords-h cpv/string-plus-or-options
   :sensor cpv/string-plus-and-options
   :short-name cpv/string-plus-and-options
   :simplify-shapefile cpv/string-param-options
   :spatial cpv/and-or-option
   :spatial-keyword cpv/string-plus-and-options
   :temporal (conj exclude-plus-and-or-option :limit-to-granules)
   :two-d-coordinate-system cpv/string-param-options
   :version cpv/string-param-options

   ;; Tag related parameters
   :tag-key cpv/pattern-option
   :tag-data cpv/pattern-option
   :tag-originator-id cpv/pattern-option

   ;; Variable related parameters
   :variable-name cpv/string-plus-and-options
   :variable-concept-id cpv/and-option
   :variable-native-id cpv/string-plus-and-options
   :measurement cpv/string-plus-and-options
   :variables-h cpv/string-plus-or-options

   ;; service related parameters
   :service-name cpv/string-plus-and-options
   :service-type cpv/string-param-options
   :service-concept-id cpv/and-option

   ;; tool related parameters
   :tool-name cpv/string-plus-and-options
   :tool-type cpv/string-param-options
   :tool-concept-id cpv/and-option})

(defmethod cpv/valid-parameter-options :granule
  [_]
  {:attribute exclude-plus-or-option
   :campaign cpv/string-plus-and-options
   :collection-concept-id cpv/pattern-option
   :collection-data-type cpv/string-param-options
   :created-at cpv/and-option
   :crid-id cpv/string-plus-and-options
   :data-center cpv/string-plus-and-options
   :dataset-id cpv/pattern-option
   :day-night cpv/string-param-options
   :entry-title cpv/string-plus-and-options
   :feature-id cpv/string-plus-and-options
   :granule-ur cpv/string-param-options
   :grid cpv/string-param-options
   :instrument cpv/string-plus-and-exclude-collection-options
   :native-id cpv/string-param-options
   :passes cpv/and-option
   :platform cpv/string-plus-and-exclude-collection-options
   :producer-granule-id cpv/string-param-options
   :production-date cpv/and-option
   :project cpv/string-plus-and-options
   :provider cpv/string-param-options
   :readable-granule-name cpv/string-plus-and-options
   :revision-date cpv/and-option
   :science-keywords cpv/string-plus-or-options
   :sensor cpv/string-plus-and-exclude-collection-options
   :short-name cpv/string-plus-and-options
   :simplify-shapefile cpv/string-param-options
   :spatial spatial-options
   :spatial-keyword cpv/string-plus-and-options
   :temporal exclude-plus-and-or-option
   :two-d-coordinate-system cpv/string-param-options
   :version cpv/string-param-options})

(defmethod cpv/valid-parameter-options :tag
  [_]
  {:tag-key cpv/pattern-option
   :originator-id cpv/pattern-option})

(defmethod cpv/valid-parameter-options :variable
  [_]
  {:name cpv/string-param-options ;; name is the alias to variable-name
   :variable-name cpv/string-param-options
   :full-path cpv/string-param-options
   :measurement cpv/string-param-options
   :native-id cpv/string-param-options
   :provider cpv/string-param-options
   :measurement-identifiers cpv/string-plus-or-options})

(defmethod cpv/valid-parameter-options :service
  [_]
  {:name cpv/string-param-options
   :type cpv/string-param-options
   :native-id cpv/string-param-options
   :provider cpv/string-param-options})

(defmethod cpv/valid-parameter-options :tool
  [_]
  {:name cpv/string-param-options
   :native-id cpv/string-param-options
   :provider cpv/string-param-options})

(defmethod cpv/valid-parameter-options :subscription
  [_]
  {:name cpv/string-param-options
   :subscription-name cpv/string-param-options
   :subscriber-id cpv/string-param-options
   :type cpv/string-param-options
   :collection-concept-id cpv/string-param-options
   :native-id cpv/string-param-options
   :provider cpv/string-param-options})

(defmethod cpv/valid-parameter-options :autocomplete
  [_]
  {:q cpv/string-param-options
   :type cpv/string-plus-or-options})

(defmethod cpv/valid-query-level-params :collection
  [_]
  #{:include-granule-counts :include-has-granules :include-facets :hierarchical-facets
    :include-highlights :include-tags :all-revisions :echo-compatible :boosts :facets-size})

(defmethod cpv/valid-query-level-options :collection
  [_]
  #{:highlights :spatial})

(defmethod cpv/valid-query-level-options :granule
  [_]
  #{:spatial})

(defmethod cpv/valid-query-level-params :granule
  [_]
  #{:echo-compatible :include-facets :spatial})

(defmethod cpv/valid-query-level-params :variable
  [_]
  #{:all-revisions})

(defmethod cpv/valid-query-level-params :service
  [_]
  #{:all-revisions})

(defmethod cpv/valid-query-level-params :tool
  [_]
  #{:all-revisions})

(defmethod cpv/valid-query-level-params :subscription
  [_]
  #{:all-revisions})

(defmethod cpv/valid-sort-keys :collection
  [_]
  #{:short-name
    :entry-title
    :entry-id
    :dataset-id
    :start-date
    :end-date
    :provider
    :platform
    :instrument
    :sensor
    :revision-date
    :score
    :has-granules
    :has-granules-or-cwic
    :has-granules-or-opensearch
    :usage-relevancy-score
    :ongoing})

(defmethod cpv/valid-sort-keys :granule
  [_]
  #{:granule-ur
    :producer-granule-id
    :readable-granule-name
    :start-date
    :end-date
    :entry-title
    :dataset-id
    :short-name
    :version
    :provider
    :data-size
    :cloud-cover
    :campaign
    :platform
    :instrument
    :sensor
    :project
    :day-night
    :downloadable
    :browsable
    :revision-date})

(defmethod cpv/valid-sort-keys :variable
  [_]
  #{:name
    :long-name
    :revision-date
    :provider})

(defmethod cpv/valid-sort-keys :service
  [_]
  #{:name
    :type
    :long-name
    :revision-date
    :provider})

(defmethod cpv/valid-sort-keys :tool
  [_]
  #{:name
    :long-name
    :revision-date
    :provider})

(defmethod cpv/valid-sort-keys :subscription
  [_]
  #{:name
    :subscription-name
    :collection-concept-id
    :provider})

(defn- day-valid?
  "Validates if the given day in temporal is an integer between 1 and 366 inclusive"
  [day tag]
  (when-not (s/blank? day)
    (try
      (let [num (Integer/parseInt day)]
        (when (or (< num 1) (> num 366))
          [(format "%s [%s] must be an integer between 1 and 366" tag day)]))
      (catch NumberFormatException e
        [(format "%s [%s] must be an integer between 1 and 366" tag day)]))))

(defn- temporal-input-format-valid?
  "Validates the temporal input format and makes sure it was passed in as a valid string not a map"
  [temporal]
  (every? string? temporal))

(defn- temporal-format-validation
  "Validates that temporal datetime parameter conforms to the :date-time-no-ms format,
  start-day and end-day are integer between 1 and 366"
  [concept-type params]
  (when-let [temporal (:temporal params)]
    (let [temporal (if (sequential? temporal)
                     temporal
                     [temporal])]
      (if (temporal-input-format-valid? temporal)
        (mapcat
         (fn [value]
           (if (re-find #"/" value)
             (let [[iso-range start-day end-day] (map s/trim (s/split value #","))]
               (concat
                (cpv/validate-date-time-range nil)
                (day-valid? start-day "temporal_start_day")
                (day-valid? end-day "temporal_end_day")))
             (let [[start-date end-date start-day end-day] (map s/trim (s/split value #","))]
               (concat
                (cpv/validate-date-time "temporal start" start-date)
                (cpv/validate-date-time "temporal end" end-date)
                (day-valid? start-day "temporal_start_day")
                (day-valid? end-day "temporal_end_day")))))
         temporal)
        ["The valid format for temporal parameters are temporal[]=startdate,stopdate and temporal[]=startdate,stopdate,startday,endday"]))))

(defn- updated-since-validation
  "Validates updated-since parameter conforms to formats in data-time-parser NS"
  [concept-type params]
  (when-let [param-value (:updated-since params)]
    (if (and (sequential? (:updated-since params)) (> (count (:updated-since params)) 1))
      ["Search not allowed with multiple updated_since values"]
      (let [updated-since-val (if (sequential? param-value) (first param-value) param-value)]
        (cpv/validate-date-time "updated_since" updated-since-val)))))

(defn- tag-data-validation
  "Validates tag-data parameter must be a map"
  [concept-type params]
  (when-let [param-value (:tag-data params)]
    (if (map? param-value)
      ;; validate that tag-value cannot be empty
      (when-let [empty-value-keys (seq (map first (filter #(empty? (second %)) param-value)))]
        [(format "Tag value cannot be empty for tag data search, but were for tag keys [%s]."
                 (s/join ", " (map name empty-value-keys)))])
      ["Tag data search must be in the form of tag-data[tag-key]=tag-value"])))

(defn- validate-multi-date-range
  "Validates a given date range that may contain several date ranges."
  [date-range param-name]
  (mapcat
   (fn [value]
     (let [parts (map s/trim (s/split value #"," -1))
           [start-date end-date] parts]
       (if (> (count parts) 2)
         [(format "Too many commas in %s %s" param-name value)]
         (concat
          (cpv/validate-date-time (str param-name " start") start-date)
          (cpv/validate-date-time (str param-name " end") end-date)))))
   date-range))

(defn- sequential-multi-date-validation
  "Validates the given date parameter contains valid date time strings."
  [concept-type params param-key]
  (when-let [value (param-key params)]
    (let [value (if (sequential? value)
                  value
                  [value])]
      (validate-multi-date-range value (name param-key)))))

(defn- revision-date-validation
  "Validates that revision date parameter contains valid date time strings."
  [concept-type params]
  (sequential-multi-date-validation concept-type params :revision-date))

(defn- production-date-validation
  "Validates that production date parameter contains valid date time strings."
  [concept-type params]
  (sequential-multi-date-validation concept-type params :production-date))

(defn- created-at-validation
  "Validates that created-at parameter contains valid datetime strings."
  [concept-type params]
  (sequential-multi-date-validation concept-type params :created-at))

(defn- attribute-validation
  [concept-type params]
  (when-let [attributes (:attribute params)]
    (if (sequential? attributes)
      (mapcat #(-> % attrib/parse-value :errors) attributes)
      [(attrib-msg/attributes-must-be-sequence-msg)])))

(defn- science-keywords-validation-for-field
  "Performs science keywords subfield validation."
  [field concept-type params]
  (validation-util/nested-field-validation-for-subfield
   field concept-type params (msg/science-keyword-invalid-format-msg)))

(defn- variables-validation
  "Validates the variables-h search parameters are in the format of e.g.
   variables-h[0][measurement]=value."
  [concept-type params]
  (validation-util/nested-field-validation-for-subfield
   :variables-h concept-type params (msg/variable-invalid-format-msg)))

(defn- temporal-facets-subfields-validation
  "Performs temporal facets subfield validation."
  [concept-type params]
  (validation-util/nested-field-validation-for-subfield
   :temporal-facet concept-type params (msg/temporal-facets-invalid-format-msg)))

(defn- measurement-identifiers-validation
  "Validates the measurement_identifiers search parameters are in the format of e.g.
   measurement_identifiers[0][contextmedium]=value."
  [concept-type params]
  (validation-util/nested-field-validation-for-subfield
   :measurement-identifiers concept-type params (msg/measurement-identifiers-invalid-format-msg)))

(def max-value-for-date-field
  "Defines the maximum valid value for each date field."
  {:year 9999
   :month 12
   :day 31})

(defn- valid-value-for-date-field?
  "Returns true if the provided value is valid for the given date field and false otherwise."
  [value field]
  (let [parsed-value (try
                       (Long. value)
                       (catch Exception e
                         nil))]
    (and (not (nil? parsed-value))
         (<= 1 parsed-value (max-value-for-date-field field)))))

(defn- temporal-facet-date-validation
  "Validates that the given temporal date field in all temporal-facet parameters are valid."
  [date-field concept-type params]
  (when-let [param-values (:temporal-facet params)]
    (when (map? param-values)
      (let [temporal-facet-maps (vals param-values)
            values (keep date-field temporal-facet-maps)]
        (reduce
         (fn [errors value]
           (if-not (valid-value-for-date-field? value date-field)
             (conj errors (format (str "%s [%s] within [temporal_facet] is not a valid %s. "
                                       "%ss must be between 1 and %d.")
                                  (s/capitalize (name date-field))
                                  value
                                  (name date-field)
                                  (s/capitalize (name date-field))
                                  (max-value-for-date-field date-field)))
             errors))
         []
         values)))))

(defn- temporal-facet-year-validation
  "Validates that the years provided in all temporal-facet parameters are valid."
  [concept-type params]
  (temporal-facet-date-validation :year concept-type params))

(defn- temporal-facet-month-validation
  "Validates that the months provided in all temporal-facet parameters are valid."
  [concept-type params]
  (temporal-facet-date-validation :month concept-type params))

(defn- temporal-facet-day-validation
  "Validates that the days provided in all temporal-facet parameters are valid."
  [concept-type params]
  (temporal-facet-date-validation :day concept-type params))

;; This method is for processing legacy numeric ranges in the form of
;; param_nam[value], param_name[minValue], and param_name[maxValue].
;; It simply validates that the provided values are numbers and that
;; at least one is present.
(defn- validate-legacy-numeric-range-param
  "Validates a numeric parameter in the form of a map, appending the message argument
  to the error array on failure."
  [param-map error-message-fn & args]
  (let [{:keys [value min-value max-value]} param-map]
    (try
      (when value
        (Double. value))
      (when min-value
        (Double. min-value))
      (when max-value
        (Double. max-value))
      (when-not (or value min-value max-value)
        (if error-message-fn
          [(apply error-message-fn args)]
          [(d-msg/nil-min-max-msg)]))
      (catch NumberFormatException e
        [(apply error-message-fn args)]))))

(defn- cloud-cover-validation
  "Validates cloud cover range values are numeric"
  [concept-type params]
  (when-let [cloud-cover (:cloud-cover params)]
    (if (string? cloud-cover)
      (cpv/validate-numeric-range-param cloud-cover nil)
      (validate-legacy-numeric-range-param cloud-cover nil))))

(defn- orbit-number-validation
  "Validates that the orbital number is either a single number or a range in the format
  start,stop, or in the catlog-rest style orbit_number[value], orbit_number[minValue],
  orbit_number[maxValue]."
  [concept-type params]
  (when-let [orbit-number-param (:orbit-number params)]
    (if (string? orbit-number-param)
      (cpv/validate-numeric-range-param orbit-number-param on-msg/invalid-orbit-number-msg)
      (validate-legacy-numeric-range-param orbit-number-param on-msg/invalid-orbit-number-msg))))

(defn- equator-crossing-longitude-validation
  "Validates that the equator-crossing-longitude parameter is a single number or
  a valid range string."
  [concept-type params]
  (when-let [equator-crossing-longitude (:equator-crossing-longitude params)]
    (if (string? equator-crossing-longitude)
      (cpv/validate-numeric-range-param equator-crossing-longitude nil)
      (validate-legacy-numeric-range-param equator-crossing-longitude
                                           on-msg/non-numeric-equator-crossing-longitude-parameter))))

(defn- equator-crossing-date-validation
  "Validates that the equator_crossing_date parameter is a valid date range string."
  [concept-type params]
  (when-let [equator-crossing-date (:equator-crossing-date params)]
    (parser/date-time-range-string-validation equator-crossing-date)))

(defn- exclude-validation
  "Validates that the key(s) supplied in 'exclude' param value are in exclude-params set"
  [concept-type params]
  (when-let [exclude-kv (:exclude params)]
    (let [invalid-exclude-params (set/difference (set (keys exclude-kv))
                                                 (exclude-params concept-type))]
      (if (empty? invalid-exclude-params)
        (let [exclude-values (flatten (vals exclude-kv))]
          (if (every? string? exclude-values)
            (when (some #(.startsWith % "C") exclude-values)
              [(str "Exclude collection is not supported, " exclude-kv)])
            ["Invalid format for exclude parameter, must be in the format of exclude[name][]=value"]))
        [(msg/invalid-exclude-param-msg invalid-exclude-params)]))))

(defn- boolean-value-validation
  "Validates that all of the boolean parameters have values of true, false or unset."
  [concept-type params]
  (let [bool-params (select-keys params [:downloadable :browsable :include-granule-counts
                                         :include-has-granules :has-granules :hierarchical-facets
                                         :include-highlights :all-revisions :has-opendap-url
                                         :simplify-shapefile :cloud-hosted :standard-product])]
    (mapcat
      (fn [[param value]]
        (when-not (contains? #{"true" "false" "unset"} (when value (s/lower-case value)))
          [(format "Parameter %s must take value of true, false, or unset, but was [%s]"
                   (csk/->snake_case_string param) value)]))
      bool-params)))

(defn- collection-include-facets-validation
  "Validates that the include_facets parameter has a value of true, false or v2."
  [concept-type params]
  (when-let [include-facets (:include-facets params)]
    (when-not (contains? #{"true" "false" "v2"} (s/lower-case include-facets))
      [(format "Collection parameter include_facets must take value of true, false, or v2, but was [%s]"
               include-facets)])))

(defn- not-all-positive-integer-values?
  "Returns the first value in string-values that can not be converted to positive integer."
  [string-values]
  (some #(when-not (and (integer? %) (< 0 %)) %)
        (map #(if (and (not (sequential? %))
                       (not (clojure.string/blank? %)))
                (read-string %)
                ;; not returning % here because of the case when the only non-integer value is nil.
                "some-string")
             string-values)))

(defn- collection-facets-size-validation
  "Validates that the facets-size parameter has a value positive integer value."
  [concept-type params]
  (when-let [facets-size (:facets-size params)]
    (when (or (not (map? facets-size))
              (not-all-positive-integer-values? (vals facets-size)))
      [(str "Collection parameter facets_size needs to be passed in like "
            "facets_size[platform]=n1&facets_size[instrument]=n2 with n1 and n2 being "
            "a positive integer, which will be translated into a map with positive integer string values "
            "like {:platform \"1\" :instrument \"2\"} but was ["
            facets-size "].")])))

(defn- no-facets-size-without-include-facets-v2
  "Validates that the include-facets parameter is set to v2 if facets-size is set."
  [concept-type params]
  (when (and (:facets-size params)
             (not= "v2" (:include-facets params)))
    ["facets_size option is not allowed unless the include_facets is v2."]))

(defn- granule-include-facets-validation
  "Validates that the include_facets parameter has a value of v2."
  [concept-type params]
  (when-let [include-facets (:include-facets params)]
    (when-not (= "v2" (s/lower-case include-facets))
      [(format "Granule parameter include_facets only supports the value v2, but was [%s]"
               include-facets)])))

(defn- spatial-validation
  "Validate a geometry of the given type in the params"
  [params spatial-type]
  (when-let [spatial-param (spatial-type params)]
    (mapcat #(:errors (spatial-codec/url-decode spatial-type %)) (flatten [spatial-param]))))

(defn- polygon-validation
  ([params] (polygon-validation nil params))
  ([_ params] (spatial-validation params :polygon)))

(defn- bounding-box-validation
  ([params] (bounding-box-validation nil params))
  ([_ params] (spatial-validation params :bounding-box)))

(defn- point-validation
  ([params] (point-validation nil params))
  ([_ params] (spatial-validation params :point)))

(defn- line-validation
  ([params] (line-validation nil params))
  ([_ params] (spatial-validation params :line)))

(defn- collection-concept-id-validation
  "Validates the collection-concept-id(s)"
  [concept-type params]
  ;; collection-concept-ids can be either a vector or a single value.
  (when-let [c-concept-ids (util/seqify (:collection-concept-id params))]
    (mapcat (partial cc/concept-id-validation :collection-concept-id) c-concept-ids)))

(defn- timeline-start-date-validation
  "Validates the timeline start date parameter"
  [concept-type params]
  (let [start-date (:start-date params)]
    (if-not (s/blank? start-date)
      (cpv/validate-date-time "Timeline parameter start_date" start-date)
      ["start_date is a required parameter for timeline searches"])))

(defn- timeline-end-date-validation
  "Validates the timeline end date parameter"
  [concept-type params]
  (let [end-date (:end-date params)]
    (if-not (s/blank? end-date)
      (cpv/validate-date-time "Timeline parameter end_date" end-date)
      ["end_date is a required parameter for timeline searches"])))

(defn- timeline-range-validation
  "Validates the start date is before the end date"
  [concept-type params]
  (try
    (let [{:keys [start-date end-date]} params]
      (when (and start-date end-date
                 (t/after? (dt-parser/parse-datetime start-date)
                           (dt-parser/parse-datetime end-date)))
        [(format "start_date [%s] must be before the end_date [%s]"
                 start-date end-date)]))
    (catch ExceptionInfo e
      ;; The date times are invalid. This error should be handled by other validations
      [])))

(defn- no-highlight-options-without-highlights-validation
  "Validates that the include-highlights parameter is set to true if any of the highlights
  options params are set."
  [concept-type params]
  (when (and (get-in params [:options :highlights])
             (not= "true" (:include-highlights params)))
    ["Highlights options are not allowed unless the include-highlights is true."]))

(defn- highlights-numeric-options-validation
  "Validates that the highlights option (if present) is an integer greater than zero."
  [concept-type params]
  (keep
   (fn [param]
     (when-let [value (get-in params [:options :highlights param])]
       (try
         (let [int-value (Integer/parseInt value)]
           (when (< int-value 1)
             (format "%s option [%d] for highlights must be an integer greater than 0."
                     (csk/->snake_case_string param) int-value)))
         (catch NumberFormatException e
           (format
            "%s option [%s] for highlights is not a valid integer."
            (csk/->snake_case_string param) value)))))
   [:snippet-length :num-snippets]))

(defn- include-tags-parameter-validation
  "Validates parameters against result format."
  [concept-type params]
  (concat
   (when (and (not (#{:json :atom :echo10 :dif :dif10 :iso19115 :native} (:result-format params)))
              (not (s/blank? (:include-tags params))))
     [(format "Parameter [include_tags] is not supported for %s format search."
              (name (cqm/base-result-format (:result-format params))))])))

(def valid-timeline-intervals
  "A list of the valid values for timeline intervals."
  #{"year" "month" "day" "hour" "minute" "second"})

(defn- timeline-interval-validation
  "Validates the timeline interval parameter"
  [concept-type params]
  (if-let [interval (:interval params)]
    (when-not (valid-timeline-intervals interval)
      [(str "Timeline interval is a required parameter for timeline search and must be one of"
            " year, month, day, hour, minute, or second.")])
    ["interval is a required parameter for timeline searches"]))

(defn- boosts-validation
  "Validates that all the provided fields in the boosts parameter are valid and that the values
  are numeric."
  [concept-type params]
  (let [boosts (:boosts params)]
    (keep (fn [[field value]]
            (if (or (field k2e/default-boosts)
                    (= field :provider))
              (when-not (util/numeric-string? value)
                (format "Relevance boost value [%s] for field [%s] is not a number."
                        (csk/->snake_case_string value) (csk/->snake_case_string field)))
              (when-not (= field :include-defaults)
                (format "Cannot set relevance boost on field [%s]." (csk/->snake_case_string field)))))
          (seq boosts))))

(def ^:private valid-shapefile-formats
  "Valid shapefile formats"
  #{mt/shapefile mt/geojson mt/kml})

(defn shapefile-format-validation
  "Validates that the shapefile format value is one of the accepted formats"
  [concept-type params]
  (when-let [shapefile-format (get-in params [:shapefile :content-type])]
    (when (not (contains? valid-shapefile-formats shapefile-format))
      [(format "Shapefile format [%s] is not supported. It must be one of %s"
               shapefile-format
               (util/human-join (vec valid-shapefile-formats) "," "or"))])))

(defn- validate-circle-value
  "Validates the given circle value"
  [value]
  (let [circle (spatial-codec/url-decode :circle value)]
    (if-let [err-msgs (:errors circle)]
      err-msgs
      (sv/validate circle))))

(defn- circle-values-validation
  "Validates that the circle values are valid"
  [concept-type params]
  (when-let [circle-value (:circle params)]
    (let [circle-values (if (sequential? circle-value)
                          circle-value
                          [circle-value])]
      (mapcat validate-circle-value circle-values))))

(def parameter-validations
  "Lists of parameter validation functions by concept type"
  {:collection (concat
                cpv/common-validations
                [boosts-validation
                 temporal-format-validation
                 updated-since-validation
                 revision-date-validation
                 created-at-validation
                 orbit-number-validation
                 equator-crossing-longitude-validation
                 equator-crossing-date-validation
                 cloud-cover-validation
                 attribute-validation
                 (partial science-keywords-validation-for-field :science-keywords)
                 (partial science-keywords-validation-for-field :science-keywords-h)
                 variables-validation
                 exclude-validation
                 boolean-value-validation
                 polygon-validation
                 bounding-box-validation
                 point-validation
                 line-validation
                 tag-data-validation
                 no-highlight-options-without-highlights-validation
                 highlights-numeric-options-validation
                 include-tags-parameter-validation
                 collection-include-facets-validation
                 no-facets-size-without-include-facets-v2
                 collection-facets-size-validation
                 circle-values-validation
                 shapefile-format-validation])
   :granule (concat
             cpv/common-validations
             [temporal-format-validation
              created-at-validation
              updated-since-validation
              production-date-validation
              revision-date-validation
              orbit-number-validation
              equator-crossing-longitude-validation
              equator-crossing-date-validation
              cloud-cover-validation
              track/cycle-pass-tile-validation
              attribute-validation
              (partial science-keywords-validation-for-field :science-keywords)
              exclude-validation
              boolean-value-validation
              polygon-validation
              bounding-box-validation
              point-validation
              line-validation
              collection-concept-id-validation
              granule-include-facets-validation
              temporal-facets-subfields-validation
              temporal-facet-year-validation
              temporal-facet-month-validation
              temporal-facet-day-validation
              circle-values-validation
              shapefile-format-validation])
   :tag cpv/common-validations
   :variable (concat cpv/common-validations
                     [boolean-value-validation
                      measurement-identifiers-validation])
   :service (concat cpv/common-validations
                    [boolean-value-validation])
   :tool (concat cpv/common-validations
                 [boolean-value-validation])
   :autocomplete cpv/common-validations
   :subscription (concat cpv/common-validations
                         [boolean-value-validation])})

(def standard-query-parameter-validations
  "A list of functions that can validate the query parameters passed in with an AQL or JSON search.
  They all accept parameters as an argument and return a list of errors."
  [cpv/single-value-validation
   cpv/page-size-validation
   cpv/page-num-validation
   cpv/paging-depth-validation
   boosts-validation
   cpv/sort-key-validation
   cpv/unrecognized-standard-query-params-validation])

(def timeline-parameter-validations
  "A list of function that can validate timeline query parameters. It will only validate the timeline
  parameters specifically. Parameter validation on the "
  [timeline-start-date-validation
   timeline-end-date-validation
   timeline-interval-validation
   timeline-range-validation])

(def parameter-data-type-validations
  "Validations of the data type of various parameters, used to ensure the data is the correct
  shape before we manipulate it further."
  [(partial cpv/validate-map [:options])
   (partial cpv/validate-map [:options :entry-title])
   (partial cpv/validate-map [:options :platform])
   (partial cpv/validate-map [:platforms]) ;; for facet v2 apply links
   (partial cpv/validate-all-map-values cpv/validate-map [:platforms-h]) ;; for facet v2 apply links
   (partial cpv/validate-map [:options :instrument])
   (partial cpv/validate-map [:options :sensor])
   (partial cpv/validate-map [:options :project])
   (partial cpv/validate-map [:options :attribute])
   (partial cpv/validate-map [:exclude])
   (partial cpv/validate-map [:science-keywords])
   (partial cpv/validate-all-map-values cpv/validate-map [:science-keywords])
   (partial cpv/validate-map [:science-keywords-h])
   (partial cpv/validate-all-map-values cpv/validate-map [:science-keywords-h])
   (partial cpv/validate-map [:measurement-identifiers])
   (partial cpv/validate-all-map-values cpv/validate-map [:measurement-identifiers])])

(defn- validate-parameter-data-types
  "Validates data types of parameters.  Unlike other validations, this returns a tuple of
  [safe-params errors] where errors contains the usual list of errors and safe-params
  contains only params whose data type is correct.  Dissoc'ing invalid data types from
  the list allows other validations to make assumptions about their shapes / types."
  [params]
  (cpv/apply-type-validations params parameter-data-type-validations))

(defn validate-parameters
  "Validates parameters. Throws exceptions to send to the user. Returns parameters if validation
  was successful so it can be chained with other calls."
  [concept-type params]
  (let [[safe-params type-errors] (validate-parameter-data-types params)]
    (cpv/validate-parameters
     concept-type safe-params (parameter-validations concept-type) type-errors))
  params)

(defn validate-standard-query-parameters
  "Validates the query parameters passed in with an AQL or JSON search.
  Throws exceptions to send to the user. Returns parameters if validation
  was successful so it can be chained with other calls."
  [concept-type params]
  (cpv/validate-parameters concept-type params standard-query-parameter-validations)
  params)

(defn validate-timeline-parameters
  "Validates the query parameters passed in with a timeline search.
  Throws exceptions to send to the user. Returns parameters if validation
  was successful so it can be chained with other calls."
  [params]
  (let [[safe-params type-errors] (validate-parameter-data-types params)
        timeline-params (select-keys safe-params [:interval :start-date :end-date])
        regular-params (dissoc safe-params :interval :start-date :end-date)
        errors (concat type-errors
                       (mapcat #(% :granule regular-params) (parameter-validations :granule))
                       (mapcat #(% :granule timeline-params) timeline-parameter-validations))]
    (when (seq errors)
      (errors/throw-service-errors :bad-request errors)))
  params)

(def valid-tile-search-params
  "Valid parameters for tile search"
  #{:bounding-box
    :line
    :point
    :polygon})

(defn- unrecognized-tile-params-validation
  "Validates that no invalid parameters were supplied to tile search"
  [params]
  (map #(format "Parameter [%s] was not recognized." (csk/->snake_case_string %))
       (set/difference (set (keys params)) valid-tile-search-params)))

(defn validate-tile-parameters
  "Validates the query parameters passed in with a tile search. Throws exceptions to send
  to the user if a validation fails. Returns parameters if validation is successful."
  [params]
  (let [errors (mapcat #(% params)
                       [unrecognized-tile-params-validation
                        polygon-validation
                        bounding-box-validation
                        point-validation
                        line-validation])]
    (when (seq errors)
      (errors/throw-service-errors :bad-request errors)))
  params)

(def ^:private valid-deleted-collections-search-params
  "Valid parameters for deleted collections search"
  #{:revision-date
    :revision_date
    :result-format})

(def ^:private valid-deleted-granules-search-params
  "Valid parameters for deleted granules search"
  #{:revision-date
    :provider
    :parent-collection-id
    :result-format})

(defn- unrecognized-deleted-colls-params-validation
  "Validates that no invalid parameters were supplied to deleted collections search"
  [params]
  (map #(format "Parameter [%s] was not recognized." (csk/->snake_case_string %))
       (set/difference (set (keys params)) valid-deleted-collections-search-params)))

(defn- deleted-colls-result-format-validation
  "Validates that the only result format support by deleted collections search is :xml"
  [params]
  (when-not (= :xml (:result-format params))
    [(format (str "Result format [%s] is not supported by deleted collections search. "
                  "The only format that is supported is xml.")
             (name (:result-format params)))]))

(defn- validate-deleted-colls-revision-date-str
  [revision-date]
  (when (.contains revision-date ",")
    [(format (str "Invalid format for revision date, only a starting date is allowed "
                  "for deleted collections search, but was [%s]") revision-date)]))

(defn- deleted-colls-revision-date-validation
  "Validates that revision date can only have a start date for deleted collections search"
  [params]
  (when-let [revision-date (or (:revision-date params)
                               (:revision_date params))]
    (if (sequential? revision-date)
      (if (> (count revision-date) 1)
        [(format "Only one revision date is allowed, but %s were provided." (count revision-date))]
        (validate-deleted-colls-revision-date-str (first revision-date)))
      (validate-deleted-colls-revision-date-str revision-date))))

(defn validate-deleted-collections-params
  "Validates the query parameters passed in with deleted collections search.
   Throws exceptions to send to the user if a validation fails."
  [params]
  (let [errors (mapcat #(% params)
                       [unrecognized-deleted-colls-params-validation
                        deleted-colls-result-format-validation
                        deleted-colls-revision-date-validation])]
    (when (seq errors)
      (errors/throw-service-errors :bad-request errors))))

(defn- deleted-grans-result-format-validation
  "Validates that the only result format support by deleted granules search is :json"
  [params]
  (when-not (= :json (:result-format params))
    [(format (str "Result format [%s] is not supported by deleted granules search. "
                  "The only format that is supported is json")
             (name (:result-format params)))]))

(defn- unrecognized-deleted-grans-params-validation
  "Validates that no invalid parameters were supplied to deleted granules search"
  [params]
  (map #(format "Parameter [%s] was not recognized." (csk/->snake_case_string %))
       (set/difference (set (keys params)) valid-deleted-granules-search-params)))

(defn- validate-deleted-grans-revision-date-str
  [revision-date]
  (when (.contains revision-date ",")
    [(format (str "Invalid format for revision date, only a starting date is allowed "
                  "for deleted granules search, but was [%s]") revision-date)]))

(defn- deleted-grans-revision-date-validation
  "Validates that deleted time can only have a start date for deleted collections search"
  [params]
  (when-let [revision-date (:revision-date params)]
    (if (sequential? revision-date)
      (if (> (count revision-date) 1)
        [(format "Only one deleted time is allowed, but %s were provided." (count revision-date))]
        (validate-deleted-grans-revision-date-str (first revision-date)))
      (validate-deleted-grans-revision-date-str revision-date))))

(defn- deleted-grans-revision-date-range-validation
  "Validates that deleted time can only have a start date for deleted collections search"
  [params]
  (when-let [revision-date (:revision-date params)]
    (when (t/before? (dt-parser/parse-datetime revision-date) (t/minus- (t/now) (t/days 365)))
      [(format "Revision date must be within one year of today.")])))

(defn- deleted-grans-revision-required-validation
  "Validates that deleted time can only have a start date for deleted collections search"
  [params]
  (let [revision-date (:revision-date params)]
    (when-not (seq revision-date)
      [(format "One revision date is required for deleted granules search.")])))

(defn validate-deleted-granules-params
  "Validates the query parameters passed in with deleted collections search.
   Throws exceptions to send to the user if a validation fails."
  [params]
  (let [errors (mapcat #(% params)
                       [unrecognized-deleted-grans-params-validation
                        deleted-grans-result-format-validation
                        deleted-grans-revision-required-validation
                        deleted-grans-revision-date-range-validation])]
    (when (seq errors)
      (errors/throw-service-errors :bad-request errors))))
