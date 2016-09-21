(ns cmr.access-control.test.bootstrap
  "Bootstraps the initial access control system so that the echo system token has minimal permissions"
  (:require
   [cmr.access-control.services.group-service :as group-service]
   [cmr.access-control.data.access-control-index :as ac-index]
   [cmr.common-app.services.search.elastic-search-index :as search-index]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.transmit.config :as transmit-config]))

(defn- administrators-group
  "Returns a new instance of the administrators group for creation."
  []
  {:name (transmit-config/administrators-group-name)
   :description "CMR Administrators"
   :legacy-guid (transmit-config/administrators-group-legacy-guid)})

(defn find-existing-group-with-name
  "Returns the concept id of an existing group with the given name."
  [context name]
  (let [query (qm/query {:concept-type :access-group
                         :condition (qm/string-condition :name name)
                         :skip-acls? true
                         :page-size 1
                         :result-format :query-specified
                         :result-fields [:concept-id]})
        response (qe/execute-query context query)]
    (-> response :items first :concept-id)))

(defn bootstrap
  "Bootstraps data necessary for testing access control."
  [system]
  (info "Bootstrapping development data in access control")

  (let [context {:system system :token (transmit-config/echo-system-token)}
        ;; Find or create the administrators group.
        {:keys [concept-id]} (or (find-existing-group-with-name context (transmit-config/administrators-group-name))
                                 (group-service/create-group context (administrators-group) {:skip-acls? true}))
        ;; Add the echo system user to the group. Add members properly handles adding duplicate members
        ;; so there shouldn't be a problem.
        {:keys [concept-id revision-id]} (group-service/add-members context concept-id
                                                                    [(transmit-config/echo-system-username)]
                                                                    {:skip-acls? true})]
    ;; Manually index the concept. We want the data to be available as soon as this function returns.
    (ac-index/index-concept-by-concept-id-revision-id context concept-id revision-id)
    (search-index/refresh context)))


(comment
 (bootstrap (get-in user/system [:apps :access-control])))
