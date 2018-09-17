(ns cmr.umm.validation.validation-core
  "Defines validations UMM concept types."
  (:require
   [clj-time.core :as t]
   [cmr.common.validations.core :as v]
   [cmr.umm.validation.collection-validation :as vc]
   [cmr.umm.validation.granule :as vg]
   [cmr.umm.validation.parent-weaver :as pw]
   [cmr.umm.validation.validation-utils :as vu]

   ;; Required so that the spatial validations will be available.
   [cmr.spatial.ring-validations]
   [cmr.common.services.errors :as e]
   [cmr.umm.iso-smap.granule.orbit]
   [clojure.string :as str]))

(defmulti humanize-field-for-error-msg
  "Converts a field name into an easier to read field name.  This should only be used when
  constructing error messages."
  (fn [field-path-item] (type field-path-item)))

(defmethod humanize-field-for-error-msg :default
  [field-path-item]
  field-path-item)

(defmethod humanize-field-for-error-msg clojure.lang.Keyword
  [field-path-item]
  (-> field-path-item
      name
      (str/replace #"-refs$" "-references")
      (str/replace #"-ref$" "-reference")
      keyword))

(defn- validation-errors->path-errors
  "Converts a validation error map to a list of path errors."
  [validation-errors]
  (for [[field-path errors] validation-errors]
    (e/map->PathErrors
     {:path field-path
      :errors (mapv (partial v/create-error-message
                             (map humanize-field-for-error-msg field-path)) errors)})))

(defn validate-collection
  "Validates the umm record returning a list of error maps containing a path through the
  UMM model and a list of errors at that path. Returns an empty sequence if it is valid."
  ([collection]
   (validate-collection collection nil))
  ([collection additional-validations]
   (validation-errors->path-errors
     (v/validate (cons vc/collection-validations additional-validations)
                 collection))))

(defn validate-granule
  "Validates the umm record returning a list of error maps containing a path through the
  UMM model and a list of errors at that path. Returns an empty sequence if it is valid."
  [collection granule]
  (validation-errors->path-errors
    (v/validate vg/granule-validations (pw/set-parent granule collection))))
