(ns cmr.common-app.humanizer
  "Implements transforms to 'humanize' faceted fields on UMM collections.
  See https://wiki.earthdata.nasa.gov/display/CMR/Humanizing+Facets+Design"
  (:require
    [clojure.string :as str]
    [cmr.common.util :as util]))

(def humanizer-field->umm-paths
  "Map of humanizer JSON field names to lists of paths into parsed UMM collections
  corresponding to those fields."
  {"platform" [[:Platforms :ShortName]]
   "instrument" [[:Platforms :Instruments :ShortName]]
   "science_keyword" [[:ScienceKeywords :Category]
                      [:ScienceKeywords :Topic]
                      [:ScienceKeywords :Term]
                      [:ScienceKeywords :VariableLevel1]
                      [:ScienceKeywords :VariableLevel2]
                      [:ScienceKeywords :VariableLevel3]
                      [:ScienceKeywords :DetailedVariable]]
   "project" [[:Projects :ShortName]]
   "processing_level" [[:ProcessingLevel :Id]]
   "organization" [[:DataCenters :ShortName]]
   "tiling_system_name" [[:TilingIdentificationSystems :TilingIdentificationSystemName]]
   "granule_data_format" [[:ArchiveAndDistributionInformation :FileDistributionInformation :Format]]})

(defmulti to-human
  "Map of humanizer JSON type values to functions which take a field value and
  humanizer configuration and return a transformed field value. The functions
  can assume that the humanizer should be applied to the value."
  (fn [humanizer value]
    (:type humanizer)))

(defmethod to-human "trim_whitespace"
  [humanizer value]
  (assoc value
         :value (str/trim (str/replace (:value value) #"\s+" " "))))

(defmethod to-human "capitalize"
  [humanizer value]
  (assoc value
         :value (->> (str/split (:value value) #"\b")
                     (map str/capitalize)
                     str/join)))

(defmethod to-human "alias"
  [humanizer value]
  (assoc value
         :value (:replacement_value humanizer)))

(defmethod to-human "ignore"
  [humanizer value]
  nil)

(defmethod to-human "priority"
  [humanizer value]
  (assoc value
         :priority (:priority humanizer)))

(defn- transform-in-all
  "(Convenience method) Similar to update-in-all but calls fn with the parent of the
  value at path and the final key of path."
  [obj path f & args]
  (apply util/update-in-all obj (pop path) f (peek path) args))

(defn humanizer-key
  "Prefixes a key with the humanizer namespace"
  [key]
  (keyword "cmr-humanized" (name key)))

(defn- humanizer-matches?
  "Tests whether the given humanizer config applies to parent[key]"
  [parent key humanizer]
  (let [match-value (:source_value humanizer)
        value (get-in parent [(humanizer-key key) :value])]
    (and (some? value)
         (or (nil? match-value)
             (= match-value value)))))

(defn- assoc-humanized
  "Returns parent with a humanized version of the field at source-key"
  [parent source-key humanizer]
  (if (humanizer-matches? parent source-key humanizer)
    (let [target-key (humanizer-key source-key)
          value-at-key (get parent target-key)
          humanized-value (to-human humanizer value-at-key)
          humanized-value (if (:reportable humanizer)
                            (assoc humanized-value :reportable true)
                            humanized-value)]
      (assoc parent target-key humanized-value))
    parent))

(defn- apply-humanizer
  "Applies the humanizer to the collection"
  [collection humanizer]
  (let [paths (humanizer-field->umm-paths (:field humanizer))]
    (reduce #(transform-in-all %1 %2 assoc-humanized humanizer) collection paths)))

(defn- add-humanizer-field
  "(Helper for add-humanizer-fields) Given a parent object and a key
  copies of parent[key] to a key with the humanizer namespace"
  [parent key]
  (assoc parent (humanizer-key key) {:value (get parent key) :priority 0}))

(defn- add-humanizer-fields
  "Duplicates all fields of the collection which could be humanized into keys
  with the humanizer namespace. This allows us to run the humanizers while keeping
  the original fields in place."
  [collection]
  (let [field-paths (apply concat (vals humanizer-field->umm-paths))]
    (reduce #(transform-in-all %1 %2 add-humanizer-field) collection field-paths)))

(defn umm-collection->umm-collection+humanizers
  "Applies humanizers to a parsed UMM-spec collection"
  [collection humanizers]
  (reduce apply-humanizer (add-humanizer-fields collection) (sort-by :order humanizers)))
