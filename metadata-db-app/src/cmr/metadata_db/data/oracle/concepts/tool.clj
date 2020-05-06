(ns cmr.metadata-db.data.oracle.concepts.tool
  "Implements multi-method variations for tools."
  (:require
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :tool
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :tool)
          (assoc :provider-id (:provider_id result))
          (assoc :user-id (:user_id result))
          (assoc-in [:extra-fields :tool-name] (:tool_name result))))

(defn- tool-concept->insert-args
  [concept]
  (let [{{:keys [tool-name]} :extra-fields
         user-id :user-id
         provider-id :provider-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["provider_id" "user_id" "tool_name"])
     (concat values [provider-id user-id tool-name])]))

(defmethod c/concept->insert-args [:tool false]
  [concept _]
  (tool-concept->insert-args concept))

(defmethod c/concept->insert-args [:tool true]
  [concept _]
  (tool-concept->insert-args concept))
