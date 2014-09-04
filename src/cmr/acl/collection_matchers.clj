(ns cmr.acl.collection-matchers
  "Contains code for determining if a collection matches an acl"
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [cmr.common.services.errors :as errors]))

(def supported-collection-identifier-keys
  #{:entry-titles :access-value})

(defn coll-matches-collection-id?
  "Returns true if the collection matches the collection id"
  [coll collection-id]
  (= (:data-set-id collection-id) (:entry-title coll)))

(defn coll-matches-access-value-filter
  "Returns true if the collection matches the access-value filter"
  [coll access-value-filter]
  (let [{:keys [min-value max-value include-undefined]} access-value-filter]
    (when (and (not min-value) (not max-value) (not include-undefined))
      (errors/internal-error!
        "Encountered restriction flag filter where min and max were not set and include-undefined was false"))
    (if-let [^double access-value (:access-value coll)]
      ;; If there's no range specified then a collection without a value is restricted
      (when (or min-value max-value)
        (and (or (nil? min-value)
                 (>= access-value ^double min-value))
             (or (nil? max-value)
                 (<= access-value ^double max-value))))
      ;; collection's without a value will only be included if include-undefined is true
      include-undefined)))

(defn- coll-matches-collection-identifier?
  "Returns true if the collection matches the collection identifier"
  [coll coll-id]
  (let [coll-entry-title (:entry-title coll)
        {:keys [entry-titles access-value]} coll-id]
    (and (or (empty? entry-titles)
             (some (partial = coll-entry-title) entry-titles))
         (or (nil? access-value)
             (coll-matches-access-value-filter coll access-value)))))

(defn coll-applicable-acl?
  "Returns true if the acl is applicable to the collection."
  ([coll-prov-id coll acl]
   (coll-applicable-acl? coll-prov-id coll acl nil))
  ([coll-prov-id coll acl ignore-keys]
   (when-let [{:keys [collection-applicable
                      collection-identifier
                      provider-id]} (:catalog-item-identity acl)]

     ;; Verify the collection identifier isn't using any unsupported ACL features.
     (when collection-identifier
       (let [unsupported-keys (set/difference (set (keys collection-identifier))
                                              (reduce #(conj %1 %2)
                                                       supported-collection-identifier-keys
                                                       ignore-keys))]
         (when-not (empty? unsupported-keys)
           (errors/internal-error!
             (format "The ACL with GUID %s had unsupported attributes set: %s"
                     (:guid acl)
                     (str/join ", " unsupported-keys))))))


     (and collection-applicable
          (= coll-prov-id provider-id)
          (or (nil? collection-identifier)
              (coll-matches-collection-identifier? coll collection-identifier))))))
