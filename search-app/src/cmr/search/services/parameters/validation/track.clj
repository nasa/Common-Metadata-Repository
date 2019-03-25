(ns cmr.search.services.parameters.validation.track
  "Contains functions for validating track related parameters."
  (:require
   [clojure.string :as string]
   [cmr.common-app.services.search.params :as common-params]
   [cmr.search.services.messages.common-messages :as msg]
   [cmr.search.services.parameters.validation.util :as validation-util])
  (:import
   (clojure.lang ExceptionInfo)
   (java.lang Integer)))

(defn- passes-validation-for-field
  "Performs passes subfield validation."
  [params]
  (validation-util/nested-field-validation-for-subfield
   :passes :granule params (msg/passes-invalid-format-msg)))

(defn- validate-natural-number
  "Validate the given field value can be parsed as a positive integer"
  [field value]
  (try
    (let [num (Integer/parseInt value)]
      (when (< num 1)
        [(format "%s must be a positive integer, but was [%s]"
                 (string/capitalize (name field)) value)]))
    (catch NumberFormatException e
      [(format "%s must be a positive integer, but was [%s]"
               (string/capitalize (name field)) value)])))

(defn- validate-tile
  "Validate tile is in the format of \"\\d+[LRF]\""
  [index tile]
  (when-not (re-matches #"\d+[LRF]" tile)
    [(format "Tile must be in the format of \"\\d+[LRF]\", but was [%s] in passes[%s][tiles]"
             tile index)]))

(defn- validate-tiles
  "Validate tiles within the given passes-obj is in the format of \"\\d+[LRF]\""
  [passes-obj]
  (let [[index {:keys [tiles]}] passes-obj
        index (name index)]
    (if (or (nil? tiles) (sequential? tiles) (string? tiles))
      (mapcat #(validate-tile index %) (common-params/normalized-list-value tiles))
      [(format "Tiles must be a string or list of strings, but was %s in passes[%s][tiles]"
               tiles index)])))

(defn- cycle-param->cycle-values
  "Returns cycle values in a list from the given cycle parameter value"
  [cycle]
  (if (sequential? cycle)
    cycle
    (when-not (string/blank? cycle)
      (vector cycle))))

(defn- validate-pass
  "Validates the given passes, returns error when required pass is missing or not a natural number"
  [passes-obj]
  (let [[index {:keys [pass]}] passes-obj
        index (name index)]
    (cond
      ;; the pass value must be a string value represent a postive integer
      (and (some? pass) (not (string? pass)))
      [(format "Parameter passes[%s][pass] must be a positive integer, but was %s"
               index pass)]

      (string/blank? pass)
      [(format "Parameter passes[%s] is missing required field passes[%s][pass]"
               index index)]

      :else
      (validate-natural-number (format "passes[%s][pass]" index) pass))))

(defn- cycle-pass-tile-format-validation
  "Validates the formats of cycle, passes and tiles.
  cycle and pass must be natural numbers, tile is in the format of \"\\d+[LRF]\""
  [params]
  (let [{:keys [cycle passes]} params
        cycle-values (cycle-param->cycle-values cycle)]
    (concat (mapcat #(validate-natural-number :cycle %) cycle-values)
            (mapcat validate-pass passes)
            (mapcat validate-tiles passes))))

(defn- cycle-exist-with-passes-validation
  "Validates that cycle must be provided when searching with passes."
  [cycle-values passes]
  (when (seq passes)
    (if (empty? cycle-values)
      ["Cycle value must be provided when searching with passes."]
      (when (> (count cycle-values) 1)
        [(format (str "There can only be one cycle value when searching with passes, "
                      "but was %s.")
                 cycle-values)]))))

(defn- basic-cycle-pass-validation
  "Validates the basic count and structure validation of cycle, passes and tiles."
  [params]
  (let [{:keys [cycle passes]} params
        cycle-values (cycle-param->cycle-values cycle)]
    (concat (cycle-exist-with-passes-validation cycle-values passes)
            (passes-validation-for-field params))))

(defn cycle-pass-tile-validation
  "Validates cycle pass tile track info for a granule."
  [_ params]
  (let [basic-errors (basic-cycle-pass-validation params)]
    (if (seq basic-errors)
      basic-errors
      ;; no basic validation errors, continue with cyle, pass, tile validations
      (cycle-pass-tile-format-validation params))))
