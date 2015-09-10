(ns cmr.search.services.tagging-service-messages
  "This contains error response messages for the tagging service")

(def token-required-for-tag-modification
  "Tags cannot be modified without a valid user token.")

(defn tag-already-exists
  [tag concept-id]
  (format "A tag with namespace [%s] and value [%s] already exists with concept id %s."
          (:namespace tag) (:value tag) concept-id))