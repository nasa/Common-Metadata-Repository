(ns cmr.access-control.services.group-service-messages)

(def token-required-for-group-modification
  "Groups cannot be modified without a valid user token.")

(defn group-already-exists
  [group concept-id]
  (if (:provider-id group)
    (format "A provider group with name [%s] already exists with concept id [%s] for provider [%s]."
            (:name group) concept-id (:provider-id group))
    (format "A system group with name [%s] already exists with concept id [%s]."
            (:name group) concept-id)))

(defn group-does-not-exist
  [concept-id]
  (format "Group could not be found with concept id [%s]" concept-id))

(defn bad-group-concept-id
  [concept-id]
  (format "[%s] is not a valid group concept id." concept-id))

(defn group-deleted
  [concept-id]
  (format "Group with concept id [%s] was deleted." concept-id))

(defn provider-does-not-exist
  [provider-id]
  (format "Provider with provider-id [%s] does not exist." provider-id))
