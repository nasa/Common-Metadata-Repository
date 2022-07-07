(ns cmr.umm-spec.versioning
  "Contains UMM JSON version defintions and helper functions."
  (:require [cmr.common.mime-types :as mt]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Important Constants

;; dynamic is here only for testing purposes
(def ^:dynamic versions
  "A map of concept type to a sequence of valid UMM Schema versions, with the newest one last.
  The sequence must be updated when new schema versions are added for the concept type."
  {:collection ["1.0" "1.1" "1.2" "1.3" "1.4" "1.5" "1.6" "1.7" "1.8" "1.9" "1.10" "1.11" "1.12" "1.13" "1.14"
                "1.15" "1.15.1" "1.15.2" "1.15.3" "1.15.4" "1.15.5"
                "1.16" "1.16.1" "1.16.2" "1.16.3" "1.16.4" "1.16.5" "1.16.6" "1.16.7"
                "1.17.0" "1.17.1"]
   :granule ["1.4" "1.5" "1.6" "1.6.1" "1.6.2" "1.6.3" "1.6.4"]
   :variable ["1.0" "1.1" "1.2" "1.3" "1.4" "1.5" "1.6" "1.7" "1.8" "1.8.1"]
   :service ["1.0" "1.1" "1.2" "1.3" "1.3.1" "1.3.2" "1.3.3" "1.3.4" "1.4" "1.4.1" "1.5.0"]
   :tool ["1.0" "1.1" "1.1.1" "1.2.0"]
   :subscription ["1.0" "1.1"]})

(def current-collection-version
  "The current version of the collection UMM schema."
  (-> versions :collection last))

(def current-granule-version
  "The current version of the granule UMM schema."
  (-> versions :granule last))

(def current-variable-version
  "The current version of the variable UMM schema."
  (-> versions :variable last))

(def current-service-version
  "The current version of the service UMM schema."
  (-> versions :service last))

(def current-tool-version
  "The current version of the tool UMM schema."
  (-> versions :tool last))

(def current-subscription-version
  "The current version of the subscription UMM schema."
  (-> versions :subscription last))

(defn current-version
  "Returns the current UMM version of the given concept type."
  [concept-type]
  ;; sometimes we can't figure out the exact concept-type for a search
  ;; and it is safe to assume that using collection as the default works for these cases
  ;; so we use the :collection as the default concept-type here
  (let [concept-type (or concept-type :collection)]
    (-> versions concept-type last)))

;;; Public Utilities for UMM Schema Version in Media Types

(defn with-default-version
  "Returns the media type with the current UMM version if no version parameter is specified."
  [concept-type media-type]
  (if (mt/version-of media-type)
    media-type
    (str media-type ";version=" (current-version concept-type))))
