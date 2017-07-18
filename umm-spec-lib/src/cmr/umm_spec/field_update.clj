(ns cmr.umm-spec.field-update
 "Functions to apply an update of a particular type to a field-translation"
 (:require
  [cmr.common.mime-types :as mt]
  [cmr.common.util :as util]
  [cmr.umm-spec.date-util :as date-util]
  [cmr.umm-spec.umm-spec-core :as spec-core]))

(defn field-update-functions
  "Partial update functions for handling specific update cases"
  [umm]
  {[:Instruments] (partial util/update-in-each umm [:Platforms])})

(defn- value-matches?
  "Return true if the value is a match on find value."
  [find-value value]
  (= (select-keys value (keys find-value))
     find-value))

(defmulti apply-umm-list-update
  "Apply the umm update by type. Assumes that the update-field is a list in
  the umm collection. Update-field should be a vector to handle nested fields."
  (fn [update-type umm update-field update-value find-value]
    update-type))

(defmethod apply-umm-list-update :add-to-existing
  [update-type umm update-field update-value find-value]
  (update-in umm update-field #(conj % update-value)))

(defmethod apply-umm-list-update :clear-all-and-replace
  [update-type umm update-field update-value find-value]
  (assoc-in umm update-field [update-value]))

(defmethod apply-umm-list-update :find-and-remove
  [update-type umm update-field update-value find-value]
  (if (seq (get-in umm update-field))
    (update-in umm update-field #(remove (fn [x]
                                           (value-matches? find-value x))
                                         %))
    umm))

(defmethod apply-umm-list-update :find-and-replace
  [update-type umm update-field update-value find-value]
  (if (seq (get-in umm update-field))
    (let [update-value (util/remove-nil-keys update-value)]
      ;; For each entry in update-field, if we find it using the find params,
      ;; completely replace with update value
      (update-in umm update-field #(distinct (map (fn [x]
                                                    (if (value-matches? find-value x)
                                                      update-value
                                                      x))
                                                  %))))
    umm))

(defmethod apply-umm-list-update :find-and-update
  [update-type umm update-field update-value find-value]
  (if (seq (get-in umm update-field))
    (let [update-value (util/remove-nil-keys update-value)]
      ;; For each entry in update-field, if we find it using the find params,
      ;; update only the fields supplied in update-value with nils removed
      (update-in umm update-field #(distinct (map (fn [x]
                                                    (if (value-matches? find-value x)
                                                      (merge x update-value)
                                                      x))
                                                  %))))
    umm))

(defn apply-update
  "Apply an update to a umm record. Check for field-specific function and use that
  if exists, otherwise apply the default list update."
  [update-type umm update-field update-value find-value]
  (if-let [partial-update-fn (get (field-update-functions umm) update-field)]
    (partial-update-fn #(apply-umm-list-update update-type % update-field update-value find-value))
    (apply-umm-list-update update-type umm update-field update-value find-value)))


(defn update-concept
  "Apply an update to a raw concept. Convert to UMM, apply the update, and
  convert back to native format.

  Specify an update format to convert back to a different format than the
  original concept. If the update format is umm, sanitize the umm on translation
  so we make sure we are returning valid UMM."
  [context concept update-type update-field update-value find-value update-format]
  (let [{:keys [format metadata concept-type]} concept
        update-format (or update-format (:format concept))
        umm (spec-core/parse-metadata
             context concept-type format metadata {:sanitize? (= :umm-json (mt/format-key update-format))})
        umm (apply-update update-type umm update-field update-value find-value)
        umm (date-util/update-metadata-dates umm "UPDATE")]
    (spec-core/generate-metadata context umm update-format)))
