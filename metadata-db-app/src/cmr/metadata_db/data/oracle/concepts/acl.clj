(ns cmr.metadata-db.data.oracle.concepts.acl
  "Implements multi-methods for ACL concepts."
  (:require [cmr.metadata-db.data.oracle.concepts :as c]
            [cmr.metadata-db.data.oracle.concepts.group :as g]
            [cmr.common.util :as u]))

;; NOTE: borrowing Access Group functions to implement these methods, because they are identical

(defmethod c/db-result->concept-map :acl
  [_ db provider-id result]
  (some-> (c/db-result->concept-map :access-group db provider-id result)
          (assoc :concept-type :acl)
          (assoc-in [:extra-fields :acl-identity] (:acl_identity result))
          (assoc-in [:extra-fields :target-provider-id] (:target_provider_id result))
          (update-in [:extra-fields] u/remove-nil-keys)))

(defn- acl-insert-args
  [concept]
  (let [[cols vals] (g/group-concept->insert-args concept)]
    [(concat cols ["acl_identity" "target_provider_id"])
     (concat vals [(get-in concept [:extra-fields :acl-identity])
                   (get-in concept [:extra-fields :target-provider-id])])]))

(defmethod c/concept->insert-args [:acl false]
  [concept _]
  (acl-insert-args concept))

(defmethod c/concept->insert-args [:acl true]
  [concept _]
  (acl-insert-args concept))
