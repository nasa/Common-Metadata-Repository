(ns cmr.search.services.parameter-validation
  "Contains functions for validating query parameters"
  (:require [clojure.set :as set]
            [cmr.common.services.errors :as err]
            [clojure.string :as s]
            [clj-time.format :as f]
            [cmr.search.services.parameters :as p]))

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
  (if-let [page-size (:page_size params)]
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
  (println (str "PARAMS: " params))
  (if-let [page-num (:page_num params)]
    (try (let [page-num-i (Integer. page-num)]
           (if (> 0 page-num-i)
             [(format "page_num %d is less than 0" page-num-i)]))
      (catch NumberFormatException e ["page_num must be a number greater than or equal to 0"]))
    []))

(defn unrecognized-params-validation
  "Validates that no invalid parameters were supplied"
  [concept-type params]
  ;; this test does not apply to page_size or page_num
  (let [params (dissoc params :page_size :page_num)]
    (map #(str "Parameter [" (name % )"] was not recognized.")
         (set/difference (set (keys params))
                         (concept-type->valid-param-names concept-type)))))

(defn unrecognized-params-in-options-validation
  "Validates that no invalid parameters names in the options were supplied"
  [concept-type params]
  (if-let [options (:options params)]
    (map #(str "Parameter [" (name %)"] with option was not recognized.")
         (set/difference (set (keys options))
                         (concept-type->valid-param-names concept-type)))
    []))

(defn options-only-for-string-conditions-validation
  "Validates that only string conditions support options"
  [concept-type params]
  ;; TODO once we have more conditions than string conditions add a validation that only
  ;; string conditions support options.
  [])

(defn unrecognized-params-settings-in-options-validation
  "Validates that no invalid parameters names in the options were supplied"
  [concept-type params]
  (if-let [options (:options params)]
    (apply concat
           (map
             (fn [[param settings]]
               (map #(str "Option [" (name %) "] for param [" (name param) "] was not recognized.")
                    (set/difference (set (keys settings)) (set [:ignore_case :pattern :and :or]))))
             options))
    []))

(defn- validate-date-time
  "Validates datetime string is in the given format"
  [dt format-type]
  (try
    (when-not (s/blank? dt)
      (f/parse (f/formatters format-type) dt))
    []
    (catch IllegalArgumentException e
      [(format "temporal datetime is invalid: %s, should be in yyyy-MM-ddTHH:mm:ssZ format."
               (.getMessage e))])))

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
                   (validate-date-time start-date :date-time-no-ms)
                   (validate-date-time end-date :date-time-no-ms)
                   (day-valid? start-day "temporal_start_day")
                   (day-valid? end-day "temporal_end_day"))))
             temporal))
    []))

(def parameter-validations
  "A list of the functions that can validate parameters. They all accept parameters as an argument
  and return a list of errors."
  [page-size-validation
   page-num-validation
   unrecognized-params-validation
   unrecognized-params-in-options-validation
   options-only-for-string-conditions-validation
   unrecognized-params-settings-in-options-validation
   temporal-format-validation])

(defn validate-parameters
  "Validates parameters. Throws exceptions to send to the user. Returns parameters if validation
  was successful so it can be chained with other calls."
  [concept-type params]
  (let [errors (mapcat #(% concept-type params) parameter-validations)]
    (when-not (empty? errors)
      (err/throw-service-errors :invalid-data errors)))
  params)
