(ns cmr.access-control.test.util
  (:require
   [clj-http.client :as client]
   [clojure.string :as str]
   [clojure.test :refer [is]]
   [cmr.access-control.data.access-control-index :as access-control-index]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.umm.umm-granule :as umm-g]
   [cmr.umm.umm-core :as umm-core]
   [cmr.elastic-utils.config :as es-config]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as config]
   [cmr.transmit.metadata-db2 :as mdb]
   [cmr.umm-spec.test.expected-conversion :refer [example-collection-record]]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(def conn-context-atom
  "An atom containing the cached connection context map."
  (atom nil))

(defn conn-context
  "Retrieves a context map that contains a connection to the access control app."
  []
  (when-not @conn-context-atom
    (reset! conn-context-atom {:system (config/system-with-connections
                                         {}
                                         [:ingest :access-control :echo-rest :metadata-db :urs])}))
  @conn-context-atom)

(defn refresh-elastic-index
  []
  (client/post (format "http://localhost:%s/_refresh" (es-config/elastic-port))))

(defn unindex-all-groups
  "Manually unindexes all groups from Elasticsearch"
  []
  (let [response (client/delete (format "http://localhost:%s/%s/%s/_query"
                                 (es-config/elastic-port)
                                 access-control-index/group-index-name
                                 access-control-index/group-type-name)
                                {:throw-exceptions false
                                 :body "{\"query\": {\"match_all\": {}}}"})]
    (when-not (= 200 (:status response))
      (throw (Exception. (str "Failed to unindex all groups:" (pr-str response)))))
    (refresh-elastic-index)))

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
   (merge {:name "Administrators2"
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
  ([token group {:keys [allow-failure?] :as options}]
   (let [options (merge {:raw? true :token token} options)
         {:keys [status] :as response} (process-response (ac/create-group (conn-context) group options))]
     (when (and (not allow-failure?)
                (or (> status 299) (< status 200)))
       (throw (Exception. (format "Unexpected status [%s] when creating group" (pr-str response)))))
     response)))

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
   (let [options (merge {:raw? true :token token} options)
         group (util/remove-nil-keys (dissoc group :concept_id :revision_id))]
     (process-response (ac/update-group (conn-context) concept-id group options)))))

(defn delete-group
  "Deletes a group"
  ([token concept-id]
   (delete-group token concept-id nil))
  ([token concept-id options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (ac/delete-group (conn-context) concept-id options)))))

(defn search-for-groups
  "Searches for groups using the given parameters"
  ([token params]
   (search-for-groups token params nil))
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
       (let [{:keys [revision_id status] :as resp} (add-members token (:concept_id group) members options)]
         (when-not (= status 200)
           (throw (Exception. (format "Unexpected status [%s] when adding members: %s" status (pr-str resp)))))
         (assoc group :revision_id revision_id))
       group))))

(defn ingest-group
  "Ingests the group and returns a group such that it can be matched with a search result."
  ([token attributes]
   (ingest-group token attributes nil))
  ([token attributes members]
   (let [group (make-group attributes)
         {:keys [concept_id status revision_id] :as resp} (create-group-with-members token group members)]
     (when-not (= status 200)
       (throw (Exception. (format "Unexpected status [%s] when creating group %s" status (pr-str resp)))))
     (assoc group
            :members members
            :concept_id concept_id
            :revision_id revision_id))))


(defn disable-publishing-messages
  "Configures metadata db to not publish messages for new data."
  []
  (side/eval-form `(mdb-config/set-publish-messages! false)))

(defn enable-publishing-messages
  "Configures metadata db to start publishing messages for new data it sees."
  []
  (side/eval-form `(mdb-config/set-publish-messages! true)))

