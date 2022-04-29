(ns cmr.umm-spec.migration.version.core
  "Contains functions for migrating between versions of UMM schema."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common.log :as log]
   [cmr.common.mime-types :as mt]
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
   [cmr.umm-spec.migration.version.granule]
   [cmr.umm-spec.migration.version.interface :as interface]
   [cmr.umm-spec.migration.version.service]
   [cmr.umm-spec.migration.version.subscription]
   [cmr.umm-spec.migration.version.tool]
   [cmr.umm-spec.migration.version.variable]
   [cmr.umm-spec.spatial-conversion :as spatial-conversion]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.versioning :as versioning]
   [cmr.umm-spec.xml-to-umm-mappings.characteristics-data-type-normalization :as char-data-type-normalization]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility Functions
(def ^:private values-are-equal 0)
(def ^:private begin-less-than-end -1)
(def ^:private begin-greater-than-end 1)

(defn- customized-compare
  "Customizing the compare because normal string compare results in
   1.n > 1.10 and doesn't convert the version_steps correctly(1< n <10).
   The inputs assume the version only contains integers separated by dots.
   This scheme allows version numbers like 1.10.2 and 1.10.3.25 etc."
  [begin end]
  (let [begin-tmp (map #(Integer/parseInt %) (clojure.string/split begin #"\."))
        end-tmp (map #(Integer/parseInt %) (clojure.string/split end #"\."))]
    (loop [begin begin-tmp
           end end-tmp]
      ;; if both are empty then the two values are equal.
      (if (and (empty? begin) (empty? end))
        values-are-equal
        ;; if the start is empty then it is smaller
        (if (empty? begin)
          begin-less-than-end
          ;; if the end is empty then start is bigger.
          (if (empty? end)
            begin-greater-than-end
            (let [result (- (first begin) (first end))]
              ;; If the result is zero the values are equal so go to the next number
              ;; and compare the next set. Otherwise return if start is greater or less than end.
              (if (zero? result)
                (recur (rest begin) (rest end))
                result))))))))

(defn- version-steps
  "Returns a sequence of version steps between begin and end, inclusive."
  [concept-type begin end]
  (->> (condp #(%1 %2) (customized-compare begin end)
         neg?  (versioning/versions concept-type)
         zero? nil
         pos?  (reverse (versioning/versions concept-type)))
       (partition 2 1 nil)
       (drop-while #(not= (first %) begin))
       (take-while #(not= (first %) end))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public Migration Interface

(defn migrate-umm
  [context concept-type source-version dest-version data]
  (if (= source-version dest-version)
    data
    ;; Migrating across versions is just reducing over the discrete steps between each version.
    (reduce (fn [data [v1 v2]]
              (interface/migrate-umm-version context data concept-type v1 v2))
            data
            (version-steps concept-type source-version dest-version))))
