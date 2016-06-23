(ns cmr.metadata-db.data.oracle.concepts.acl
  "Implements multi-methods for ACL concepts."
  (:require [cmr.metadata-db.data.oracle.concepts :as c]
            [cmr.metadata-db.data.oracle.concepts.group :as g]))

;; NOTE: borrowing Access Group functions to implement these methods, because they are identical

(defmethod c/db-result->concept-map :acl
  [_ db provider-id result]
  (some-> (c/db-result->concept-map :access-group db provider-id result)
          (assoc :concept-type :acl)))

(defn- acl-insert-args
  [concept]
  (let [[cols vals] (g/group-concept->insert-args concept)]
    [(concat cols ["acl_identity"])
     (concat cols [(:acl-identity (:extra-fields concept))])]))

(defmethod c/concept->insert-args [:acl false]
  [concept _]
  (acl-insert-args concept))

(defmethod c/concept->insert-args [:acl true]
  [concept _]
  (acl-insert-args concept))