(defmacro without-publishing-messages
  "Temporarily configures metadata db not to publish messages while executing the body."
  [& body]
  `(do
     (disable-publishing-messages)
     (try
       ~@body
       (finally
         (enable-publishing-messages)))))

(def granule-num
  "An atom storing the next number used to generate unique granules."
  (atom 0))

(defn save-granule
  "Saves a granule with given property map to metadata db and returns concept id."
  ([parent-collection-id]
   (save-granule parent-collection-id {}))
  ([parent-collection-id attrs]
   (let [short-name (str "gran" (swap! granule-num inc))
         version-id "v1"
         provider (if (:provider attrs)
                    (:provider attrs)
                    "PROV1")
         native-id short-name
         entry-id (str short-name "_" version-id)
         granule-ur (str short-name "ur")
         parent-collection (mdb/get-latest-concept (conn-context) parent-collection-id)
         parent-entry-title (:entry-title (:extra-fields parent-collection))
         timestamps (umm-g/map->DataProviderTimestamps
                     {:insert-time "2012-01-11T10:00:00.000Z"})
         granule-umm (umm-g/map->UmmGranule
                      {:granule-ur granule-ur
                       :data-provider-timestamps timestamps
                       :collection-ref (umm-g/map->CollectionRef
                                        {:entry-title parent-entry-title})})
         granule-umm (merge granule-umm attrs)]
     ;; We don't want to publish messages in metadata db since different envs may or may not be running
     ;; the indexer when we run this test.
     (without-publishing-messages
      (:concept-id
       (mdb/save-concept (conn-context)
                         {:format "application/echo10+xml"
                          :metadata (umm-core/umm->xml granule-umm :echo10)
                          :concept-type :granule
                          :provider-id provider
                          :native-id native-id
                          :revision-id 1
                          :extra-fields {:short-name short-name
                                         :entry-title short-name
                                         :entry-id entry-id
                                         :granule-ur granule-ur
                                         :version-id version-id
                                         :parent-collection-id parent-collection-id
                                         :parent-entry-title parent-entry-title}}))))))


(defn save-collection
  "Test helper. Saves collection to Metadata DB and returns its concept id."
  [options]
  (let [{:keys [native-id entry-title short-name access-value provider-id temporal-range no-temporal temporal-singles]} options
        base-umm (-> example-collection-record
                     (assoc-in [:SpatialExtent :GranuleSpatialRepresentation] "NO_SPATIAL"))
        umm (cond-> base-umm
              entry-title (assoc :EntryTitle entry-title)
              short-name (assoc :ShortName short-name)
              (contains? options :access-value) (assoc-in [:AccessConstraints :Value] access-value)
              no-temporal (assoc :TemporalExtents nil)
              temporal-singles (assoc-in [:TemporalExtents 0 :SingleDateTimes] temporal-singles)
              temporal-singles (assoc-in [:TemporalExtents 0 :RangeDateTimes] nil)
              temporal-range (assoc-in [:TemporalExtents 0 :RangeDateTimes] [temporal-range]))]

    ;; We don't want to publish messages in metadata db since different envs may or may not be running
    ;; the indexer when we run this test.
    (without-publishing-messages
     (:concept-id
       (mdb/save-concept (conn-context)
                         {:format "application/echo10+xml"
                          :metadata (umm-spec/generate-metadata (conn-context) umm :echo10)
                          :concept-type :collection
                          :provider-id provider-id
                          :native-id native-id
                          :revision-id 1
                          :extra-fields {:short-name short-name
                                         :entry-title entry-title
                                         :entry-id short-name
                                         :version-id "v1"}})))))

(defn assert-group-saved
  "Checks that a group was persisted correctly in metadata db. The user-id indicates which user
  updated this revision."
  [group user-id concept-id revision-id]
  (let [concept (mdb/get-concept (conn-context) concept-id revision-id)]
    (is (= {:concept-type :access-group
            :provider-id (:provider_id group "CMR")
            :format mt/edn
            :metadata (pr-str (util/map-keys->kebab-case group))
            :user-id user-id
            :deleted false
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id :native-id)))))

(defn assert-group-deleted
  "Checks that a group tombstone was persisted correctly in metadata db."
  [group user-id concept-id revision-id]
  (let [concept (mdb/get-concept (conn-context) concept-id revision-id)]
    (is (= {:concept-type :access-group
            :provider-id (:provider_id group "CMR")
            :metadata ""
            :format mt/edn
            :user-id user-id
            :deleted true
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id :native-id)))))

(defn create-acl
  "Creates an acl."
  ([token acl]
   (create-acl token acl nil))
  ([token acl options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (ac/create-acl (conn-context) acl options)))))

(defn get-acl
  "Retrieves an ACL by concept id"
  ([token concept-id params]
   (process-response (ac/get-acl (conn-context) concept-id {:raw? true :token token :http-options {:query-params params}})))
  ([token concept-id]
   (get-acl token concept-id nil)))

(defn search-for-acls
  "Searches for groups using the given parameters"
  ([token params]
   (search-for-acls token params nil))
  ([token params options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (ac/search-for-acls (conn-context) params options)))))
