(ns cmr.search.services.acls.acl-cache-extension
  "This namespace adds some additional capabilities to the ACL cache. It manipulates the ACLs
  in the cache so they are easier to use with search."
  (:require [cmr.acl.acl-cache]
            [cmr.search.services.query-service :as query-service]))

(defn get-provider-entry-title-concept-id-map
  "Returns a map of provider to entry titles to concept ids in ECHO. Dynamically loads it using
  provider holdings"
  [context]
  (let [collections (query-service/get-collections-by-providers context true)]
    (into {} (for [[prov colls] (group-by :provider-id collections)]
               [prov (into {} (for [coll colls]
                                [(:entry-title coll) (:concept-id coll)]))]))))

(defn- update-collection-identifier
  "Updates the collection identifier in the ACL to add an additional key called :collections. The
  :collection key will be mapped to a list of collections for each entry title. It will contain
  additional information like concept id. Entry titles are left in the ACL so as to work with
  existing ACL functions."
  [provider-entry-title-concept-id-map acl]
  (let [{:keys [provider-id collection-identifier] :as cii} (:catalog-item-identity acl)
        entry-title-concept-id-map (get provider-entry-title-concept-id-map provider-id)
        entry-titles (:entry-titles collection-identifier)
        collections (for [entry-title entry-titles]
                      {:entry-title entry-title
                       :concept-id (get entry-title-concept-id-map entry-title)})]
    (assoc-in acl [:catalog-item-identity :collection-identifier]
              (some-> collection-identifier
                      (assoc :collections (seq collections))))))

(comment

  (get-provider-entry-title-concept-id-map
    {:system (get-in user/system [:apps :search])})

)

(defn handle-acl-cache-update
  "When ACLs are loaded in the cache this should be called to add addition fields."
  [context acls]
  (let [pecid-map (get-provider-entry-title-concept-id-map context)]
    (for [acl acls]
      (update-collection-identifier pecid-map acl))))

