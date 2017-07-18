(ns cmr.metadata-db.int-test.utility
  "Contains various utility methods to support integration tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clj-http.conn-mgr :as conn-mgr]
   [clj-time.format :as f]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.util :as util]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.services.concept-service :as concept-service]
   [cmr.transmit.config :as transmit-config]
   [inflections.core :as inf]))

(def conn-mgr-atom (atom nil))

(defn conn-mgr
  "Returns the HTTP connection manager to use. This allows integration tests to use persistent
  HTTP connections"
  []
  (when-not @conn-mgr-atom
    (reset! conn-mgr-atom  (conn-mgr/make-reusable-conn-manager {}))))

(defn concepts-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/concepts/"))

(defn concept-id-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/concept-id/"))

(defn reset-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/reset"))

(defn old-revision-concept-cleanup-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/jobs/old-revision-concept-cleanup"))

(defn expired-concept-cleanup-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/jobs/expired-concept-cleanup"))

(defn providers-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/providers"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; utility methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; concepts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def granule-xml
  "Valid ECHO10 granule for concept generation"
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <RestrictionFlag>0.0</RestrictionFlag>
    <Orderable>false</Orderable>
  </Granule>")

(def collection-xml
  "Valid ECHO10 collection for concept generation"
  "<Collection>
    <ShortName>MINIMAL</ShortName>
    <VersionId>1</VersionId>
    <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
    <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
    <LongName>A minimal valid collection</LongName>
    <DataSetId>A minimal valid collection V 1</DataSetId>
    <Description>A minimal valid collection</Description>
    <Orderable>true</Orderable>
    <Visible>true</Visible>
  </Collection>")

(def service-edn
  (pr-str {:name "Some Service"
           :etc "TBD"}))

(def tag-edn
  "Valid EDN for tag metadata"
  (pr-str {:tag-key "org.nasa.something.ozone"
           :description "A very good tag"
           :originator-id "jnorton"}))

(def tag-association-edn
  "Valid EDN for tag association metadata"
  (pr-str {:tag-key "org.nasa.something.ozone"
           :associated-concept-id "C120000000-PROV1"
           :revision-id 1
           :value "Some Value"}))

(def group-edn
  "Valid EDN for group metadata"
  (pr-str {:name "LPDAAC_ECS Administrators"
            :provider-id "LPDAAC_ECS"
            :description "Contains users with permission to manage LPDAAC_ECS holdings."
            :members ["jsmith" "prevere" "ndrew"]}))

(def acl-edn
  (pr-str {:name "Some ACL"
           :etc "TBD"}))

(def humanizer-json
  (json/generate-string
    [{"type" "trim_whitespace", "field" "platform", "order" -100},
     {"type" "priority", "field" "platform", "source_value" "Aqua", "order" 10, "priority" 10}]))

(def variable-json
  (json/generate-string
    { "Name" "totCldH2OStdErr",
      "LongName" "totCldH2OStdErr",
      "Units" "",
      "DataType" "float",
      "DimensionsName" [
        "H2OFunc",
        "H2OPressureLay",
        "MWHingeSurf",
        "Cloud",
        "HingeSurf",
        "H2OPressureLev",
        "AIRSXTrack",
        "StdPressureLay",
        "CH4Func",
        "StdPressureLev",
        "COFunc",
        "O3Func",
        "AIRSTrack"
      ],
      "Dimensions" [ "11", "14", "7", "2", "100", "15", "3", "28", "10", "9" ],
      "ValidRange" nil,
      "Scale" "1.0",
      "Offset" "0.0",
      "FillValue" "-9999.0 ",
      "VariableType" "",
      "ScienceKeywords" []}))

(def variable-association-edn
  "Valid EDN for variable association metadata"
  (pr-str {:variable-name "totCldH2OStdErr"
           :associated-concept-id "C120000000-PROV1"
           :revision-id 1
           :value "Some Value"}))

(def concept-dummy-metadata
  "Index events are now created by MDB when concepts are saved. So the Indexer will attempt
  to look up the metadata for the concepts and parse it. So we need to provide valid
  metadata for each test concept we create. This maps concept-type to dummy data that can be used
  by default."
  {:collection collection-xml
   :granule granule-xml
   :service service-edn
   :tag tag-edn
   :tag-association tag-association-edn
   :access-group group-edn
   :acl acl-edn
   :humanizer humanizer-json
   :variable variable-json
   :variable-association variable-association-edn})

(defn- concept
  "Create a concept map for any concept type. "
  [provider-id concept-type uniq-num attributes]
  (merge {:native-id (str "native-id " uniq-num)
          :metadata (concept-type concept-dummy-metadata)
          :deleted false}
         attributes
         ;; concept-type and provider-id args take precedence over attributes
         {:provider-id provider-id
          :concept-type concept-type}))

(defn collection-concept
  "Creates a collection concept"
  ([provider-id uniq-num]
   (collection-concept provider-id uniq-num {}))
  ([provider-id uniq-num attributes]
   (let [short-name (str "short" uniq-num)
         version-id (str "V" uniq-num)
         ;; ensure that the required extra-fields are available but allow them to be
         ;; overridden in attributes
         extra-fields (merge {:short-name short-name
                              :version-id version-id
                              :entry-id (if version-id
                                          (str short-name "_" version-id)
                                          short-name)
                              :entry-title (str "dataset" uniq-num)
                              :delete-time nil}
                             (:extra-fields attributes))
         attributes (merge {:user-id (str "user" uniq-num)
                            :format "application/echo10+xml"
                            :extra-fields extra-fields}
                           (dissoc attributes :extra-fields))]
     (concept provider-id :collection uniq-num attributes))))

(defn granule-concept
  "Creates a granule concept"
  ([provider-id parent-collection uniq-num]
   (granule-concept provider-id parent-collection uniq-num {}))
  ([provider-id parent-collection uniq-num attributes]
   (let [extra-fields (merge {:parent-collection-id (:concept-id parent-collection)
                              :parent-entry-title (get-in parent-collection [:extra-fields :entry-title])
                              :delete-time nil
                              :granule-ur (str "granule-ur " uniq-num)}
                             (:extra-fields attributes))
         attributes (merge {:format "application/echo10+xml"
                            :extra-fields extra-fields}
                           (dissoc attributes :extra-fields))]
     (concept provider-id :granule uniq-num attributes))))

(defn tag-concept
 "Creates a tag concept"
 ([uniq-num]
  (tag-concept uniq-num {}))
 ([uniq-num attributes]
  (let [native-id (str "tag-key" uniq-num)
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/edn"
                           :native-id native-id}
                          attributes)]
    ;; no provider-id should be specified for tags
    (dissoc (concept nil :tag uniq-num attributes) :provider-id))))

(defn tag-association-concept
  "Creates a tag association concept"
  ([assoc-concept tag uniq-num]
   (tag-association-concept assoc-concept tag uniq-num {}))
  ([assoc-concept tag uniq-num attributes]
   (let [{:keys [concept-id revision-id]} assoc-concept
         tag-id (:native-id tag)
         user-id (str "user" uniq-num)
         native-id (string/join "/" [tag-id concept-id revision-id])
         extra-fields (merge {:associated-concept-id concept-id
                              :associated-revision-id revision-id
                              :tag-key tag-id}
                             (:extra-fields attributes))
         attributes (merge {:user-id user-id
                            :format "application/edn"
                            :native-id native-id
                            :extra-fields extra-fields}
                           (dissoc attributes :extra-fields))]
     ;; no provider-id should be specified for tag associations
     (dissoc (concept nil :tag-association uniq-num attributes) :provider-id))))

(defn group-concept
  "Creates a group concept"
  ([provider-id uniq-num]
   (group-concept provider-id uniq-num {}))
  ([provider-id uniq-num attributes]
   (let [attributes (merge {:user-id (str "user" uniq-num)
                            :format "application/edn"}
                           attributes)]
     (concept provider-id :access-group uniq-num attributes))))

(defn acl-concept
  "Returns an :acl concept map for use in integration tests."
  ([provider-id uniq-num]
   (acl-concept provider-id uniq-num {}))
  ([provider-id uniq-num attributes]
   (let [attributes (merge {:user-id (str "user" uniq-num)
                            :format "application/edn"
                            :extra-fields {:acl-identity (str "test-identity:" provider-id ":" uniq-num)}}
                           attributes)]
     (concept provider-id :acl uniq-num attributes))))

(defn service-concept
  "Creates a service concept"
  ([uniq-num]
   (service-concept uniq-num {}))
  ([uniq-num attributes]
   (let [extra-fields (merge {:service-name (str "service_name_" uniq-num)}
                             (:extra-fields attributes))
         attributes (merge {:user-id (str "user" uniq-num)
                            :format "application/edn"
                            :extra-fields extra-fields}
                           (dissoc attributes :extra-fields))]
     ;; no provider-id should be specified for services
     (dissoc (concept nil :service uniq-num attributes) :provider-id))))

(defn humanizer-concept
 "Creates a humanizer concept"
 ([uniq-num]
  (humanizer-concept uniq-num {}))
 ([uniq-num attributes]
  (let [native-id "humanizer"
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/json"
                           :native-id native-id}
                          attributes)]
    ;; no provider-id should be specified for humanizers
    (dissoc (concept nil :humanizer uniq-num attributes) :provider-id))))

(defn variable-concept
  "Creates a variabe concept"
  ([provider-id uniq-num]
   (variable-concept provider-id uniq-num {}))
  ([provider-id uniq-num attributes]
   (let [native-id (str "var-native" uniq-num)
         extra-fields (merge {:variable-name (str "var" uniq-num)
                              :measurement (str "measurement" uniq-num)}
                             (:extra-fields attributes))
         attributes (merge {:user-id (str "user" uniq-num)
                            :format "application/json"
                            :native-id native-id
                            :extra-fields extra-fields}
                           (dissoc attributes :extra-fields))]
     (concept provider-id :variable uniq-num attributes))))

(defn variable-association-concept
  "Creates a variable association concept"
  ([assoc-concept variable uniq-num]
  (variable-association-concept assoc-concept variable uniq-num {}))
  ([assoc-concept variable uniq-num attributes]
  (let [{:keys [concept-id revision-id]} assoc-concept
       variable-name (:native-id variable)
       user-id (str "user" uniq-num)
       native-id (string/join "/" [variable-name concept-id revision-id])
       extra-fields (merge {:associated-concept-id concept-id
                            :associated-revision-id revision-id
                            :variable-name variable-name}
                           (:extra-fields attributes))
       attributes (merge {:user-id user-id
                          :format "application/edn"
                          :native-id native-id
                          :extra-fields extra-fields}
                         (dissoc attributes :extra-fields))]
   ;; no provider-id should be specified for variable associations
   (dissoc (concept nil :variable-association uniq-num attributes) :provider-id))))

(defn assert-no-errors
  [save-result]
  (is (nil? (:errors save-result)))
  save-result)

(defn- parse-concept
  "Parses a concept from a JSON response"
  [response]
  (-> response
      :body
      (json/decode true)
      (update-in [:revision-date] (partial f/parse (f/formatters :date-time)))
      (update-in [:concept-type] keyword)))

(defn- parse-errors
  "Parses an error response from a JSON response"
  [response]
  (-> response
      :body
      (json/decode true)))

(defn- parse-concepts
  "Parses multiple concept from a JSON response"
  [response]
  (map #(-> %
            (update-in [:revision-date] (partial f/parse (f/formatters :date-time)))
            (update-in [:concept-type] keyword))
       (json/decode (:body response) true)))

(defn get-concept-id
  "Make a GET to retrieve the id for a given concept-type, provider-id, and native-id."
  [concept-type provider-id native-id]
  (let [response (client/get (str (concept-id-url) (name concept-type) "/" provider-id "/" native-id)
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)
        body (json/decode (:body response) true)
        {:keys [concept-id errors]} body]
    {:status status :concept-id concept-id :errors errors}))

(defn get-concept-by-id-and-revision
  "Make a GET to retrieve a concept by concept-id and revision."
  [concept-id revision-id]
  (let [response (client/get (str (concepts-url) concept-id "/" revision-id)
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)]
    (if (= status 200)
      {:status status :concept (parse-concept response)}
      {:status status})))

(defn get-concept-by-id
  "Make a GET to retrieve a concept by concept-id."
  [concept-id]
  (let [response (client/get (str (concepts-url) concept-id)
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)]
    (if (= status 200)
      {:status status :concept (parse-concept response)}
      {:status status})))

(defn get-concepts
  "Make a POST to retrieve concepts by concept-id and revision."
  ([tuples]
   (get-concepts tuples nil))
  ([tuples allow-missing?]
   (let [query-params (if (nil? allow-missing?)
                        {}
                        {:allow_missing allow-missing?})
         path "search/concept-revisions"
         response (client/post (str (concepts-url) path)
                               {:query-params query-params
                                :body (json/generate-string tuples)
                                :content-type :json
                                :accept :json
                                :throw-exceptions false
                                :connection-manager (conn-mgr)})
         status (:status response)]
     (if (= status 200)
       {:status status
        :concepts (parse-concepts response)}
       (assoc (parse-errors response) :status status)))))

(defn get-latest-concepts
  "Make a POST to retreive the latest revision of concpets by concept-id."
  ([concept-ids]
   (get-latest-concepts concept-ids nil))
  ([concept-ids allow-missing?]
   (let [query-params (if (nil? allow-missing?)
                        {}
                        {:allow_missing allow-missing?})
         path "search/latest-concept-revisions"
         response (client/post (str (concepts-url) path)
                               {:query-params query-params
                                :body (json/generate-string concept-ids)
                                :content-type :json
                                :accept :json
                                :throw-exceptions false
                                :connection-manager (conn-mgr)})
         status (:status response)]
     (if (= status 200)
       {:status status
        :concepts (parse-concepts response)}
       (assoc (parse-errors response) :status status)))))

(defn find-concepts
  "Make a get to retrieve concepts by parameters for a specific concept type"
  ([concept-type]
   (find-concepts concept-type {}))
  ([concept-type params]
   (let [response (client/get (str (concepts-url) "search/" (inf/plural (name concept-type)))
                              {:query-params params
                               :accept :json
                               :throw-exceptions false
                               :connection-manager (conn-mgr)})
         status (:status response)]
     (if (= status 200)
       {:status status
        :concepts (parse-concepts response)}
       (assoc (parse-errors response) :status status)))))

(defn find-latest-concepts
  "Make a get to retrieve the latest revision of concepts by parameters for a specific concept type"
  [concept-type params]
  (find-concepts concept-type (assoc params :latest true)))

(defn get-expired-collection-concept-ids
  "Make a get to retrieve expired collection concept ids."
  [provider-id]
  (let [response (client/get (str (concepts-url) "search/expired-collections")
                             {:query-params (when provider-id {:provider provider-id})
                              :accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)]
    (if (= status 200)
      {:status status
       :concept-ids (json/decode (:body response) true)}
      (assoc (parse-errors response) :status status))))

(defn- save-concept-core
  "Fundamental save operation"
  [concept]
  (let [response (client/post (concepts-url)
                              {:body (json/generate-string concept)
                               :content-type :json
                               :accept :json
                               :throw-exceptions false
                               :connection-manager (conn-mgr)})
        status (:status response)
        body (json/decode (:body response) true)
        {:keys [revision-id concept-id errors]} body]
    {:status status :revision-id revision-id :concept-id concept-id :errors errors}))

(defn save-concept
  "Make a POST request to save a concept with JSON encoding of the concept.  Returns a map with
  status, revision-id, transaction-id, and a list of error messages"
  ([concept]
   (save-concept concept 1))
  ([concept num-revisions]
   (let [concept (update-in concept [:revision-date]
                            ;; Convert date times to string but allow invalid strings to be passed through
                            #(when % (str %)))]
     (dotimes [n (dec num-revisions)]
       (assert-no-errors (save-concept-core concept)))
     (save-concept-core concept))))

(defn delete-concept
  "Make a DELETE request to mark a concept as deleted. Returns the status and revision id of the
  tombstone."
  ([concept-id]
   (delete-concept concept-id nil nil))
  ([concept-id revision-id]
   (delete-concept concept-id revision-id nil))
  ([concept-id revision-id revision-date]
   (let [url (if revision-id
               (format "%s%s/%s" (concepts-url) concept-id revision-id)
               (format "%s%s" (concepts-url) concept-id))
         query-params (when revision-date
                        {:revision-date (str revision-date)})
         response (client/delete url
                                 {:throw-exceptions false
                                  :query-params query-params
                                  :connection-manager (conn-mgr)})
         status (:status response)
         body (json/decode (:body response) true)
         {:keys [revision-id errors]} body]
     {:status status :revision-id revision-id :errors errors})))

(defn force-delete-concept
  "Make a DELETE request to permanently remove a revison of a concept."
  [concept-id revision-id]
  (let [url (format "%sforce-delete/%s/%s" (concepts-url) concept-id revision-id)
        response (client/delete url
                                {:throw-exceptions false
                                 :connection-manager (conn-mgr)})
        status (:status response)
        body (json/decode (:body response) true)
        {:keys [revision-id errors]} body]
    {:status status :revision-id revision-id :errors errors}))

(defmulti expected-concept
  "Modifies a concept for comparison with a retrieved concept."
  (fn [concept]
    (let [{:keys [concept-type]} concept]
      (if (contains? concept-service/system-level-concept-types concept-type)
        ;; system level concept
        :system-level-concept
        concept-type))))

(defmethod expected-concept :granule
  [concept]
  ;; :parent-entry-title is saved but not retrieved
  (if (:extra-fields concept)
    (update-in concept [:extra-fields] dissoc :parent-entry-title)
    concept))

(defmethod expected-concept :access-group
  [concept]
  (if (:provider-id concept)
    concept
    (assoc concept :provider-id "CMR")))

(defmethod expected-concept :system-level-concept
  [concept]
  (assoc concept :provider-id "CMR"))

(defmethod expected-concept :default
  [concept]
  concept)

(defn verify-concept-was-saved
  "Check to make sure a concept is stored in the database."
  [concept]
  (let [{:keys [concept-id revision-id]} concept
        stored-concept (:concept (get-concept-by-id-and-revision concept-id revision-id))]
    (is (= (expected-concept concept) (dissoc stored-concept :revision-date :transaction-id :created-at)))))

(defn is-tag-association-deleted?
  "Returns if the ta is marked as deleted in metadata-db"
  [tag-association deleted?]
  (let [{:keys [status concept]} (get-concept-by-id (:concept-id tag-association))]
    (is (= 200 status))
    (is (= deleted? (:deleted concept)))))

(defn create-and-save-collection
  "Creates, saves, and returns a collection concept with its data from metadata-db. "
  ([provider-id uniq-num]
   (create-and-save-collection provider-id uniq-num 1))
  ([provider-id uniq-num num-revisions]
   (create-and-save-collection provider-id uniq-num num-revisions {}))
  ([provider-id uniq-num num-revisions attributes]
   (let [concept (collection-concept provider-id uniq-num attributes)
         _ (dotimes [n (dec num-revisions)]
             (assert-no-errors (save-concept concept)))
         {:keys [concept-id revision-id]} (save-concept concept)]
     (assoc concept :concept-id concept-id :revision-id revision-id))))

(defn create-and-save-granule
  "Creates, saves, and returns a granule concept with its data from metadata-db"
  ([provider-id parent-collection uniq-num]
   (create-and-save-granule provider-id parent-collection uniq-num 1))
  ([provider-id parent-collection uniq-num num-revisions]
   (create-and-save-granule
     provider-id parent-collection uniq-num num-revisions {}))
  ([provider-id parent-collection uniq-num num-revisions attributes]
   (let [concept (granule-concept provider-id parent-collection uniq-num attributes)
         _ (dotimes [n (dec num-revisions)]
             (assert-no-errors (save-concept concept)))
         {:keys [concept-id revision-id]} (save-concept concept)]
     (-> concept
         (assoc :concept-id concept-id :revision-id revision-id)))))

(defn create-and-save-tag
  "Creates, saves, and returns a tag concept with its data from metadata-db"
  ([uniq-num]
   (create-and-save-tag uniq-num 1))
  ([uniq-num num-revisions]
   (create-and-save-tag uniq-num num-revisions {}))
  ([uniq-num num-revisions attributes]
   (let [concept (tag-concept uniq-num attributes)
         _ (dotimes [n (dec num-revisions)]
             (assert-no-errors (save-concept concept)))
         {:keys [concept-id revision-id]} (save-concept concept)]
     (assoc concept :concept-id concept-id :revision-id revision-id))))

(defn create-and-save-tag-association
  "Creates, saves, and returns a tag concept with its data from metadata-db"
  ([concept tag uniq-num]
   (create-and-save-tag-association concept tag uniq-num 1))
  ([concept tag uniq-num num-revisions]
   (create-and-save-tag-association concept tag uniq-num num-revisions {}))
  ([concept tag uniq-num num-revisions attributes]
   (let [concept (tag-association-concept concept tag uniq-num attributes)
          _ (dotimes [n (dec num-revisions)]
              (assert-no-errors (save-concept concept)))
          {:keys [concept-id revision-id]} (save-concept concept)]
      (assoc concept :concept-id concept-id :revision-id revision-id))))

(defn create-and-save-service
  "Creates, saves, and returns a service concept with its data from metadata-db."
  ([uniq-num]
   (create-and-save-service uniq-num 1))
  ([uniq-num num-revisions]
   (create-and-save-service uniq-num num-revisions {}))
  ([uniq-num num-revisions attributes]
   (let [concept (service-concept uniq-num attributes)
         _ (dotimes [n (dec num-revisions)]
             (assert-no-errors (save-concept concept)))
         {:keys [concept-id revision-id]} (save-concept concept)]
     (assoc concept :concept-id concept-id :revision-id revision-id))))

(defn create-and-save-group
  "Creates, saves, and returns a group concept with its data from metadata-db."
  ([provider-id uniq-num]
   (create-and-save-group provider-id uniq-num 1))
  ([provider-id uniq-num num-revisions]
   (create-and-save-group provider-id uniq-num num-revisions {}))
  ([provider-id uniq-num num-revisions attributes]
   (let [concept (group-concept provider-id uniq-num attributes)
         _ (dotimes [n (dec num-revisions)]
             (assert-no-errors (save-concept concept)))
         {:keys [concept-id revision-id]} (save-concept concept)]
     (assoc concept :concept-id concept-id :revision-id revision-id))))

(defn create-and-save-acl
  "Creates, saves, and returns an ACL concept with its data from metadata-db."
  ([provider-id uniq-num]
   (create-and-save-acl provider-id uniq-num 1))
  ([provider-id uniq-num num-revisions]
   (create-and-save-acl provider-id uniq-num num-revisions {}))
  ([provider-id uniq-num num-revisions attributes]
   (let [concept (acl-concept provider-id uniq-num attributes)
         _ (dotimes [n (dec num-revisions)]
             (assert-no-errors (save-concept concept)))
         {:keys [concept-id revision-id]} (save-concept concept)]
     (assoc concept :concept-id concept-id :revision-id revision-id))))

(defn create-and-save-humanizer
  "Creates, saves, and returns a humanizer concept with its data from metadata-db"
  ([]
   (create-and-save-humanizer 1))
  ([num-revisions]
   (create-and-save-humanizer num-revisions {}))
  ([num-revisions attributes]
   (let [concept (humanizer-concept 1 attributes)
         _ (dotimes [n (dec num-revisions)]
             (assert-no-errors (save-concept concept)))
         {:keys [concept-id revision-id]} (save-concept concept)]
     (assoc concept :concept-id concept-id :revision-id revision-id))))

(defn create-and-save-variable
  "Creates, saves, and returns a variable concept with its data from metadata-db"
  ([provider-id uniq-num]
   (create-and-save-variable provider-id uniq-num 1))
  ([provider-id uniq-num num-revisions]
   (create-and-save-variable provider-id uniq-num num-revisions {}))
  ([provider-id uniq-num num-revisions attributes]
   (let [concept (variable-concept provider-id uniq-num attributes)
         _ (dotimes [n (dec num-revisions)]
             (assert-no-errors (save-concept concept)))
         {:keys [concept-id revision-id]} (save-concept concept)]
     (assoc concept :concept-id concept-id :revision-id revision-id))))

(defn create-and-save-variable-association
  "Creates, saves, and returns a variable association concept with its data from metadata-db"
  ([concept variable uniq-num]
   (create-and-save-variable-association concept variable uniq-num 1))
  ([concept variable uniq-num num-revisions]
   (create-and-save-variable-association concept variable uniq-num num-revisions {}))
  ([concept variable uniq-num num-revisions attributes]
   (let [concept (variable-association-concept concept variable uniq-num attributes)
          _ (dotimes [n (dec num-revisions)]
              (assert-no-errors (save-concept concept)))
          {:keys [concept-id revision-id]} (save-concept concept)]
      (assoc concept :concept-id concept-id :revision-id revision-id))))

;;; Providers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-provider
  "Make a POST request to save a provider with JSON encoding of the provider. Returns a map with
  status and a list of error messages."
  [params]
  (let [response (client/post (providers-url)
                              {:body (json/generate-string
                                       (util/remove-nil-keys params))
                               :content-type :json
                               :accept :json
                               :throw-exceptions false
                               :connection-manager (conn-mgr)
                               :headers {transmit-config/token-header (transmit-config/echo-system-token)}})
        status (:status response)
        {:keys [errors provider-id]} (json/decode (:body response) true)]
    {:status status :errors errors :provider-id provider-id}))

(defn get-providers
  "Make a GET request to retrieve the list of providers."
  []
  (let [response (client/get (providers-url)
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)
        body (json/decode (:body response) true)]
    {:status status
     :errors (:errors body)
     :providers (when (= status 200) body)}))

(defn update-provider
  "Updates the provider with the given parameters, which is a map of key and value for
  provider-id, short-name, cmr-only and small fields of the provider."
  [params]
  (let [response (client/put (format "%s/%s" (providers-url) (:provider-id params))
                             {:body (json/generate-string params)
                              :content-type :json
                              :accept :json
                              :as :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)
                              :headers {transmit-config/token-header (transmit-config/echo-system-token)}})
        {:keys [status body]} response
        {:keys [errors]} (when (not= 200 status)
                           (json/decode (:body response) true))]
    {:status status :errors errors}))

(defn delete-provider
  "Make a DELETE request to remove a provider."
  [provider-id]
  (let [response (client/delete (format "%s/%s" (providers-url) provider-id)
                                {:accept :json
                                 :throw-exceptions false
                                 :connection-manager (conn-mgr)
                                 :headers {transmit-config/token-header (transmit-config/echo-system-token)}})
        status (:status response)
        {:keys [errors]} (json/decode (:body response) true)]
    {:status status :errors errors}))


(defn verify-provider-was-saved
  "Verify that the given provider-map is in the list of providers."
  [provider-map]
  (some #{(merge {:short-name (:provider-id provider-map)
                  :cmr-only false
                  :small false}
                 (util/remove-nil-keys provider-map))}
        (:providers (get-providers))))

