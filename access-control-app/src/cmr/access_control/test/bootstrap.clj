(ns cmr.access-control.test.bootstrap
  "Bootstraps the initial access control system so that the echo system token has minimal permissions"
  (:require
   [cmr.access-control.services.group-service :as group-service]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.transmit.config :as transmit-config]))

(defn- administrators-group
  "Returns a new instance of the administrators group for creation."
  []
  {:name (transmit-config/administrators-group-name)
   :description "CMR Administrators"
   :legacy_guid (transmit-config/administrators-group-legacy-guid)})

(comment
 (def context {:system (get-in user/system [:apps :access-control])}))



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

 ;; TODO system integration tests to write
 ;; after reset the goup exists
 ;; bootstrap after reset works.
 ;; group does not contain duplicate members.
 ;; Add a member to the group and bootstrap does not remove other members
 ;; Manually remove echo system user and then bootstrap re-adds the member.

 ;; TODO Q: If every application starts up and runs this there is a race condition to be the first to
 ;; create/update the group. Running in application startup seems like overkill but then how do we automate it
 ;; to make sure it always runs?
 ;; Answer: DB migrations. Deploy will run that. The problem with that is this is going to make requests
 ;; to other systems like metadata db. Is that up yet?
 ;; Alternative Answer: Do something to completely avoid any race condition. Handle the possibility
 ;; of the same thing running on 5 other hosts all at the same time.
 ;; This will be really difficult. The find might return false but the goup is actually already created.
 ;; Alternative: Let it fail. One will succeed. Others will be successful.
 ;; Deployment time seems like best answer.

(defn bootstrap
  "Bootstraps data necessary for testing access control."
  [system]
  (info "Bootstrapping development data in access control")

  (let [context {:system system :token (transmit-config/echo-system-token)}
        ;; Find or create the administrators group.
        {:keys [concept-id]} (or (find-existing-group-with-name context (transmit-config/administrators-group-name))
                                 (group-service/create-group context (administrators-group) {:skip-acls? true}))]
    ;; Add the echo system user to the group. Add members properly handles adding duplicate members
    ;; so there shouldn't be a problem.
    (group-service/add-members context concept-id
                               [(transmit-config/echo-system-username)]
                               {:skip-acls? true})))
