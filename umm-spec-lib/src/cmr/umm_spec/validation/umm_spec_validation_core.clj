(ns cmr.umm-spec.validation.umm-spec-validation-core
  "Defines validations UMM concept types."
  (:require
   [clj-time.core :as t]
   [cmr.common.validations.core :as v]
   [cmr.umm-spec.validation.umm-spec-collection-validation :as vc]
   [cmr.umm-spec.validation.umm-spec-variable-validation :as vv]
   [cmr.umm-spec.validation.granule :as vg]
   [cmr.umm-spec.validation.parent-weaver :as pw]
   [cmr.umm-spec.additional-attribute :as aa]
   [cmr.common.services.errors :as e]
   [clojure.string :as str]
   [cmr.umm.iso-smap.granule.orbit]))

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
  "Validates the UMM record returning a list of error maps containing a path through the
  UMM model and a list of errors at that path. Returns an empty sequence if it is valid."
  ([collection]
   (validate-collection collection nil))
  ([collection additional-validations]
   (validation-errors->path-errors
    (v/validate (cons vc/collection-validations additional-validations)
                (aa/add-parsed-values collection)))))

(defn validate-collection-warnings
  "Validates the UMM record against the list of warnings - issues that we want
  to convey to the user, but not consider failures."
  ([collection]
   (validate-collection-warnings collection nil))
  ([collection additional-validations]
   (validation-errors->path-errors
    (v/validate (cons vc/collection-validation-warnings additional-validations)
                (aa/add-parsed-values collection)))))

(defn validate-granule
  "Validates the umm record returning a list of error maps containing a path through the
  UMM model and a list of errors at that path. Returns an empty sequence if it is valid."
  ([collection granule]
   (validate-granule collection granule nil))
  ([collection granule additional-validations]
   (let [granule-with-parent (pw/set-parent granule (aa/add-parsed-values collection))]
     (validation-errors->path-errors
      (v/validate (cons vg/granule-validations additional-validations) granule-with-parent)))))

(defn validate-granule-without-collection
  "Validates the granule alone, without a collection, returning a list of error
   maps containing a path through the UMM model and a list of errors at that
   path. Returns an empty sequence if it is valid."
  ([granule]
   (validate-granule granule nil))
  ([granule additional-validations]
   (validation-errors->path-errors (v/validate additional-validations granule))))

(defn validate-service
  "Validates the UMM record returning a list of error maps containing a path
  through the UMM model and a list of errors at that path. Returns an empty
  sequence if it is valid."
  ([service]
   (validate-service service nil))
  ([service additional-validations]
   (validation-errors->path-errors
    (v/validate (cons {} additional-validations)
                service))))

(defn validate-tool
  "Validates the UMM record returning a list of error maps containing a path
  through the UMM model and a list of errors at that path. Returns an empty
  sequence if it is valid."
  ([tool]
   (validate-tool tool nil))
  ([tool additional-validations]
   (validation-errors->path-errors
    (v/validate additional-validations tool)))) 

(defn validate-variable
  "Validates the UMM record returning a list of error maps containing a path
  through the UMM model and a list of errors at that path. Returns an empty
  sequence if it is valid."
  ([variable]
   (validate-variable variable nil))
  ([variable additional-validations]
   (validation-errors->path-errors
    (v/validate (cons vv/variable-validations additional-validations)
                variable))))

(defn validate-variable-warnings
  "Validates the UMM record against the list of warnings - issues that we want
  to convey to the user, but which we do not consider failures."
  ([variable]
   (validate-variable-warnings variable nil))
  ([variable additional-validations]
   (validation-errors->path-errors
    (v/validate (cons vv/variable-validation-warnings additional-validations)
                variable))))

(defn validate-variable-with-no-defaults
  "Similar to validate-variables, however this one has no default rules
  requiring all rules to be passed in.

  Validates the UMM record returning a list of error maps containing a path
  through the UMM model and a list of errors at that path. Returns an empty
  sequence if it is valid."
  [variable validations-rules]
   (validation-errors->path-errors (v/validate validations-rules variable)))
