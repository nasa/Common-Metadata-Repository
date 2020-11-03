(ns cmr.search.services.parameters.converters.range-facet
  "Contains functions for converting range facet parameters in the format of <min value> <with
   optional unit> to <max value> <unit> from search parameters to a nested numeric range condition
   in the query model."
  (:require
    [clojure.string :as string]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.params :as p]
    [cmr.common-app.services.search.query-model :as qm]
    [cmr.common-app.services.search.query-to-elastic :as q2e]
    [cmr.common.services.errors :as errors]
    [cmr.common.util :refer [convert-to-meters]]))

(defn- convert
  "Convert the value if necessary to meters in order to search resolutions which are stored in
   meters."
  [value unit]
  (when-let [unit (string/lower-case unit)]
    (cond
      (or (string/includes? unit "km")
          (string/includes? unit "kilo")) (convert-to-meters value "Kilometers")
      (string/includes? unit "deg") (convert-to-meters value "Decimal Degrees")
      (or (string/includes? unit "met")
          (string/includes? unit "m")) value
      :else nil)))

(defn- normalize-string
  "This function removes the '+' or the '& < more text>' from the following strings.
   '1 meter & above'
   '1 meter +'
   '1+ meter'
   so that the returned result is '1 meter'"
  [range-string]
  (string/replace range-string #" *\+| *&.*" ""))

(defn- split-range-string-by-space
  [range-string]
  (-> range-string
      (string/trim)
      (string/split #" +")))

(defn parse-unit
  "Parse out the passed in arrays to return the value's unit. Each array will either have 1 or 2
   values. If the first array just has 1 value, then use the second array to get the unit because
   both values are using the second arrays unit. Otherwise use the first array."
  [range-part1 range-part2]
  (def range-part1 range-part1)
  (def range-part2 range-part2)
  (if (= 1 (count range-part1))
    (get range-part2 1)
    (get range-part1 1)))

(defn parse-range
  "This function parses a humanizer range facet map that contains range facet values of
   '1 meter & above - any string past & is ignored.'
   '1 meter +'
   '1+ meter'
   '0 to meter' or
   '0 meter to 1 meter'"
  [range-string]
  (let [range-array (-> range-string
                        (normalize-string)
                        (string/split #" to "))
        range-part1 (split-range-string-by-space (get range-array 0))
        range-part2 (when (get range-array 1)
                      (split-range-string-by-space (get range-array 1)))
        unit-part1 (parse-unit range-part1 range-part2)
        min-value (convert (Float/parseFloat (get range-part1 0)) unit-part1)
        max-value (if range-part2
                    (convert (Float/parseFloat (get range-part2 0)) (get range-part2 1))
                    (Float/MAX_VALUE))]
    [min-value max-value]))

(defn validate-range-facet-str
  "Validate the range facet parameter string, throws error if it is invalid.
   These are the valid patterns in the order in the code below.
   '0 to 1 meter'
   '0 meter to 1 meter'
   '1+ meter'
   '1 meter +' (or 1 meter+)
   '1 meter & above - any string past & is ignored.'
   The test? parameter allows for testing of this function without putting exceptions into the console."
  ([param-str]
   (validate-range-facet-str param-str false))
  ([param-str test?]
   (let [results (or (re-find #"^\d+ +to +\d+ +(km|kilo.*|deg.*|m.*)" param-str)
                     (re-find #"^\d+ +(km|kilo.*|deg.*|m.*) +to +\d+ +(km|kilo.*|deg.*|m.*)" param-str)
                     (re-find #"^\d+\+ +(km|kilo.*|deg.*|m.*)" param-str)
                     (re-find #"^\d+ +(km|kilo.*|deg.*|m.*) *\+" param-str)
                     (re-find #"^\d+ +(km|kilo.*|deg.*|m.*) +\&" param-str))]
     (when-not results
       ;; use this to suppress expections being thrown during testing so that it is easier to find
       ;; real failures.
       (if test?
         false
         (let [msg (format (str "%s is not a correct range-facet parameter. "
                                "The correct format for the parameters is either: "
                                "<number> to <number> <unit>, "
                                "<number> <unit> to <number> <unit>, "
                                "<number> <unit> +, or "
                                "<number> <unit> & above, "
                                "where unit is either: kilometers, degrees, or meters.")
                           param-str)]
           (errors/throw-service-error :bad-request msg)))))))

(defn range-facet->condition
  "Convert the passed in range facet parameter string to a nested elastic search query."
  [concept-type param param-str]
  (validate-range-facet-str param-str)
  (let [parent-field (q2e/query-field->elastic-field param concept-type)
        value-field (keyword (str (name parent-field) ".value"))
        values (parse-range param-str)
        min-value (get values 0)
        max-value (get values 1)]
    (qm/nested-condition
      parent-field
      (qm/numeric-range-condition value-field min-value max-value))))

;;  "Converts range parameter with <min to max> or <min &> syntax into a query condition."
(defmethod p/parameter->condition :range-facet
  [_context concept-type param values options]
  (if (string? values)
    (range-facet->condition concept-type param values)
    (gc/or-conds (map (partial range-facet->condition concept-type param) values))))
