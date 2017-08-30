(ns cmr.umm-spec.migration.characteristics-data-type-migration
  "Contains helper functions for migrating between different versions of UMM characteristics' data type."
  (:require
   [clojure.set :as set] 
   [clojure.string :as string]
   [cmr.common.util :as util :refer [update-in-each]]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn]
   [cmr.umm-spec.util :as umm-spec-util]))

(def mapping-up
  "Defines mappings of Characteristics' data type values from v1.9 to v1.10.
   Anything not in this map, and not equal to \"NOT APPLICABLE\", map it to \"STRING\"."
  {"TIME/DIRECTION (ASCENDING)" "STRING"
   "TIME/DIRECTION (DESCENDING)" "STRING"
   "VARCHAR" "STRING"
   "INTEGER" "INT"
   "RADIOCARBON DATES" "STRING"
   "STRING" "STRING"
   "FLOAT" "FLOAT"
   "INT" "INT"
   "BOOLEAN" "BOOLEAN"
   "DATE" "DATE"
   "TIME" "TIME"
   "DATETIME" "DATETIME"
   "DATE_STRING" "DATE_STRING"
   "TIME_STRING" "TIME_STRING"
   "DATETIME_STRING" "DATETIME_STRING"})

(defn migrate-data-type
  "Migrate the Charateristics' data type from string to enum."
  [characteristics]
  (when-let [data-type (:DataType characteristics)]
    (let [upper-case-data-type (string/upper-case data-type)]
      (when-not (= umm-spec-util/NOT-APPLICABLE upper-case-data-type)    
       (assoc characteristics :DataType (get mapping-up upper-case-data-type umm-spec-util/STRING))))))

(defn update-each-characteristics
  "Update all the characteristics in a given element: platform/instrument/child instrument."
  [element]
  (util/remove-nil-keys
    (-> element
        (update-in-each [:Characteristics] migrate-data-type)
        (update :Characteristics #(remove nil? %))
        (update :Characteristics seq))))
            
(defn migrate-child-instrument-characteristics-data-type
  "Migrate child instrument's characteristics' data types."
  [child-instrument]
  (-> child-instrument
      update-each-characteristics
      umm-cmn/map->InstrumentChildType)) 
 
(defn migrate-instrument-characteristics-data-type
  "Migrate instrument's characteristics' data types."
  [instrument]
  (-> instrument
      update-each-characteristics 
      (update-in-each [:ComposedOf] migrate-child-instrument-characteristics-data-type)
      umm-cmn/map->InstrumentType)) 

(defn migrate-platform-characteristics-data-type
  "Migrate platform's characteristics' data types."
  [platform]
  (-> platform
      update-each-characteristics 
      (update-in-each [:Instruments] migrate-instrument-characteristics-data-type)
      umm-cmn/map->PlatformType)) 
    
(defn migrate-up
  "Migrate Characteristics' data type from string to enum."
  [c]
  (-> c
      (update-in-each [:Platforms] migrate-platform-characteristics-data-type))) 
