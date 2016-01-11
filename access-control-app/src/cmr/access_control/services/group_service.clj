(ns cmr.access-control.services.group-service
    "TODO document this"
    (:require [cmr.transmit.metadata-db2 :as mdb]
              [cmr.transmit.echo.tokens :as tokens]
              [cmr.common.services.errors :as errors]
              [cmr.common.mime-types :as mt]
              [cmr.access-control.services.group-service-messages :as msg]))

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (tokens/get-user-id context (:token context))
    (errors/throw-service-error :unauthorized msg/token-required-for-group-modification)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB Concept Map Manipulation

(defn- group->mdb-provider-id
  "Returns the provider id to use in metadata db for the group"
  [group]
  (get group :provider-id "CMR"))

(defn- group->new-concept
  "Converts a group into a new concept that can be persisted in metadata db."
  [context group]
  {:concept-type :access-group
   ;; TODO update the group design to indicate that the native id is name and provider id is set if present
   :native-id (:name group)
   ;; Provider id is optional in group. If it is a system level group then it's owned by the CMR.
   :provider-id (group->mdb-provider-id group)
   :metadata (pr-str group)
   :user-id (context->user-id context)
   ;; The first version of a group should always be revision id 1. We always specify a revision id
   ;; when saving groups to help avoid conflicts
   :revision-id 1
   :format mt/edn})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Service level functions

(defn create-group
  "TODO"
  [context group]
  ;; Check if the group already exists
  (if-let [concept-id (mdb/get-concept-id context
                                          :access-group
                                          (group->mdb-provider-id group)
                                          (:name group))]

    ;; The group exists. Check if its latest revision is a tombstone
    (let [concept (mdb/get-latest-concept context concept-id)]
      (if (:deleted concept)
        ;; The group exists but was previously deleted.
        (mdb/save-concept
         context
         (-> concept
             (assoc :metadata (pr-str group)
                    :deleted false
                    :user-id (context->user-id context))
             (dissoc :revision-date)
             (update-in [:revision-id] inc)))

        ;; The group exists and was not deleted. Reject this.
        (errors/throw-service-error :conflict (msg/group-already-exists group concept-id))))

    ;; The group doesn't exist
    (mdb/save-concept context (group->new-concept context group))))
