(ns cmr.search.services.legacy-parameters
  "Contains functions for tranforming legacy parameters to the CMR format."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [ring.util.codec :as rc]
            [cmr.common.services.messages :as msg]
            [cmr.search.services.messages.attribute-messages :as a-msg]
            [cmr.common.services.errors :as errors]))

(def param-aliases
  "A map of non UMM parameter names to their UMM fields."
  {:dataset-id :entry-title
   :dif-entry-id :entry-id
   :campaign :project
   :echo-collection-id :concept-id
   :echo-granule-id :concept-id
   :online-only :downloadable})

(defn- replace-exclude-param-aliases
  "Convert non UMM parameter names to their UMM fields iff exclude params are present"
  [params]
  (if (map? (:exclude params))
    (update-in params [:exclude]
               #(set/rename-keys % param-aliases))
    params))


(defn replace-parameter-aliases
  "Replaces aliases of parameter names"
  [params]
  (-> params
      (set/rename-keys param-aliases)
      (update-in [:options]
                 #(when % (set/rename-keys % param-aliases)))
      replace-exclude-param-aliases))

(defn- psa-pre-validation
  "Check to see if the client has specified BOTH legacy format psa parameters and the current csv
  format, which is an error. Also check to make sure that cmr style uses 'attribute[]=' and not
  'attribute='."
  [params]
  (if-let [attributes (:attribute params)]
    (if (vector? attributes)
      (reduce (fn [memo x]
                (cond
                  (or (and (string? x) (= :legacy-style memo))
                      (and (map? x) (= :cmr-style memo)))
                  (msg/data-error :invalid-data a-msg/mixed-legacy-and-cmr-style-parameters-msg)

                  (string? x)
                  :cmr-style

                  (map? x)
                  :legacy-style

                  :else
                  (errors/internal-error! (a-msg/expected-map-or-str-parameter-msg x))))
              nil
              attributes)
      (msg/data-error :invalid-data a-msg/attributes-must-be-sequence-msg))))

(defn- escape-commas
  "Escape commas in an attribute parameter field"
  [value]
  (when value (s/replace value "," "\\,")))

(defn- attr-map->cmr-param
  "Create an attribute string from a map of attribute key/values."
  [{:keys [name type value minValue maxValue]}]
  (let [[name type value minValue maxValue] (map escape-commas [name type value minValue maxValue])
        base (str type "," name ",")]
    (if value
      (str base value)
      (str base minValue "," maxValue))))

(defn- legacy-psa-param->tuple
  "Convert a legacy attribute url parameter to a tuple"
  [param]
  (let [[_ key value] (re-find #"(?s)attribute\[\]\[(.*?)\]=(.*)" param)]
    (when key [(keyword key) value])))

(defn- checked-merge
  "Merge a tuple into a map only if the key does not already exist. Throws an
  exception if it does. This allows us to catch client errors where they try to specify
  the same attribute field twice, e.g., &attribute[][name]=a&attribute[name]=b."
  [map tuple]
  (if (get map (first tuple))
    (msg/data-error :invalid-data a-msg/duplicate-parameter-msg tuple)
    (merge map tuple)))


(defn- group-legacy-psa-tuples
  "Take a list of tuples created from a legacy query string and group them together as attributes"
  [big-list]
  (reduce (fn [results item]
            (if (= :name (first item))
              ;; Put current set on results and start a new current set
              (conj results (merge {} item))
              ;; Update the current set (last set on results)
              (update-in results [(dec (count results))] checked-merge item)))
          []
          big-list))

(defn process-legacy-psa
  "Process legacy product specific attributes by parsing the query string and updating params
  with attributes matching the new cmr csv style"
  [params query-string]
  (psa-pre-validation params)
  (let [param-strings (map rc/url-decode (s/split query-string #"&"))
        param-tuples (keep legacy-psa-param->tuple param-strings)
        param-maps (group-legacy-psa-tuples param-tuples)
        psa (map attr-map->cmr-param param-maps)]
    (if-not (empty? psa)
      (assoc params :attribute psa)
      params)))

(defn- process-legacy-range-maps
  "Changes legacy map range conditions in the param[minValue]/param[maxValue] format
  to the cmr format: min,max."
  [concept-type params]
  (reduce-kv (fn [memo k v]
               ;; look for parameters in the map form
               (if (map? v)
                 (let [{:keys [value min-value max-value min max]} v]
                   (cond
                     (or min-value max-value)
                     ;; convert the map into a comma separated string
                     (assoc memo k (str min-value "," max-value))

                     (or min max)
                     ;; convert the map into a comma separated string
                     (assoc memo k (str min "," max))

                     value
                     (assoc memo k value)

                     :else ;; do nothing
                     memo))
                 memo))
             params
             params))

(defn- process-equator-crossing-date
  "Legacy format for granule equator crossing date is to specify two separate parameters:
  equator-crossing-start-date and equator-crossing-end-date. This function replaces those
  parameters with the current format of start,end."
  [concept-type params]
  (let [{:keys [equator-crossing-start-date equator-crossing-end-date]} params]
    (if (or equator-crossing-start-date equator-crossing-end-date)
      (-> params
          (dissoc :equator-crossing-start-date :equator-crossing-end-date)
          (assoc :equator-crossing-date (str equator-crossing-start-date
                                             ","
                                             equator-crossing-end-date)))
      params)))

;; Add others to this list as needed - note that order is important here
(def legacy-multi-params-condition-funcs
  "A list of functions to call to transform any legacy parameters into CMR form. Each function
  must accept a pair of arguments [concept-type params] where concept-type is :collection,
  :granule, etc. and params is the parameter map generated by the ring middleware."
  [process-equator-crossing-date
   process-legacy-range-maps])

(defn process-legacy-multi-params-conditions
  "Handle conditions that use a legacy range style of using two parameters to specify a range."
  [concept-type params]
  (reduce #(%2 concept-type %1)
          params
          legacy-multi-params-condition-funcs))