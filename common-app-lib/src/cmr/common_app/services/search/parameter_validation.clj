(ns cmr.common-app.services.search.parameter-validation
  "Contains functions for validating query parameters"
  (:require [clojure.set :as set]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.messages :as c-msg]
            [cmr.common.parameter-parser :as parser]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.common.util :as util]
            [clojure.string :as s]
            [cmr.common.date-time-parser :as dt-parser]
            [cmr.common.date-time-range-parser :as dtr-parser]
            [cmr.common-app.services.search.params :as p]
            [cmr.common-app.services.search.messages :as msg]
            [camel-snake-kebab.core :as csk]
            [clj-time.core :as t])
  (:import clojure.lang.ExceptionInfo))

(defn- by-concept-type
  "This is a very simple helper function for defining some of the multimethods here."
  [concept-type]
  concept-type)

(defconfig search-paging-depth-limit
  "The maximum value for page-num * page-size"
  {:type Long
   :default 1000000})

(def basic-params-config
  "Defines a map of parameter validation types to a set of the parameters."
  {;; Parameters that must take a single value, never a vector of values.
   :single-value #{:page-size :page-num :result-format}
   ;; Parameters that must take a single value or a vector of values, never a map of values.
   :multiple-value #{:concept-id}
   ;; Parameters which do not allow option with ignore_case set to true.
   :always-case-sensitive #{:concept-id}
   ;; Parameters which do not allow pattern search option.
   :disallow-pattern #{:concept-id}
   ;; Parameter which allow search with the OR option.
   :allow-or #{}})

(defn merge-params-config
  "Takes two parameter configs like that matching basic-params-config and merges them."
  [params1 params2]
  (merge-with set/union params1 params2))

(defmulti params-config
  "Returns the parameter config for the given concept type. Parameters config should be a map with
   keys like that of basic-params-config."
  by-concept-type)

(def specific-params-config
  "Looks up the parameter validation config for the given concept type and parameter validation type
   and returns the set of parameters that match. Memoized to avoid multimethod performance lookup
   penalties."
  (memoize
   (fn [concept-type param-type]
     (or (get (params-config concept-type) param-type)
         (throw (Exception.
                 (format "No parameter validation config found for [%s] with concept-type [%s]"
                         (pr-str param-type) (pr-str concept-type))))))))

(defn single-value-validation
  "Validates that parameters which, if present, must have a single value and cannot not be
  passed as a vector of values."
  [concept-type params]
  (->> (select-keys params (specific-params-config concept-type :single-value))
       (filter #(sequential? (second %)))
       (map first)
       (map #(format "Parameter [%s] must have a single value." (csk/->snake_case_string %)))))

(defn multiple-value-validation
  "Validates that parameters which, if present, must have a single value or a vector of values."
  [concept-type params]
  (->> (select-keys params (specific-params-config concept-type :multiple-value))
       (filter #(not (or (string? (second %)) (sequential? (second %)))))
       (map first)
       (map #(format "Parameter [%s] must have a single value or multiple values."
                     (csk/->snake_case_string %)))))

(defn- get-ivalue-from-params
  "Get a value from the params as an Integer or nil value. Throws NumberFormatException
  if the value cannot be converted to an Integer."
  [params value-keyword]
  (when-let [value (value-keyword params)]
    ; Return null if value is a vector.  Assumes single-value-validation handles vectors.
    (when-not (sequential? value)
      (Integer. value))))

(defn page-size-validation
  "Validates that the page-size (if present) is a number in the valid range."
  [concept-type params]
  (try
    (if-let [page-size-i (get-ivalue-from-params params :page-size)]
      (cond
        (< page-size-i 0)
        ["page_size must be a number between 0 and 2000"]

        (> page-size-i 2000)
        ["page_size must be a number between 0 and 2000"]

        :else
        [])
      [])
    (catch NumberFormatException e
      ["page_size must be a number between 0 and 2000"])))

(def ^:private offset-error-message "offset must be a number greater than or equal to zero")

(defn offset-validation
  "Returns a seq of any errors in the offset parameter"
  [_ params]
  (if (and (:offset params) (:page-num params))
    ["Only one of offset or page-num may be specified"]
    (try
      (when-let [n (get-ivalue-from-params params :offset)]
        (when (neg? n)
          [offset-error-message]))
      (catch NumberFormatException _
        [offset-error-message]))))

(defn page-num-validation
  "Validates that the page-num (if present) is a number in the valid range."
  [concept-type params]
  (try
    (if-let [page-num-i (get-ivalue-from-params params :page-num)]
      (if (> 1 page-num-i)
        ["page_num must be a number greater than or equal to 1"]
        [])
      [])
    (catch NumberFormatException e
      ["page_num must be a number greater than or equal to 1"])))

(defn paging-depth-validation
  "Validates that the paging depths (page-num * page-size) does not exceed a set limit."
  [concept-type params]
  (try
    (let [limit (search-paging-depth-limit)
          page-size (get-ivalue-from-params params :page-size)
          page-num (get-ivalue-from-params params :page-num)]
      (when (and page-size
                 page-num
                 (> (* page-size page-num) limit))
        [(format "The paging depth (page_num * page_size) of [%d] exceeds the limit of %d."
                 (* page-size page-num)
                 limit)]))
    (catch NumberFormatException e
      ;; This should be handled separately by page-size and page-num validiation
      [])))

(def string-param-options #{:pattern :ignore-case})
(def pattern-option #{:pattern})
(def and-option #{:and})
(def and-or-option #{:and :or})
(def string-plus-and-options #{:pattern :ignore-case :and})
(def string-plus-or-options #{:pattern :ignore-case :or})

(defmulti valid-parameter-options
  "Returns a map of parameter name to the set of option keys that are allowed for that parameter."
  by-concept-type)

(defn parameter-options-validation
  [concept-type params]
  "Validates that no invalid parameter names in the options were supplied"
  [concept-type params]
  (if-let [options (:options params)]
    (let [always-case-sensitive-fields (specific-params-config concept-type :always-case-sensitive)
          valid-options (valid-parameter-options concept-type)]
      (apply concat
             (map
              (fn [[param settings]]
                (if-not (map? settings)
                  [(msg/invalid-settings-for-param param settings)]
                  ;; handle these parameters separately since they don't allow any options
                  (if (always-case-sensitive-fields param)
                    (map #(msg/invalid-opt-for-param param %) (keys settings))
                    (let [valid-options (valid-options param)]
                      ;; Only check params we recognize - other validations will handle the rest
                      (when valid-options
                        (map #(msg/invalid-opt-for-param param %)
                             (set/difference (set (keys settings))
                               valid-options)))))))
              options)))))

(defmulti valid-sort-keys
  "Returns the sort keys that are valid with the given concept type"
  by-concept-type)

(defmethod valid-sort-keys :default
  [_]
  #{})

(defn sort-key-validation
  "Validates the sort-key parameter if present"
  [concept-type params]
  (if-let [sort-key (:sort-key params)]
    (let [valid-keys (valid-sort-keys concept-type)
          sort-keys (if (sequential? sort-key) sort-key [sort-key])]
      (mapcat (fn [sort-key]
                (let [[_ field] (re-find #"[\-+]?(.*)" sort-key)]
                  (when-not (valid-keys (keyword field))
                    [(msg/invalid-sort-key (csk/->snake_case_string field ) concept-type)])))
              sort-keys))
    []))


(def concept-type->valid-param-names
  "A set of the valid parameter names for the given concept-type."
  (memoize
   (fn [concept-type]
    (set (keys (p/param-mappings concept-type))))))

(defmulti valid-query-level-params
  "Returns a set of parameter names that are valid at the query level"
  by-concept-type)

(defmethod valid-query-level-params :default
  [_]
  #{})

(def standard-valid-params
  "The set of standard valid query level parameters."
  #{:page-size :page-num :offset :sort-key :result-format :options})

(defn unrecognized-params-validation
  "Validates that no invalid parameters were supplied"
  [concept-type params]
  (map #(format "Parameter [%s] was not recognized." (csk/->snake_case_string %))
       (set/difference (set (keys params))
                       (set/union standard-valid-params
                                  (concept-type->valid-param-names concept-type)
                                  (valid-query-level-params concept-type)))))


(defmulti valid-query-level-options
  "Returns a set of query level options that are valid."
  by-concept-type)

(defmethod valid-query-level-options :default
  [_]
  #{})

(defn unrecognized-params-in-options-validation
  "Validates that no invalid parameters names in the options were supplied"
  [concept-type params]
  (if-let [options (:options params)]
    (map #(str "Parameter [" (csk/->snake_case_string %)"] with option was not recognized.")
         (set/difference (set (keys options))
                         (set/union (concept-type->valid-param-names concept-type)
                                    (valid-query-level-options concept-type))))
    []))

(defn validate-date-time
  "Validates datetime string is in the given format"
  [date-name dt]
  (try
    (when-not (s/blank? dt)
      (dt-parser/parse-datetime dt))
    []
    (catch ExceptionInfo e
      [(format "%s datetime is invalid: %s." date-name (first (:errors (ex-data e))))])))

(defn validate-date-time-range
  "Validates datetime range string is in the correct format"
  [dtr]
  (try
    (when-not (s/blank? dtr)
      (dtr-parser/parse-datetime-range dtr))
    []
    (catch ExceptionInfo e
      [(format "temporal range is invalid: %s." (first (:errors (ex-data e))))])))

(defn validate-numeric-range-param
  "Validates a numeric parameter in the form parameter=value or
  parameter=min,max, appending the message argument to the error array on failure."
  [param error-message-fn & args]
  (let [errors (parser/numeric-range-string-validation param)]
    (if-not (empty? errors)
      (if error-message-fn
        (concat [(apply error-message-fn args)] errors)
        errors)
      [])))

(defn unrecognized-standard-query-params-validation
  "Validates that any query parameters passed to the AQL or JSON search endpoints are valid."
  [concept-type params]
  (map #(str "Parameter [" (csk/->snake_case_string % )"] was not recognized.")
       (set/difference (set (keys params))
                       (set/union standard-valid-params
                                  (valid-query-level-params concept-type)))))

(defn assoc-keys->param-name
  "Given a set of parameter assoc keys, returns the URL string for the parameter at that path.  For
  instance, [:foo :bar :baz] returns \"foo[bar][baz]\""
  [keys]
  (let [[root & descendants] (map csk/->snake_case_string keys)
        subscripts (s/join (map #(str "[" % "]") descendants))]
    (str root subscripts)))

(defn validate-map
  "Validates that the parameter value found by following keys is a map or null.  Dissocs the
  parameter from params if it is invalid, returning [valid-params error-strings].
  Examples:
  => (validate-map [:parent :child] {:parent {:child {:gchild 0}}})
  [{:parent {:child {:gchild 0}}} []]
  => (validate-map [:parent :child] {:parent {:child 0}})
  [{:parent {}} [\"Parameter [parent[child]] must contain a nested value, parent[child][...]=value.\"]]"
  [keys params]
  (let [value (get-in params keys)]
    (if (or (nil? value) (map? value))
      [params []]
      (let [param-name (assoc-keys->param-name keys)]
        [(util/dissoc-in params keys)
         [(str "Parameter [" param-name "] must include a nested key, " param-name "[...]=value.")]]))))

(defn apply-type-validations
  "Validates data types of parameters.  Returns a tuple of [safe-params errors] where errors
  contains a list of type error strings and safe-params contains the original params with
  error those that have type errors dissoc'ed out."
  [params validation-functions]
  (loop [[validation & validations] validation-functions
         safe-params params
         errors []]
    (let [[new-safe-params new-errors] (validation safe-params)
          all-errors (concat new-errors errors)]
      (if (seq validations)
        (recur validations new-safe-params all-errors)
        [new-safe-params all-errors]))))

(defn validate-all-map-values
  "Applies the validation function to all values in the map and aggregates the result.  Useful
  for places like science keywords where we don't know all of the keys up front."
  [validation-fn path params]
  (let [entries (get-in params path)]
    (if (seq entries)
      (let [validations (map #(partial validation-fn (concat path [%])) (keys entries))]
        (apply-type-validations params validations))
      [params []])))

(def common-validations
  "A set of validations common to all concept types."
  [single-value-validation
   multiple-value-validation
   page-size-validation
   page-num-validation
   offset-validation
   paging-depth-validation
   sort-key-validation
   unrecognized-params-validation
   unrecognized-params-in-options-validation
   parameter-options-validation])


(defn validate-parameters
  "Applies the list of validations to the parameters throwing an exception if there were any errors.
   An optional list of other errors can be passed in to add to the set that's thrown."
  ([concept-type params validations]
   (validate-parameters concept-type params validations nil))
  ([concept-type params validations other-errors]
   (when-let [errors (seq (concat other-errors (mapcat #(% concept-type params) validations)))]
     (errors/throw-service-errors :bad-request errors))))
