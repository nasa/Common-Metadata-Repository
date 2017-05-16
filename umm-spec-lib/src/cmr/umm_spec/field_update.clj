(ns cmr.umm-spec.field-update
 "Functions to apply an update of a particular type to a field-translation"
 (:require
  [cmr.umm-spec.umm-spec-core :as spec-core]))

(defn- value-matches?
  "Return true if the value is a match on find value."
  [find-value value]
  (= (select-keys value (keys find-value))
     find-value))

(defmulti apply-umm-update
  "Apply the umm update by type"
  (fn [update-type umm update-field update-value find-value]
    update-type))

(defmethod apply-umm-update :add-to-existing
  [update-type umm update-field update-value find-value]
  (update umm update-field #(conj % update-value)))

(defmethod apply-umm-update :clear-all-and-replace
  [update-type umm update-field update-value find-value]
  (assoc umm update-field [update-value]))

(defmethod apply-umm-update :find-and-remove
  [update-type umm update-field update-value find-value]
  (if (seq (get umm update-field))
    (update umm update-field #(remove (fn [x]
                                        (value-matches? find-value x))
                                      %))
    umm))

(defmethod apply-umm-update :find-and-replace
  [update-type umm update-field update-value find-value]
  (if (seq (get umm update-field))
    (update umm update-field #(map (fn [x]
                                     (if (value-matches? find-value x)
                                       update-value
                                       x))
                                   %))
    umm))

(defn update-concept
  "Apply an update to a raw concept. Convert to UMM, apply the update, and
  convert back to native format."
  [context concept update-type update-field update-value find-value]
  (let [{:keys [format metadata concept-type]} concept
        umm (spec-core/parse-metadata context concept-type format metadata {:sanitize? false})
        umm (apply-umm-update update-type umm update-field update-value find-value)]
    (spec-core/generate-metadata context umm (:format concept))))