;;; Miscellaneous
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn old-revision-concept-cleanup
  "Runs the old revision concept cleanup job"
  []
  (:status
    (client/post (old-revision-concept-cleanup-url)
                 {:throw-exceptions false
                  :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                  :connection-manager (conn-mgr)})))

(defn expired-concept-cleanup
  "Runs the expired concept cleanup job"
  []
  (:status
    (client/post (expired-concept-cleanup-url)
                 {:throw-exceptions false
                  :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                  :connection-manager (conn-mgr)})))

(defn reset-database
  "Make a request to reset the database by clearing out all stored concepts."
  []
  (:status
   (client/post (reset-url) {:throw-exceptions false
                             :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                             :connection-manager (conn-mgr)})))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn reset-database-fixture
  "Creates a database fixture function to reset the database after every test.
  Optionally accepts a list of provider-ids to create before the test"
  [& providers]
  (fn [f]
    (try
      ;; We set this to false during a test so that messages won't be published when this is run
      ;; in dev system and cause exceptions in the indexer.
      (side/eval-form `(mdb-config/set-publish-messages! false))
      (reset-database)
      (doseq [provider providers]
        (let [{:keys [provider-id short-name cmr-only small]} provider
              short-name (if short-name short-name provider-id)]
          (save-provider {:provider-id provider-id
                          :short-name short-name
                          :cmr-only (if cmr-only true false)
                          :small (if small true false)})))
      (f)
      (finally
        (side/eval-form `(mdb-config/set-publish-messages! true))))))
