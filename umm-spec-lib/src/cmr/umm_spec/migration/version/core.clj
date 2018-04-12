(ns cmr.umm-spec.migration.version.core
  "Contains functions for migrating between versions of UMM schema."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common.mime-types :as mt]
   [cmr.common.log :as log]
   [cmr.common.util :as util :refer [update-in-each]]
   [cmr.umm-spec.dif-util :as dif-util]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.migration.contact-information-migration :as ci]
   [cmr.umm-spec.migration.distance-units-migration :as distance-units-migration]
   [cmr.umm-spec.migration.geographic-coordinate-units-migration :as geographic-coordinate-units-migration]
   [cmr.umm-spec.migration.organization-personnel-migration :as op]
   [cmr.umm-spec.migration.related-url-migration :as related-url]
   [cmr.umm-spec.migration.spatial-extent-migration :as spatial-extent]
   [cmr.umm-spec.migration.version.collection]
   [cmr.umm-spec.migration.version.interface :as interface]
   [cmr.umm-spec.migration.version.variable]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.versioning :refer [versions current-version]]
   [cmr.umm-spec.xml-to-umm-mappings.characteristics-data-type-normalization :as char-data-type-normalization]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility Functions

(defn- customized-compare
  "Customizing the compare because normal string compare results in
   1.n > 1.10 and doesn't convert the version_steps correctly(1< n <10).
   Assuming the version only contains two parts part1.part2.
   both part1 and part2 are integers."
  [begin end]
  (let [begin-parts (string/split begin #"\.")
        end-parts (string/split end #"\.")
        begin-part1 (read-string (first begin-parts))
        begin-part2 (read-string (last begin-parts))
        end-part1 (read-string (first end-parts))
        end-part2 (read-string (last end-parts))
        part1-compare (- begin-part1 end-part1)
        part2-compare (- begin-part2 end-part2)]
    (if (zero? part1-compare)
      part2-compare
      part1-compare)))

(defn- version-steps
  "Returns a sequence of version steps between begin and end, inclusive."
  [concept-type begin end]
  (->> (condp #(%1 %2) (customized-compare begin end)
         neg?  (sort-by count (versions concept-type))
         zero? nil
         pos?  (reverse (sort-by count (versions concept-type))))
       (partition 2 1 nil)
       (drop-while #(not= (first %) begin))
       (take-while #(not= (first %) end))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public Migration Interface

(defn migrate-umm
  [context concept-type source-version dest-version data]
  (log/info "concept-type" concept-type)
  (log/info "source-version" source-version)
  (log/info "dest-version" dest-version)
  (log/info "data" data)
  (if (= source-version dest-version)
    data
    ;; Migrating across versions is just reducing over the discrete steps between each version.
    (reduce (fn [data [v1 v2]]
              (interface/migrate-umm-version context data concept-type v1 v2))
            data
            (version-steps concept-type source-version dest-version))))
