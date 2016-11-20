(ns cmr.umm.validation.validation-core
  "Defines validations UMM concept types."
  (:require 
    [clj-time.core :as t]
    [clojure.string :as str]
    [cmr.common.services.errors :as e]
    [cmr.common.validations.core :as v]
    [cmr.search.services.humanizers.humanizer-service :as humanizer-service]
    [cmr.spatial.ring-validations]
    [cmr.umm.validation.collection-validation :as vc]
    [cmr.umm.validation.granule :as vg]
    [cmr.umm.validation.parent-weaver :as pw]
    [cmr.umm.validation.validation-utils :as vu]))

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
  ([collection granule]
   (validate-granule nil collection granule))
  ([context collection granule]
   (let [humanizer (try
                     (humanizer-service/get-humanizers context)
                     (catch Throwable e
                       nil))
         ;; if collection platform shortname = replacement_value in the humanizer,
         ;; we might need to validate against the old platform shortname value.
         unhumanized-plat (when-not (nil? humanizer)
                            (:source_value (first (filter #(and (= (:type %) "alias")
                                                                (= (:field %) "platform")
                                                                (= (:replacement_value %)
                                                                   (:short-name (first (:platforms collection)))))
                                                    humanizer))))
         coll-unhumanized-plat (when-not (nil? unhumanized-plat)
                                 (update-in (update-in (update-in collection [:platforms] first)
                                              [:platforms] assoc :short-name unhumanized-plat)
                                   [:platforms] vector))                        
         gran-with-parent-unhumanized-plat (when-not (nil? coll-unhumanized-plat)
                                             (pw/set-parent granule coll-unhumanized-plat))
         gran-with-parent (pw/set-parent granule collection)
         gran-to-validate (if (or (not= nil (:parent (first (:platform-refs  gran-with-parent))))
                                  (= nil gran-with-parent-unhumanized-plat))
                            gran-with-parent
                            gran-with-parent-unhumanized-plat)]
     (validation-errors->path-errors
       (v/validate vg/granule-validations gran-to-validate)))))
