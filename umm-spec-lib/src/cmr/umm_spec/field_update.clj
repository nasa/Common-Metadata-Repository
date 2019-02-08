(ns cmr.umm-spec.field-update
 "Functions to apply an update of a particular type to a field-translation"
 (:require
  [cmr.common.mime-types :as mt]
  [cmr.common.util :as util]
  [cmr.umm-spec.date-util :as date-util]
  [cmr.umm-spec.umm-spec-core :as spec-core]))

(def data-center-url
  "Data center URL's content type."
  "DataCenterURL")

(def home-page
  "Data center URL's type."
  "HOME PAGE")

(defn field-update-functions
  "Partial update functions for handling nested update-field like [:Instruments].
   We might support more nested update-field in the future."
  [umm]
  {[:Instruments] (partial util/update-in-each umm [:Platforms])})

(defn mapping-snuf-and-its-values
  "Returns a map of snuf: supported nested update-field, and all its values in umm.
   Currently bulk update only supports one nested update-field [:Instruments]."
  [umm]
  {[:Instruments] (for [platform (:Platforms umm)
                        instrument (:Instruments platform)]
                    instrument)})

(def update-types-associated-with-find-operations
 "All the update types that are associated with the find operations."
 [:find-and-remove :find-and-replace :find-and-update :find-and-update-home-page-url])

(defn- get-home-page-url
  "Get the data center home page url from a list of related urls.
   If there are more than one returned, arbitrarily choose the first one."
  [related-urls]
  (first (for [{:keys [URLContentType Type URL]} related-urls
               :when (and (= data-center-url URLContentType)
                          (= home-page Type))]
           {:URLContentType data-center-url
            :Type home-page
            :URL URL})))

(defn- home-page-url?
  "Check if a related url is a data center home page url."
  [related-url]
  (let [{:keys [URLContentType Type]} related-url]
    (and (= data-center-url URLContentType) (= home-page Type))))

