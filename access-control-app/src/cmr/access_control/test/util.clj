(ns cmr.access-control.test.util
  (:require [cmr.transmit.access-control :as ac]
            [clojure.test :refer [is]]
            [clj-http.client :as client]
            [cmr.transmit.config :as config]
            [cmr.transmit.metadata-db2 :as mdb]
            [cmr.elastic-utils.config :as es-config]
            [cmr.common.mime-types :as mt]
            [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]))

(def conn-context-atom
  "An atom containing the cached connection context map."
  (atom nil))

(defn conn-context
  "Retrieves a context map that contains a connection to the access control app."
  []
  (when-not @conn-context-atom
    (reset! conn-context-atom {:system (config/system-with-connections
                                         {}
                                         [:access-control :echo-rest :metadata-db :urs])}))
  @conn-context-atom)

(defn refresh-elastic-index
  []
  (client/post (format "http://localhost:%s/_refresh" (es-config/elastic-port))))

(defn wait-until-indexed
  "Waits until all messages are processed and then flushes the elasticsearch index"
  []
  (qb-side-api/wait-for-terminal-states)
  (refresh-elastic-index))

(defn make-group
  "Makes a valid group"
  ([]
   (make-group nil))
  ([attributes]
   (merge {:name "Administrators"
           :description "A very good group"}
          attributes)))

(defn- process-response
  "Takes an HTTP response that may have a parsed body. If the body was parsed into a JSON map then it
  will associate the status with the body otherwise returns a map of the unparsed body and status code."
  [{:keys [status body]}]
  (if (map? body)
    (assoc body :status status)
    {:status status
     :body body}))

(defn create-group
  "Creates a group."
  ([token group]
   (create-group token group nil))
  ([token group options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (ac/create-group (conn-context) group options)))))

(defn get-group
  "Retrieves a group by concept id"
  ([token concept-id params]
   (process-response (ac/get-group (conn-context) concept-id {:raw? true :token token :http-options {:query-params params}})))
  ([token concept-id]
    (get-group token concept-id nil)))

(defn update-group
  "Updates a group."
  ([token concept-id group]
   (update-group token concept-id group nil))
  ([token concept-id group options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (ac/update-group (conn-context) concept-id group options)))))

(defn delete-group
  "Deletes a group"
  ([token concept-id]
   (delete-group token concept-id nil))
  ([token concept-id options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (ac/delete-group (conn-context) concept-id options)))))

(defn search
  "Searches for groups using the given parameters"
  ([token params]
   (search token params nil))
  ([token params options]
   (let [options (merge {:raw? true :token token} options)]
    (process-response (ac/search-for-groups (conn-context) params options)))))

(defn add-members
  "Adds members to the group"
  ([token concept-id members]
   (add-members token concept-id members nil))
  ([token concept-id members options]
   (let [options (merge {:raw? true :token token} options)]
    (process-response (ac/add-members (conn-context) concept-id members options)))))

(defn remove-members
  "Removes members from the group"
  ([token concept-id members]
   (remove-members token concept-id members nil))
  ([token concept-id members options]
   (let [options (merge {:raw? true :token token} options)]
    (process-response (ac/remove-members (conn-context) concept-id members options)))))

(defn get-members
  "Gets members in the group"
  ([token concept-id]
   (get-members token concept-id nil))
  ([token concept-id options]
   (let [options (merge {:raw? true :token token} options)]
    (process-response (ac/get-members (conn-context) concept-id options)))))

(defn create-group-with-members
  "Creates a group with the given list of members."
  ([token group members]
   (create-group-with-members token group members nil))
  ([token group members options]
   (let [group (create-group token group options)]
     (if (seq members)
       (let [{:keys [revision-id status] :as resp} (add-members token (:concept-id group) members options)]
         (when-not (= status 200)
           (throw (Exception. (format "Unexpected status [%s] when adding members: %s" status (pr-str resp)))))
         (assoc group :revision-id revision-id))
       group))))

(defn assert-group-saved
  "Checks that a group was persisted correctly in metadata db. The user-id indicates which user
  updated this revision."
  [group user-id concept-id revision-id]
  (let [concept (mdb/get-concept (conn-context) concept-id revision-id)]
    (is (= {:concept-type :access-group
            :native-id (:name group)
            :provider-id (:provider-id group "CMR")
            :format mt/edn
            :metadata (pr-str group)
            :user-id user-id
            :deleted false
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))

(defn assert-group-deleted
  "Checks that a group tombstone was persisted correctly in metadata db."
  [group user-id concept-id revision-id]
  (let [concept (mdb/get-concept (conn-context) concept-id revision-id)]
    (is (= {:concept-type :access-group
            :native-id (:name group)
            :provider-id (:provider-id group "CMR")
            :metadata ""
            :format mt/edn
            :user-id user-id
            :deleted true
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))
