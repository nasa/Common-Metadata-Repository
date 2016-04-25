(ns cmr.umm-spec.versioning
  "Contains functions for migrating between versions of UMM schema."
  (:require [clojure.set :as set]
            [cmr.common.mime-types :as mt]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Important Constants

(def versions
  "A sequence of valid UMM Schema versions, with the newest one last. This sequence must be updated
   when new schema versions are added to the CMR."
  ["1.0" "1.1"])

(def current-version
  "The current version of the UMM schema."
  (last versions))

(defn valid-version?
  [x]
  (boolean (some #{x} versions)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility Functions

(defn version-steps
  "Returns a sequence of version steps between begin and end, inclusive."
  [begin end]
  (->> (condp #(%1 %2) (compare begin end)
         neg?  (sort versions)
         zero? nil
         pos?  (reverse (sort versions)))
       (partition 2 1 nil)
       (drop-while #(not= (first %) begin))
       (take-while #(not= (first %) end))))

;;; Public Utilities for UMM Schema Version in Media Types

(defn with-default-version
  "Returns the media type with the current UMM version if no version parameter is specified."
  [media-type]
  (if (mt/version-of media-type)
    media-type
    (str media-type ";version=" current-version)))

(defn fix-concept-format
  "Fixes formats"
  [fmt]
  (if (mt/umm-json? fmt)
    (with-default-version fmt)
    fmt))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Migrating Between Versions

;; Private Migration Functions

(defn- dispatch-migrate
  [_ concept-type source-version dest-version]
  [concept-type source-version dest-version])

(defmulti ^:private migrate-umm-version
          "Returns the given data structure of the indicated concept type and UMM version updated to conform to the
           target UMM schema version."
          #'dispatch-migrate)

(defmethod migrate-umm-version :default
  [c & _]
  ;; Do nothing by default. This lets us skip over "holes" in the version sequence, where the UMM
  ;; version may be updated but a particular concept type's schema may not be affected.
  c)

;; Collection Migrations

(defmethod migrate-umm-version [:collection "1.0" "1.1"]
  [c & _]
  (-> c
      (update-in [:TilingIdentificationSystem] #(when % [%]))
      (set/rename-keys {:TilingIdentificationSystem :TilingIdentificationSystems})))

(defmethod migrate-umm-version [:collection "1.1" "1.0"]
  [c & _]
  (-> c
      (update-in [:TilingIdentificationSystems] first)
      (set/rename-keys {:TilingIdentificationSystems :TilingIdentificationSystem})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public Migration Interface

(defn migrate-umm
  [concept-type source-version dest-version data]
  (if (= source-version dest-version)
    data
    ;; Migrating across versions is just reducing over the discrete steps between each version.
    (reduce (fn [data [v1 v2]]
              (migrate-umm-version data concept-type v1 v2))
            data
            (version-steps source-version dest-version))))
