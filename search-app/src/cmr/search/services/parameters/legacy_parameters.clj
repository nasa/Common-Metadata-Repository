(ns cmr.search.services.parameters.legacy-parameters
  "Contains functions for tranforming legacy parameters to the CMR format."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [ring.util.codec :as rc]
            [cmr.common.util :as cu]
            [clojure.walk :as w]
            [cmr.common.services.messages :as msg]
            [cmr.search.services.messages.attribute-messages :as a-msg]
            [cmr.common.services.errors :as errors]))

(def param-aliases
  "A map of non UMM parameter names to their UMM fields."
  {:dataset-id :entry-title
   :campaign :project
   :echo-collection-id :concept-id
   :echo-granule-id :concept-id
   :online-only :downloadable
   :provider-id :provider
   :day-night-flag :day-night
   :browse-only :browsable
   :processing-level :processing-level-id
   :grid :two-d-coordinate-system})

(def sort-key-replacements
  "A map of legacy sort keys with all variations of +/- to use for substitutions."
  (into {} (mapcat (fn [[a orig]]
                     (let [a (name a)
                           orig (name orig)]
                       [[a orig]
                        [(str "+" a) (str "+" orig)]
                        [(str "-" a) (str "-" orig)]]))
                   param-aliases)))

(defn merger
  "Make a sequence from supplied values."
  [v1 v2]
  (let [make-seq #(if (sequential? %) % [%])]
    (concat (make-seq v1) (make-seq v2))))

(defn- replace-legacy-sort-key
  "Replace legacy sort key with CMR version. Takes a sort key with +,-, or nothing as a prefix
  and replaces the appropriate part."
  [sort-key]
  (get sort-key-replacements sort-key sort-key))

(defn- replace-legacy-sort-keys
  "Replace legacy sort keys with CMR versions."
  [params]
  (if-let [sort-key (:sort-key params)]
    (assoc params :sort-key
           (if (sequential? sort-key)
             (map replace-legacy-sort-key sort-key)
             (replace-legacy-sort-key sort-key)))
    params))

(defn replace-parameter-aliases
  "Walk the request params tree to replace aliases of parameter names."
  [params]
  (->> params
       (w/postwalk #(if (map? %)
                      (cu/rename-keys-with % param-aliases merger)
                      %))
       replace-legacy-sort-keys))

(defn- psa-pre-validation
  "Check to see if the client has specified BOTH legacy format psa parameters and the current csv
  format, which is an error. Also check to make sure that cmr style uses 'attribute[]=' and not
  'attribute='."
  [attributes]
  (when-let [attributes (if (map? attributes)
                          ;; treat it as legacy style
                          (vec (vals attributes))
                          attributes)]
    (if (vector? attributes)
      (reduce (fn [memo x]
                (cond
                  (or (and (string? x) (= :legacy-style memo))
                      (and (map? x) (= :cmr-style memo)))
                  (msg/data-error :bad-request a-msg/mixed-legacy-and-cmr-style-parameters-msg)

                  (string? x)
                  :cmr-style

                  (map? x)
                  :legacy-style

                  :else
                  (errors/internal-error! (a-msg/expected-map-or-str-parameter-msg x))))
              nil
              attributes)
      (msg/data-error :bad-request a-msg/attributes-must-be-sequence-msg))))

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

(defn- checked-merge
  "Merge the given attribute map into the result map only if the key does not already exist.
  Throws an exception if it does. This allows us to catch client errors where they try to specify
  the same attribute field twice, e.g., &attribute[][name]=a&attribute[][name]=b."
  [result attr-map]
  (if (get result (first (keys attr-map)))
    (msg/data-error :bad-request a-msg/duplicate-parameter-msg (first attr-map))
    (merge result attr-map)))

(defn process-legacy-psa
  "Process legacy product specific attributes by updating params with attributes
  matching the new cmr csv style"
  [params]
  (let [attributes (:attribute params)
        attribute-type (psa-pre-validation attributes)
        attr-maps (when (= :legacy-style attribute-type)
                    (if (vector? attributes)
                      [(reduce checked-merge attributes)]
                      (vec (vals attributes))))
        psa (map attr-map->cmr-param attr-maps)]
    (if (seq psa)
      (assoc params :attribute psa)
      params)))

(defn- process-legacy-range-maps
  "Changes legacy map range conditions in the param[minValue]/param[maxValue] format
  to the cmr format: min,max."
  [concept-type params]
  (reduce-kv (fn [memo k v]
               ;; look for parameters in the map form
               (if (and (map? v) (not= :options k))
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

(defn- cmr-two-d-coord-system
  "Returns the CMR style two d coordinate system parameter for the legacy style params"
  [name coords]
  (let [cmr-coords (when coords (-> coords
                                    (s/replace ":" "+")
                                    (s/replace "," ":")
                                    (s/replace "+" ",")))]
    (if cmr-coords
      (str name ":" cmr-coords)
      name)))

(defn- process-legacy-two-d-coord-system
  "Legacy format for granule two d coordinate system is to specify two separate parameters:
  two_d_coordinate_system[name]=wrs-1&two_d_coordinate_system[coordinates]=0-5:0-10.
  This function replaces those parameters with the CMR format."
  [concept-type params]
  (let [{:keys [two-d-coordinate-system]} params
        {:keys [name coordinates]} two-d-coordinate-system]
    (if (or name coordinates)
      (-> params
          (dissoc :two-d-coordinate-system :equator-crossing-end-date)
          (assoc :two-d-coordinate-system (cmr-two-d-coord-system name coordinates)))
      params)))

;; Add others to this list as needed - note that order is important here
(def legacy-multi-params-condition-funcs
  "A list of functions to call to transform any legacy parameters into CMR form. Each function
  must accept a pair of arguments [concept-type params] where concept-type is :collection,
  :granule, etc. and params is the parameter map generated by the ring middleware."
  [process-equator-crossing-date
   process-legacy-range-maps
   process-legacy-two-d-coord-system])

(defn process-legacy-multi-params-conditions
  "Handle conditions that use a legacy range style of using two parameters to specify a range."
  [concept-type params]
  (reduce #(%2 concept-type %1)
          params
          legacy-multi-params-condition-funcs))

(defn replace-science-keywords-or-option
  "Handle legacy styled science keywords or options."
  [concept-type params]
  (if-let [or-value (get-in params [:science-keywords :or])]
    (-> params
        (cu/dissoc-in [:science-keywords :or])
        (assoc-in [:options :science-keywords :or] or-value))
    params))

(comment
  ;;;;;;;;;;
  (let [params {:exclude {:concept-id ["G10000000099-PROV2"],
                          :echo-granule-id ["G1000000006-PROV2"]
                          :echo-collection-id "C1000000002-PROV2"},
                :echo-granule-id ["G1000000002-PROV1" "G1000000003-PROV1"
                                  "G1000000004-PROV1" "G1000000005-PROV2" "G1000000006-PROV2"]}]
    (replace-parameter-aliases params)))




