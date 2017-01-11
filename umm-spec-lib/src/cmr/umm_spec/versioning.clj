(ns cmr.umm-spec.versioning
  "Contains UMM JSON version defintions and helper functions."
  (:require [cmr.common.mime-types :as mt]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Important Constants

;; dynamic is here only for testing purposes
(def ^:dynamic versions
  "A sequence of valid UMM Schema versions, with the newest one last. This sequence must be updated
   when new schema versions are added to the CMR."
  ["1.0" "1.1" "1.2" "1.3" "1.4" "1.5" "1.6" "1.7" "1.8" "1.9"])

(def current-version
  "The current version of the UMM schema."
  (last versions))

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
