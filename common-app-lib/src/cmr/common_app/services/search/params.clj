(ns cmr.common-app.services.search.params
  "Contains common code for handling search parameters and converting them into a query model."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.data :as data]
   [clojure.string :as string]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as u]))

(defn- sanitize-sort-key
  "Sanitizes a single sort key preserving the direction character."
  [sort-key]
  (if-let [[_ dir-char field] (re-find #"([^a-zA-Z])?(.*)" sort-key)]
    (str dir-char (csk/->kebab-case field))
    sort-key))

(defn remove-empty-params
  "Returns the params after removing the ones with value of an empty string
  or string with just whitespaces"
  [params]
  (let [not-empty-string? (fn [value]
                            (not (and (string? value) (= "" (string/trim value)))))]
    (into {} (filter (comp not-empty-string? second) params))))

(defn sanitize-params
  "Manipulates the parameters to make them easier to process"
  [params]
  (-> params
      remove-empty-params
      u/map-keys->kebab-case
      (update-in [:sort-key] #(when % (if (sequential? %)
                                        (map sanitize-sort-key %)
                                        (sanitize-sort-key %))))))

(defn- comma-separated-string->list
  "Returns the list of values from a comma separated string"
  [value]
  (when value
    (map string/trim (string/split value #","))))

(defn normalized-list-value
  "Returns the normalized value of a comma separated string or a list of such string."
  [value]
  (let [values (if (sequential? value)
                 (mapcat comma-separated-string->list value)
                 (comma-separated-string->list value))]
    (distinct values)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parameter to query conversion

(defmulti always-case-sensitive-fields
  "Returns a set of parameters that will always be case sensitive for the given concept type."
  (fn [concept-type]
    concept-type))

(defmethod always-case-sensitive-fields :default
  [_]
  #{:concept-id})

(defn case-sensitive-field?
  "Return true if the given field is a case-sensitive field"
  [concept-type field options]
  (or (contains? (always-case-sensitive-fields concept-type) field)
      (= "false" (get-in options [field :ignore-case]))))

(defn pattern-field?
  "Returns true if the field is a pattern"
  [concept-type field options]
  (= "true" (get-in options [field :pattern])))

(defn group-operation
  "Returns the group operation (:and or :or) for the given field."
  ([field options]
   (group-operation field options :or))
  ([field options default]
   (cond
     (= "true" (get-in options [field :and])) :and
     (= "true" (get-in options [field :or])) :or
     :else default)))

(defmulti param-mappings
  "Returns the mapping of parameter name to parameter type given a concept type."
  (fn [concept-type]
    concept-type))

(defn- param-name->type
  "Returns the query condition type based on the given concept-type and param-name."
  [concept-type param-name]
  (get (param-mappings concept-type) param-name))

(defmulti parameter->condition
  "Converts a parameter into a condition"
  (fn [context concept-type param value options]
    (param-name->type concept-type param)))

(defmethod parameter->condition :default
  [_context concept-type param value options]
  (errors/internal-error!
   (format "Could not find parameter handler for [%s] with concept-type [%s]" param concept-type)))

(defn string-parameter->condition
  [concept-type param value options]
  (if (sequential? value)
    (gc/group-conds (group-operation param options)
                    (map #(string-parameter->condition concept-type param % options) value))
    (let [case-sensitive (case-sensitive-field? concept-type param options)
          pattern (pattern-field? concept-type param options)]
      (qm/string-condition param value case-sensitive pattern))))

(defmethod parameter->condition :string
  [_context concept-type param value options]
  (string-parameter->condition concept-type param value options))

(defmethod parameter->condition :int
  [_context concept-type param value options]
  (if (sequential? value)
    (gc/group-conds (group-operation param options)
                    (map #(qm/numeric-value-condition param %) value))
    (qm/numeric-value-condition param value)))

;; or-conds --> "not (CondA and CondB)" == "(not CondA) or (not CondB)"
(defmethod parameter->condition :exclude
  [context concept-type param value options]
  (gc/or-conds
   (map (fn [[exclude-param exclude-val]]
          (qm/map->NegatedCondition
           {:condition (parameter->condition context concept-type exclude-param exclude-val options)}))
        value)))

(defmethod parameter->condition :boolean
  [_context concept-type param value options]
  (let [value (u/safe-lowercase value)]
    (cond
    (or (= "true" value) (= "false" value))
    (qm/boolean-condition param (= "true" value))
    (= "unset" (string/lower-case value))
    qm/match-all

    :else
    (errors/internal-error! (format "Boolean condition for %s has invalid value of [%s]" param value)))))

(defmethod parameter->condition :num-range
  [_context concept-type param value options]
  (qm/numeric-range-str->condition param value))

(defn parse-sort-key
  "Parses the sort key param and returns a sequence of maps with fields and order.
   Returns nil if no sort key was specified."
  [sort-key aliases]
  (when sort-key
    (if (sequential? sort-key)
      (mapcat #(parse-sort-key % aliases) sort-key)
      (let [[_ dir-char field] (re-find #"([\-+])?(.*)" sort-key)
            direction (cond
                        (= dir-char "-")
                        :desc

                        (= dir-char "+")
                        :asc

                        :else
                        ;; score sorts default to descending sort, everything else ascending
                        (if (= "score" (string/lower-case sort-key))
                          :desc
                          :asc))
            field (keyword field)]
        [{:order direction
          :field (or (get aliases field)
                     field)}]))))

(defn default-parse-query-level-params
  ([concept-type params]
   (default-parse-query-level-params concept-type params {}))
  ([concept-type params aliases]
   (let [page-size (Integer. (get params :page-size qm/default-page-size))
         scroll (when-let [scroll-param (:scroll params)]
                  (case (string/lower-case scroll-param)
                    "true" true
                    "defer" "defer"
                    false))
         {:keys [offset page-num]} params]
     [(dissoc params :offset :page-size :page-num :result-format :scroll :sort-key)
      {:concept-type concept-type
       :page-size page-size
       :scroll scroll
       :offset (cond
                 page-num (* (dec (Integer. page-num)) page-size)
                 offset (Integer. offset)
                 :else qm/default-offset)
       :sort-keys (parse-sort-key (:sort-key params) aliases)
       :result-format (:result-format params)}])))

(defn generate-param-query-conditions
  "Generate the search conditions for the query based on the parameters"
  [context concept-type params]
  (let [options (u/map-keys->kebab-case (get params :options {}))
        params (dissoc params :options)]
    (when (seq params)
      (map (fn [[param value]]
             (parameter->condition context concept-type param value options))
           params))))

(defmulti parse-query-level-params
  "Extracts parameters apply at the query level page-size and result format and returns a tuple of
   leftover parameters and a map as query attributes.

   The function takes the entire set of parameters from the API, converts some of them into attributes
   that will go in the query, and returns the leftover parameters along with the query attributes.
   Anything that is not a parameter that becomes a condition is a query level parameter. There are
   different query level parameters for different concept types. Collections have many of these like
   include_granule_counts, include_facets, and echo_compatible."
  (fn [concept-type params]
    concept-type))

(defmethod parse-query-level-params :default
  [concept-type params]
  (default-parse-query-level-params concept-type params))

(def ^:private valid-spatial-params
  "Define a list of valid spatial parameter keys"
  [:bounding-box :circle :point :line :polygon])

(defn- generate-conditions
  "Converts given parameters into query conditions"
  [context concept-type params options]
  (map (fn [[param value]]
         (parameter->condition context concept-type param value options))
       params))

(defn- create-combined-conditions
  "When valid spatial parameters are given and the spatial OR option is passed,
   create proper search conditions"
  [context concept-type options params spatial-params]
  (let [non-spatial-params (apply dissoc params valid-spatial-params)
        non-spatial-conditions (when (seq non-spatial-params)
                                 (generate-conditions
                                  context concept-type non-spatial-params options))
        combined-spatial-conditions (when (seq spatial-params)
                                      (gc/or-conds
                                       (generate-conditions
                                        context concept-type spatial-params options)))
        all-conditions (remove nil?
                               (conj non-spatial-conditions combined-spatial-conditions))]
    (gc/and-conds all-conditions)))

(defn- params->query-conditions
  "Appropriately combine query operators based on given options"
  [context concept-type options params query-attribs]
  (let [has-spatial-or-option? (= (get-in options [:spatial :or]) "true")
        spatial-params (select-keys params valid-spatial-params)]
    (if (and has-spatial-or-option?
             (seq spatial-params))
      (qm/query
       (assoc query-attribs :condition (create-combined-conditions context
                                                                   concept-type
                                                                   options
                                                                   params
                                                                   spatial-params)))
      (qm/query
       (assoc query-attribs :condition (gc/and-conds
                                        (generate-conditions context
                                                             concept-type
                                                             params
                                                             options)))))))

(defn parse-parameter-query
  "Converts parameters into a query model."
  [context concept-type params]
  (let [[params query-attribs] (parse-query-level-params concept-type params)
        options (u/map-keys->kebab-case (get params :options {}))
        {:keys [scroll-id search-after]} context
        params (dissoc params :options)
        query (if (empty? params)
                ;; matches everything
                (qm/query query-attribs)
                ;; Convert params into conditions
                (params->query-conditions context concept-type options params query-attribs))]
    ;; add the scroll-id and search-after if present
    (merge query
           (when scroll-id {:scroll-id scroll-id})
           (when search-after {:search-after search-after}))))
