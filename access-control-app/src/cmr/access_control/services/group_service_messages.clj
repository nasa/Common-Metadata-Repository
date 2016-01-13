(ns cmr.access-control.services.group-service-messages)

(def token-required-for-group-modification
  "Groups cannot be modified without a valid user token.")

(defn group-already-exists
  [group concept-id]
  (format "A system group with name [%s] already exists with concept id %s."
          (:name group) concept-id))
