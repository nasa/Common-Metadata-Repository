(ns cmr.access-control.test.bootstrap
  "Bootstraps the initial access control system so that the echo system token has minimal permissions"
  (:require
   [clojure.string :as string]
   [cmr.access-control.services.group-service :as group-service]
   [cmr.common.log :refer [info]]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.metadata-db :as mdb-legacy]))

(defn- administrators-group
  "Returns a new instance of the administrators group for creation."
  []
  {:name (transmit-config/administrators-group-name)
   :description "CMR Administrators"
   :legacy-guid (transmit-config/administrators-group-legacy-guid)})

(defn bootstrap
  "Bootstraps data necessary for testing access control."
  [system]
  (info "Bootstrapping development data in access control")
  (let [context {:system system :token (transmit-config/echo-system-token)}
        ;; Find or create the administrators group.
        {:keys [concept-id]} (or (mdb-legacy/find-latest-concept
                                  context
                                  {:native-id (string/lower-case (transmit-config/administrators-group-name))
                                   :provider-id "CMR"
                                   :exclude-metadata true}
                                  :access-group)
                                 (group-service/create-group context (administrators-group) {:skip-acls? true}))]
    ;; Add the echo system user to the group. Add members properly handles adding duplicate members
    ;; so there shouldn't be a problem.
    (group-service/add-members context concept-id
                               [(transmit-config/echo-system-username)
                                transmit-config/local-system-test-user]
                               {:skip-acls? true
                                :skip-member-validation? true})))


(comment 
 (bootstrap (get-in user/system [:apps :access-control])))