(defn- home-page-url-update
  "Apply special home-page-url update to data-center using the update-value.
   If the new home-page-url is provided in the update-value.
      a. If data-center-related-urls contains home-page-url, update it with the new home-page-url.
      b. If data-center-related-urls doesn't contain home-page-url, add the new home-page-url to it.
   If the new home-page-url is not present in update-value, remove the home-page-url from data center.
   Note: Anything other than the home-page-url in update-value are ignored."
  [data-center update-value]
  (let [update-value-related-urls (get-in update-value [:ContactInformation :RelatedUrls])
        update-value-home-page-url (get-home-page-url update-value-related-urls)
        data-center-related-urls (get-in data-center [:ContactInformation :RelatedUrls])]
    (if (seq update-value-home-page-url)
      (if (some home-page-url? data-center-related-urls)
        (update-in data-center [:ContactInformation :RelatedUrls]
          #(map (fn [x] (if (home-page-url? x) update-value-home-page-url x)) %))
        (let [new-related-urls (conj data-center-related-urls update-value-home-page-url)]
          (assoc-in data-center [:ContactInformation :RelatedUrls] new-related-urls)))
      (update-in data-center [:ContactInformation :RelatedUrls]
          #(remove home-page-url? %)))))

(defn- remove-data-center-empty-related-urls
  "Remove RelatedUrls from data center's ContactInformation if it's empty"
  [data-center]
  (if (seq (get-in data-center [:ContactInformation :RelatedUrls]))
    data-center
    (update-in data-center [:ContactInformation] dissoc :RelatedUrls)))

(defn- remove-data-center-empty-contact-info
  "Remove data center's ContactInformation if it's empty"
  [data-center]
  (if (seq (:ContactInformation data-center))
    data-center
    (dissoc data-center :ContactInformation)))

(defn- data-center-update
  "Apply update to data-center using the update-value. Returns data center.
   It includes special update to the home page url inside ContactInformation
   and regular update to everything outside of ContactInformation."
  [data-center update-value]
  (let [update-value-non-contact-info-part (dissoc update-value :ContactInformation)
        data-center-with-updated-home-page-url (-> data-center
                                                   (home-page-url-update update-value)
                                                   remove-data-center-empty-related-urls
                                                   remove-data-center-empty-contact-info)]
    (merge data-center-with-updated-home-page-url update-value-non-contact-info-part)))

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

(defn- remove-duplicates
  "Remove duplicates in update-field, whoes value is [map(s)] for bulk updates."
  [umm update-field]
  (-> umm
      (util/update-in-each update-field util/remove-nil-keys)
      ;; In order to do distinct, convert the list of models to a list of maps
      (update-in update-field #(map (partial into {}) %))
      (update-in update-field distinct)))

(defn- find-value-exists-in-umm?
  "Check if the find-value exists in the umm update-field."
  [find-value umm update-field]
  ;; snuf stands for supported nested update-field.
  (if-let [snuf-values (get (mapping-snuf-and-its-values umm) update-field)]
    (some (partial value-matches? find-value) snuf-values)
    (some (partial value-matches? find-value) (get-in umm update-field))))

(defmethod apply-umm-list-update :add-to-existing
  [update-type umm update-field update-value find-value]
  (as-> umm umm
        (if (sequential? update-value)
          (update-in umm update-field #(concat % update-value))
          (update-in umm update-field #(conj % update-value)))
        (remove-duplicates umm update-field)))

(defmethod apply-umm-list-update :clear-all-and-replace
  [update-type umm update-field update-value find-value]
  (as-> umm umm
        (if (sequential? update-value)
          (assoc-in umm update-field update-value)
          (assoc-in umm update-field [update-value]))
        (remove-duplicates umm update-field)))

(defmethod apply-umm-list-update :find-and-remove
  [update-type umm update-field update-value find-value]
  (let [umm-updated (update-in umm update-field #(remove (fn [x]
                                                           (value-matches? find-value x))
                                                   %))]
    (if (seq (get-in umm-updated update-field))
      umm-updated
      (update-in umm-updated update-field seq))))

(defmethod apply-umm-list-update :find-and-replace
  [update-type umm update-field update-value find-value]
  (let [update-value (if (sequential? update-value)
                       (map #(util/remove-nil-keys %) update-value)
                       (util/remove-nil-keys update-value))
        ;; For each entry in update-field, if we find it using the find params,
        ;; completely replace with update value
        updated-umm (if (get-in umm update-field)
                      (update-in umm update-field #(flatten
                                                     (map (fn [x]
                                                            (if (value-matches? find-value x)
                                                              update-value
                                                              x))
                                                          %)))
                      umm)]
     (if (= updated-umm umm)
       umm
       (remove-duplicates updated-umm update-field))))

(defmethod apply-umm-list-update :find-and-update
  [update-type umm update-field update-value find-value]
  ;; For each entry in update-field, if we find it using the find params,
  ;; update only the fields supplied in update-value with nils removed
  (if (get-in umm update-field)
    (update-in umm update-field #(distinct (map (fn [x]
                                                  (if (value-matches? find-value x)
                                                    (merge x update-value)
                                                    x))
                                                %)))
    umm))

(defmethod apply-umm-list-update :find-and-update-home-page-url
  ;; This is a special case that applies to DataCenter ContactInformation update only.
  ;; All other updates outside of ContactInformation remains the same as find-and-update.
  ;; We only want to update the home page url inside the :ContactInformation :RelatedUrls.
  ;; We don't want to replace the :ContactInformation with the new one in update-value.
  ;; Any information other than the home page url value under :ContactInformation
  ;; in update-value is not used.
  [update-type umm update-field update-value find-value]
  ;; For each entry in update-field, if we find it using the find params,
  ;; update only the fields supplied in update-value with nils removed, except for the
  ;; ContactInformation part of the update-value.
  (update-in umm update-field #(distinct (map (fn [x]
                                                (if (value-matches? find-value x)
                                                  (data-center-update x update-value)
                                                  x))
                                              %))))


(defn- apply-list-update-nested
  "Apply the umm list update when the update-field is a supported nested field."
  [update-type umm update-field update-value find-value partial-update-fn]
  (if (some #(= update-type %) update-types-associated-with-find-operations)
    (when (find-value-exists-in-umm? find-value umm update-field)
      (partial-update-fn #(apply-umm-list-update update-type % update-field update-value find-value)))
    (partial-update-fn #(apply-umm-list-update update-type % update-field update-value find-value))))

(defn- apply-list-update-default
  "Apply the umm list update when the update-field is the top level field in umm."
  [update-type umm update-field update-value find-value]
  (if (some #(= update-type %) update-types-associated-with-find-operations)
    (when (find-value-exists-in-umm? find-value umm update-field)
      (apply-umm-list-update update-type umm update-field update-value find-value))
    (apply-umm-list-update update-type umm update-field update-value find-value)))

(defn apply-update
  "Apply an update to a umm record. Check for field-specific function and use that
  if update-field is a supported nested field, otherwise apply the default list update.
  Note: For all the find operations, if the find-value doesn't exist in umm, returns nil,
  which indicates that no update will be done."
  [update-type umm update-field update-value find-value]
  (if-let [partial-update-fn (get (field-update-functions umm) update-field)]
    (apply-list-update-nested update-type umm update-field update-value find-value partial-update-fn)
    (apply-list-update-default update-type umm update-field update-value find-value)))

(defn update-concept
  "Apply an update to a raw concept.
  Convert to UMM, apply the update, and convert back to the passed in update-format or native format
  if update-format is nil. Currently update-format is hard-coded to umm+json in bulk-update-service.
  Returns nil if apply-update returns nil - when the concept is not found through find-value."
  [context concept update-type update-field update-value find-value update-format]
  (let [{:keys [format metadata concept-type]} concept
        update-format (or update-format (:format concept))
        umm (spec-core/parse-metadata
             context concept-type format metadata {:sanitize? (= :umm-json (mt/format-key update-format))})]
    (when-let [umm (apply-update update-type umm update-field update-value find-value)]
      (let [umm (date-util/update-metadata-dates umm "UPDATE")]
        (spec-core/generate-metadata context umm update-format)))))
