(ns cmr.search.services.parameter-validation
  "Contains functions for validating query parameters"
  (:require [clojure.set :as set]
            [cmr.common.services.errors :as err]
            [cmr.common.services.messages :as c-msg]
            [cmr.common.parameter-parser :as parser]
            [clojure.string :as s]
            [cmr.common.date-time-parser :as dt-parser]
            [cmr.search.services.parameters :as p]
            [cmr.search.services.parameter-converters.attribute :as attrib]
            [cmr.search.services.messages.attribute-messages :as attrib-msg]
            [cmr.search.services.parameter-converters.orbit-number :as on]
            [cmr.search.services.messages.orbit-number-messages :as on-msg]
            [cmr.search.services.messages.common-messages :as msg]
            [camel-snake-kebab :as csk])
  (:import clojure.lang.ExceptionInfo))


(defn- concept-type->valid-param-names
  "A set of the valid parameter names for the given concept-type."
  [concept-type]
  (set (concat
         (keys (get p/concept-param->type concept-type))
         (keys p/param-aliases)
         [:options])))

(defn page-size-validation
  "Validates that the page-size (if present) is a number in the valid range."
  [concept-type params]
  (if-let [page-size (:page-size params)]
    (try
      (let [page-size-i (Integer. page-size)]
        (cond
          (> 1 page-size-i)
          ["page_size must be a number between 1 and 2000"]

          (< 2000 page-size-i)
          ["page_size must be a number between 1 and 2000"]

          :else
          []))
      (catch NumberFormatException e
        ["page_size must be a number between 1 and 2000"]))
    []))

(defn page-num-validation
  "Validates that the page-num (if present) is a number in the valid range."
  [concept-type params]
  (if-let [page-num (:page-num params)]
    (try
      (let [page-num-i (Integer. page-num)]
        (if (> 1 page-num-i)
          ["page_num must be a number greater than or equal to 1"]
          []))
      (catch NumberFormatException e
        ["page_num must be a number greater than or equal to 1"]))
    []))

(def concept-type->valid-sort-keys
  "A map of concept type to sets of valid sort keys"
  {:collection #{:entry-title
                 :dataset-id
                 :start-date
                 :end-date
                 :provider}
   :granule #{:granule-ur
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
              :project}})

(defn sort-key-validation
  "Validates the sort-key parameter if present"
  [concept-type params]
  (if-let [sort-key (:sort-key params)]
    (let [sort-keys (if (sequential? sort-key) sort-key [sort-key])]
      (mapcat (fn [sort-key]
                (let [[_ field] (re-find #"[\-+]?(.*)" sort-key)
                      valid-params (concept-type->valid-sort-keys concept-type)]
                  (when-not (valid-params (keyword field))
                    [(msg/invalid-sort-key (csk/->snake_case_string field ) concept-type)])))
              sort-keys))
    []))


(defn unrecognized-params-validation
  "Validates that no invalid parameters were supplied"
  [concept-type params]
  ;; this test does not apply to page_size or page_num
  (let [params (dissoc params :page-size :page-num :sort-key)]
    (map #(str "Parameter [" (csk/->snake_case_string % )"] was not recognized.")
         (set/difference (set (keys params))
                         (concept-type->valid-param-names concept-type)))))

(defn unrecognized-params-in-options-validation
  "Validates that no invalid parameters names in the options were supplied"
  [concept-type params]
  (if-let [options (:options params)]
    (map #(str "Parameter [" (csk/->snake_case_string %)"] with option was not recognized.")
         (set/difference (set (keys options))
                         (concept-type->valid-param-names concept-type)))
    []))

(defn unrecognized-params-settings-in-options-validation
  "Validates that no invalid parameters names in the options were supplied"
  [concept-type params]
  (if-let [options (:options params)]
    (apply concat
           (map
             (fn [[param settings]]
               (map #(str "Option [" (csk/->snake_case_string %)
                          "] for param [" (csk/->snake_case_string param) "] was not recognized.")
                    (set/difference (set (keys settings)) (set [:ignore-case :pattern :and :or]))))
             options))
    []))

(defn- validate-date-time
  "Validates datetime string is in the given format"
  [dt]
  (try
    (when-not (s/blank? dt)
      (dt-parser/parse-datetime dt))
    []
    (catch ExceptionInfo e
      [(format "temporal datetime is invalid: %s." (first (:errors (ex-data e))))])))

(defn- day-valid?
  "Validates if the given day in temporal is an integer between 1 and 366 inclusive"
  [day tag]
  (if-not (s/blank? day)
    (try
      (let [num (Integer/parseInt day)]
        (when (or (< num 1) (> num 366))
          [(format "%s [%s] must be an integer between 1 and 366" tag day)]))
      (catch NumberFormatException e
        [(format "%s [%s] must be an integer between 1 and 366" tag day)]))
    []))

