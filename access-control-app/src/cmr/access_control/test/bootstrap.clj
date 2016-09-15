(ns cmr.access-control.test.bootstrap
  "Bootstraps the initial access control system so that the echo system token has minimal permissions"
  (:require
   [cmr.access-control.services.group-service :as group-service]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.transmit.config :as transmit-config]))

(def administrators-group
  {:name transmit-config/mock-echo-system-group
   :description "The group to which the mock echo system token belongs"
   :legacy-guid transmit-config/mock-echo-system-group-guid})

(defn bootstrap
  "Bootstraps data necessary for testing access control."
  [system]
  (info "Bootstrapping development data in access control")
  (let [context {:system system :token (transmit-config/echo-system-token)}
         {:keys [concept-id]} (group-service/create-group context administrators-group {:skip-acls? true})]
    (group-service/add-members context concept-id
                               [transmit-config/mock-echo-system-user]
                               {:skip-acls? true})))
