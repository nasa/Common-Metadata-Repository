(ns cmr.indexer.data.concepts.humanizer
  "Implements transforms to 'humanize' faceted fields on UMM collections.
   See https://wiki.earthdata.nasa.gov/display/CMR/Humanizing+Facets+Design"
  (:require [clojure.string :as str]
            [clojure.tools.string-utils :as str-util])
  )

(def humanizer-cache
  "Cache of humanizers in the system. Currently this is just a static resource file. In the
  future, it'll come from a new concept and change periodically, so it's stored in an atom."
  (atom (cheshire.core/decode (slurp (clojure.java.io/resource "humanizers.json")) true)))

(defn- update-all-in
  "For nested maps, this is identical to clojure.core/update-in. If it encounters
   a sequential structure at one of the keys, though, it applies the update to each
   value in the sequence. If it encounters nil at one of the keys, it does nothing."
  [m [k & ks] f & args]
  (let [v (get m k)]
    (if (nil? v)
      m
      (if (sequential? v)
        (if ks
          (assoc m k (mapv #(apply update-all-in %1 ks f args) v))
          (assoc m k (mapv #(apply f %1 args) v)))
        (if ks
          (assoc m k (apply update-all-in v ks f args))
          (assoc m k (apply f v args)))))))

(defn- transform-all-in
  "(Convenience method) Similar to update-all-in but calls fn with the parent of the
   value at path and the final key of path."
  [obj path fn & args]
  (apply update-all-in obj (pop path) fn (peek path) args))


(def humanizer-field->umm-path
  "Map of humanizer JSON field names to lists of paths into parsed UMM collections
  corresponding to those fields."
  {
   "platform" [[:platforms :short-name]]
   "instrument" [[:platforms :instruments :short-name]]
   "science_keyword" [
                     [:science-keywords :category]
                     [:science-keywords :topic]
                     [:science-keywords :term]
                     [:science-keywords :variable-level-1]
                     [:science-keywords :variable-level-2]
                     [:science-keywords :variable-level-3]
                     [:science-keywords :detailed-variable]
                     ]
   "project" [[:projects :short-name]]
   "processing_level" [[:product :processing-level-id]]
   "organization" [[:organizations :org-name]]
   }
  )

(def humanizer-type->transform-fn
  "Map of humanizer JSON type values to functions which take a field value and
  humanizer configuration and return a transformed field value. The functions
  can assume that the humanizer should be applied to the value."
  {
   "trim_whitespace" (fn [value humanizer]
                       (str/trim value))
   "capitalize" (fn [value humanizer]
                  (->> (str/split value #"\b")
                       (map str/capitalize)
                       str/join))
   "alias" (fn [value humanizer]
             (:replacement_value humanizer))
   "ignore" (fn [value humanizer]
              nil)
   }
  )

(defn humanizer-key
  "Prefixes a key with the humanizer namespace"
  [key]
  (keyword "cmr.humanized" (name key)))

(defn- humanizer-matches?
  "Tests whether the given humanizer config applies to parent[key]"
  [parent key humanizer]
  (let [match-value (:source_value humanizer)
        value (get parent (humanizer-key key))]
    (and (some? value)
         (or (nil? match-value)
             (= match-value value)))))

(defn- assoc-humanized
  "Returns parent with a humanized version of the field at source-key"
  [parent source-key humanizer]
  (if (humanizer-matches? parent source-key humanizer)
    (let [target-key (humanizer-key source-key)
          value-at-key (get parent target-key)
          humanizer-fn (humanizer-type->transform-fn (:type humanizer))]
      (assoc parent target-key (humanizer-fn value-at-key humanizer)))
    parent))

(defn- apply-humanizer
  "Applies the humanizer to the collection"
  [collection humanizer]
  (let [paths (humanizer-field->umm-path (:field humanizer))]
    (reduce #(transform-all-in %1 %2 assoc-humanized humanizer) collection paths)))

(defn- add-humanizer-field
  "(Helper for add-humanizer-fields) Given a parent object and a key
  copies of parent[key] to a key with the humanizer namespace"
  [parent key]
  (assoc parent (humanizer-key key) (get parent key)))

(defn- add-humanizer-fields
  "Duplicates all fields of the collection which could be humanized into keys
  with the humanizer namespace. This allows us to run the humanizers while keeping
  the original fields in place."
  [collection]
  (let [field-paths (reduce concat (vals humanizer-field->umm-path))]
    (reduce #(transform-all-in %1 %2 add-humanizer-field) collection field-paths)))

(defn umm-collection->umm-collection+humanizers
  "Applies humanizers to a parsed UMM collection"
  ([collection]
   (umm-collection->umm-collection+humanizers collection @humanizer-cache))

  ([collection humanizers]
   (reduce apply-humanizer (add-humanizer-fields collection) (sort-by :priority humanizers))))