(defn temporal-format-validation
  "Validates that temporal datetime parameter conforms to the :date-time-no-ms format,
  start-day and end-day are integer between 1 and 366"
  [concept-type params]
  (if-let [temporal (:temporal params)]
    (apply concat
           (map
             (fn [value]
               (let [[start-date end-date start-day end-day] (map s/trim (s/split value #","))]
                 (concat
                   (validate-date-time start-date)
                   (validate-date-time end-date)
                   (day-valid? start-day "temporal_start_day")
                   (day-valid? end-day "temporal_end_day"))))
             temporal))
    []))


(defn updated-since-validation
  "Validates updated-since parameter conforms to formats in data-time-parser NS"
  [concept-type params]
  (if-let [param-value (:updated-since params)]
    (if (and (sequential? (:updated-since params)) (> (count (:updated-since params)) 1))
      [(format "search not allowed with multiple updated_since values s%: " (:updated-since params))]
      (let [updated-since-val (if (sequential? param-value) (first param-value) param-value)]
        (validate-date-time updated-since-val)))
    []))

(defn attribute-validation
  [concept-type params]
  (if-let [attributes (:attribute params)]
    (if (sequential? attributes)
      (mapcat #(-> % attrib/parse-value :errors) attributes)
      [(attrib-msg/attributes-must-be-sequence-msg)])
    []))

(defn- validate-numeric-range-map
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
      (if (or value min-value max-value)
        []
        (if error-message-fn
          [(apply error-message-fn args)]
          [(c-msg/invalid-numeric-range-msg)]))
      (catch NumberFormatException e
        [(apply error-message-fn args)]))))

(defn- validate-numeric-range-string-param
  "Validates a numeric parameter in the form parameter=value or
  parameter=min,max, appending the message argument to the error array on failure."
  [param error-message-fn & args]
  (let [errors (parser/numeric-range-string-validation param)]
    (if-not (empty? errors)
      (if error-message-fn
        (concat [(apply error-message-fn args)] errors)
        errors)
      [])))

(defn cloud-cover-validation
  "Validates cloud cover range values are numeric"
  [concept-type params]
  (if-let [cloud-cover (:cloud-cover params)]
    (if (string? cloud-cover)
      (validate-numeric-range-string-param cloud-cover nil)
      (validate-numeric-range-map cloud-cover nil))
    []))

(defn orbit-number-validation
  "Validates that the orbital number is either a single number or a range in the format
  start,stop, or in the catlog-rest style orbit_number[value], orbit_number[minValue],
  orbit_number[maxValue]."
  [concept-type params]
  (if-let [orbit-number-param (:orbit-number params)]
    (if (string? orbit-number-param)
      (validate-numeric-range-string-param orbit-number-param on-msg/invalid-orbit-number-msg)
      (validate-numeric-range-map orbit-number-param on-msg/invalid-orbit-number-msg))
    []))

(defn equator-crossing-longitude-validation
  "Validates that the equator_crossing_longitude parameter is a valid range string."
  [concept-type params]
  (if-let [equator_crossing_longitude (:equator-crossing-longitude params)]
    (if (string? equator_crossing_longitude)
      (validate-numeric-range-string-param equator_crossing_longitude nil)
      (validate-numeric-range-map equator_crossing_longitude
                                  on-msg/non-numeric-equator-crossing-longitude-parameter))
    []))

(defn boolean-value-validation
  [concept-type params]
  (let [bool-params (select-keys params [:downloadable])]
    (mapcat
      (fn [[key value]]
        (if (or (= "true" value) (= "false" value))
          []
          [(format "Parameter %s must take value of true of false, but was %s" key value)]))
      bool-params)))

(def parameter-validations
  "A list of the functions that can validate parameters. They all accept parameters as an argument
  and return a list of errors."
  [page-size-validation
   page-num-validation
   sort-key-validation
   unrecognized-params-validation
   unrecognized-params-in-options-validation
   unrecognized-params-settings-in-options-validation
   temporal-format-validation
   updated-since-validation
   orbit-number-validation
   equator-crossing-longitude-validation
   cloud-cover-validation
   attribute-validation
   boolean-value-validation])


(defn validate-parameters
  "Validates parameters. Throws exceptions to send to the user. Returns parameters if validation
  was successful so it can be chained with other calls."
  [concept-type params]
  (let [errors (mapcat #(% concept-type params) parameter-validations)]
    (when-not (empty? errors)
      (err/throw-service-errors :invalid-data errors)))
  params)
