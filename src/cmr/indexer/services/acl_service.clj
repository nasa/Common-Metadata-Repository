(ns cmr.indexer.services.acl-service
  "Contains code for retrieving and manipulating ACLs."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [cmr.common.services.errors :as errors]))

;; TODO this is currently in the indexer but the search application will need access to it as well.
;; We'll want to move it into its own library eventually. We should implement it appropriately.

(def supported-collection-identifier-keys
  #{:collection-ids :restriction-flag})

(defn coll-matches-collection-id?
  "Returns true if the collection matches the collection id"
  [coll collection-id]
  (let [{:keys [data-set-id short-name version]} collection-id
        {:keys [entry-title product access-value]} coll]
    (or (= data-set-id entry-title)
        (and (= short-name (:short-name product))
             (= version (:version-id product))))))

(defn coll-matches-restriction-flag-filter
  "Returns true if the collection matches the restriction flag filter"
  [coll restriction-flag-filter]
  (let [{:keys [min-value max-value include-undefined-value]} restriction-flag-filter]
    (when (and (not min-value) (not max-value) (not include-undefined-value))
      (errors/internal-error!
        "Encountered restriction flag filter where min and max were not set and include-undefined-value was false"))
    (if-let [^double access-value (:access-value coll)]
      ;; If there's no range specified then a collection without a value is restricted
      (when (or min-value max-value)
        (and (or (nil? min-value)
                 (>= access-value ^double min-value))
             (or (nil? max-value)
                 (<= access-value ^double max-value))))
      ;; collection's without a value will only be included if include-undefined-value is true
      include-undefined-value)))

(defn- coll-matches-collection-identifier?
  "Returns true if the collection matches the collection identifier"
  [coll coll-id]
  (let [{:keys [collection-ids restriction-flag]} coll-id]
    (and (or (empty? collection-ids)
             (some (partial coll-matches-collection-id? coll) collection-ids))
         (or (nil? restriction-flag)
             (coll-matches-restriction-flag-filter coll restriction-flag)))))

(defn coll-applicable-acl?
  "Returns true if the acl is applicable to the collection."
  [coll-prov-guid coll acl]
  (when-let [{:keys [collection-applicable
                     collection-identifier
                     provider-guid]} (:catalog-item-identity acl)]

    ;; Verify the collection identifier isn't using any unsupported ACL features.
    (when collection-identifier
      (let [unsupported-keys (set/difference (set (keys collection-identifier))
                                             supported-collection-identifier-keys)]
        (when-not (empty? unsupported-keys)
          (errors/internal-error!
            (format "The ACL with GUID %s had unsupported attributes set: %s"
                    (:guid acl)
                    (str/join ", " unsupported-keys))))))


    (and collection-applicable
         (= coll-prov-guid provider-guid)
         (or (nil? collection-identifier)
             (coll-matches-collection-identifier? coll collection-identifier)))))

(defn get-coll-permitted-group-ids
  "Returns the groups ids (group guids, 'guest', 'registered') that have permission to read
  this collection"
  [coll acls]

  ;; may also need some concept ids etc.

  ;; TODO (and unit test)
  ;; wait until after integrated to unit test.
  )