(ns cmr.transmit.echo.conversion
  "Contains functions for converting between CMR style and ECHO style objects"
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [cmr.common.util :as util]
            [camel-snake-kebab :as csk]))


(defn- echo-ace->cmr-ace
  "Cleans up the access control entry of an ACL."
  [ace]
  (let [{:keys [permissions sid]} ace
        permissions (mapv (comp keyword str/lower-case) permissions)
        group-guid (get-in sid [:group-sid :group-guid])
        user-type (some-> sid
                          :user-authorization-type-sid
                          :user-authorization-type
                          str/lower-case
                          keyword)]
    (if group-guid
      {:permissions permissions
       :group-guid group-guid}
      {:permissions permissions
       :user-type user-type})))

(defn- echo-coll-id->cmr-coll-id
  [cid]
  (when-let [{:keys [collection-ids restriction-flag]} cid]
    (merge {}
           (when collection-ids
             {:entry-titles (mapv :data-set-id collection-ids)})
           (when restriction-flag
             {:access-value (set/rename-keys restriction-flag
                                             {:include-undefined-value :include-undefined})}))))

(defn echo-acl->cmr-acl
  "Cleans up the acl data structure to be easier to work with. See the in code comment in this namespace for an example."
  [acl]
  (-> acl
      :acl
      util/map-keys->kebab-case
      (set/rename-keys {:id :guid :access-control-entries :aces})
      (update-in [:aces] (partial mapv echo-ace->cmr-ace))
      (update-in [:catalog-item-identity :collection-identifier] echo-coll-id->cmr-coll-id)))

(defn cmr-ace->echo-ace
  [ace]
  (let [{:keys [permissions group-guid user-type]} ace]
    {:permissions (mapv (comp str/upper-case name) permissions)
     :sid (if group-guid
            {:group-sid {:group-guid group-guid}}
            {:user-authorization-type-sid
             {:user-authorization-type
              (str/upper-case (name user-type))}})}))

(defn cmr-coll-id->echo-coll-id
  [cid]
  (when-let [{:keys [entry-titles access-value]} cid]
    (merge {}
           (when entry-titles
             {:collection-ids (for [et entry-titles]
                                {:data-set-id et})})
           (when access-value
             {:restriction-flag
              (set/rename-keys access-value
                               {:include-undefined :include-undefined-value})}))))

(defn cmr-acl->echo-acl
  "Converts a cmr style acl back to the echo style. Converting echo->cmr->echo is lossy due to
  short names and version ids not being included. These are optional and don't impact enforcement
  so it's ok."
  [acl]
  (-> acl
      (update-in [:aces] (partial mapv cmr-ace->echo-ace))
      (update-in [:catalog-item-identity :collection-identifier] cmr-coll-id->echo-coll-id)
      (set/rename-keys {:guid :id :aces :access-control-entries})
      util/map-keys->snake_case
      (#(hash-map :acl %))))