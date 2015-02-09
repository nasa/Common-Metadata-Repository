(ns cmr.umm.validation.core
  "Defines validations UMM concept types."
  (:require [clj-time.core :as t]
            [cmr.common.validations.core :as v]
            [cmr.umm.validation.collection :as vc]
            [cmr.umm.validation.granule :as vg]
            [cmr.umm.validation.parent-weaver :as pw]
            [cmr.umm.validation.utils :as vu]
            [cmr.common.services.errors :as e]
            [clojure.string :as str]))

(defn- humanize-field
  "Converts a field name into an easier to read field name.  This should only be used when
  constructing error messages."
  [field-path-item]
  (when (or (keyword? field-path-item) (string? field-path-item))
    (-> field-path-item
        name
        (str/replace #"-refs" "-references"))))

(defn- validation-errors->path-errors
  "Converts a validation error map to a list of path errors."
  [validation-errors]
  (for [[field-path errors] validation-errors]
    (e/map->PathErrors
      {:path field-path
       :errors (map (partial v/create-error-message
                             (map humanize-field field-path)) errors)})))

(defn validate-collection
  "Validates the umm record returning a list of error maps containing a path through the
  UMM model and a list of errors at that path. Returns an empty sequence if it is valid."
  [collection]
  (validation-errors->path-errors
    (v/validate vc/collection-validations collection)))

(defn validate-granule
  "Validates the umm record returning a list of error maps containing a path through the
  UMM model and a list of errors at that path. Returns an empty sequence if it is valid."
  [collection granule]
  (validation-errors->path-errors
    (v/validate vg/granule-validations (pw/set-parent granule collection))))



