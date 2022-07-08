(ns cmr.system-int-test.utils.subscription-util
  "This contains utilities for testing subscriptions."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.test :refer [is]]
   [cmr.common.mime-types :as mime-types]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-subscription :as data-umm-sub]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.url-helper :as urls]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.search :as transmit-search]
   [cmr.umm.echo10.echo10-core :as echo10]
   [cmr.umm-spec.versioning :as versioning]))

(def ^:private latest-umm-sub-verison versioning/current-subscription-version)

(defn- versioned-content-type
  "A versioned content type used in the tests."
  [umm-sub-version]
  (mime-types/with-version
    mime-types/umm-json umm-sub-version))

(def default-ingest-opts
  "Default HTTP client options for use when ingesting subscriptions using the functions below."
  {:accept-format :json
   :content-type mime-types/umm-json})

(defn token-opts
  "A little testing utility function that adds a user token to the default
  headers (HTTP client options)."
  [token]
  (merge default-ingest-opts {:token token}))

(defn grant-all-subscription-fixture
  "A test fixture that grants all users the ability to create and modify subscriptions."
  [providers guest-permissions registered-permissions]
  (fn [f]
    ;; grant INGEST_MANAGEMENT_ACL permission.
    (echo-util/grant-system-ingest-management (s/context) guest-permissions registered-permissions)
    (let [providers (for [[provider-guid provider-id] providers]
                      {:provider-guid provider-guid
                       :provider-id provider-id})]
      (doseq [provider-map providers]
        ;; grant SUBSCRIPTION_MANAGEMENT permission for each provider.
        (echo-util/grant-all-subscription-sm (s/context)
                                             (:provider-id provider-map)
                                             guest-permissions
                                             registered-permissions)))
    (f)))

(defn update-subscription-notification
  "Ingest a concept and return a map with status, concept-id, and revision-id"
  [concept]
  (let [request-url (urls/mdb-subscription-notification-time concept)
        request (merge
                 (config/conn-params (s/context))
                 {:accept :xml :throw-exceptions false})
        response (client/put request-url request)
        {:keys [headers body]} response
        status (int (:status response))]
    response))

(defn make-subscription-concept
  "Convenience function for creating a subscription concept"
  ([]
   (make-subscription-concept {}))
  ([metadata-attrs]
   (make-subscription-concept metadata-attrs {}))
  ([metadata-attrs attrs]
   (make-subscription-concept metadata-attrs attrs 0))
  ([metadata-attrs attrs idx]
   (make-subscription-concept metadata-attrs attrs idx latest-umm-sub-verison))
  ([metadata-attrs attrs idx umm-sub-version]
   (-> (merge {:provider-id "PROV1" :Name (str "Name " idx)} metadata-attrs)
       (data-umm-sub/subscription-concept umm-sub-version)
       (assoc :format (versioned-content-type umm-sub-version))
       (merge attrs))))

(defn make-subscription-concept-with-umm-version
  "Convenience function for creating a subscription concept with
   a previous UMM-Sub version."
  ([version]
   (make-subscription-concept {} {} 0 version))
  ([version metadata-attrs]
   (make-subscription-concept metadata-attrs {} 0 version))
  ([version metadata-attrs attrs]
   (make-subscription-concept metadata-attrs attrs 0 version))
  ([version metadata-attrs attrs idx]
   (make-subscription-concept metadata-attrs attrs idx version)))

(defn ingest-subscription
  "A convenience function for ingesting a subscription during tests."
  ([]
   (ingest-subscription (make-subscription-concept)))
  ([subscription-concept]
   (ingest-subscription subscription-concept default-ingest-opts))
  ([subscription-concept opts]
   (let [result (ingest-util/ingest-concept subscription-concept opts)
         attrs (select-keys subscription-concept
                            [:provider-id :native-id :metadata])]
     (merge result attrs))))

(defn ingest-subscription-with-attrs
  "Helper function to ingest a subscription with the given subscription attributes."
  ([metadata-attrs]
   (ingest-subscription (make-subscription-concept metadata-attrs)))
  ([metadata-attrs attrs]
   (ingest-subscription (make-subscription-concept metadata-attrs attrs)))
  ([metadata-attrs attrs idx]
   (ingest-subscription (make-subscription-concept metadata-attrs attrs idx))))

(defn search-refs
  "Searches for subscription using the given parameters and requesting the XML references format."
  ([]
   (search-refs {}))
  ([params]
   (search-refs params {}))
  ([params options]
   (search/find-refs :subscription params options)))

(defn- validate-subscription-response-format
  [subscription]
  ;; verifying schema keys are included
  (is (set/subset? (set (keys subscription))
                   #{:collection_concept_id
                   :type
                   :concept_id
                   :name
                   :native_id
                   :provider_id
                   :revision_id
                   :subscriber_id}))
  ;; verifying no dash in keys
  (is (not-any? #(string/includes? % "-") (map name (keys subscription)))))

(defn search-json
  "Searches for subscription using the given parameters and requesting the JSON format."
  ([]
   (search-json {}))
  ([params]
   (search-json params {}))
  ([params options]
   (let [response (transmit-search/search-for-subscriptions
                   (s/context) params (merge options
                                             {:raw? true
                                              :http-options {:accept :json}}))]
     (when-let [first-subscription (-> response :body :items first)]
       (validate-subscription-response-format first-subscription))
     (search/process-response response))))

(defn subscription-result->xml-result
  [subscription]
  (let [base-url (format "%s://%s:%s/concepts"
                         (config/search-protocol)
                         (config/search-host)
                         (config/search-port))
        id (:concept-id subscription)
        revision (:revision-id subscription)
        deleted (:deleted subscription)
        location (if deleted
                   []
                   [:location (format "%s/%s/%s" base-url id revision)])]
    (select-keys
     (apply assoc subscription (concat [:id id] location))
     [:id :revision-id :location :deleted])))

(def ^:private json-field-names
  "List of fields expected in a subscription JSON response."
  [:concept-id :revision-id :provider-id :native-id :deleted :name :subscriber-id :collection-concept-id :type])

(defn extract-name-from-metadata
  "Pulls the name out of the metadata field in the provided subscription concept."
  [subscription]
  (:Name (json/parse-string (:metadata subscription) true)))

(defn extract-subscriber-id-from-metadata
  "Pulls the subscriber-id out of the metadata field in the provided subscription concept."
  [subscription]
  (:SubscriberId (json/parse-string (:metadata subscription) true)))

(defn extract-collection-concept-id-from-metadata
  "Pulls the collection-concept-id out of the metadata field in the provided subscription concept."
  [subscription]
  (:CollectionConceptId (json/parse-string (:metadata subscription) true)))

(defn extract-type-from-metadata
  "Pulls the collection-concept-id out of the metadata field in the provided subscription concept."
  [subscription]
  (:Type (json/parse-string (:metadata subscription) true)))

(defn get-expected-subscription-json
  "For the given subscription return the expected subscription JSON."
  [subscription]
  (let [subscription-type (extract-type-from-metadata subscription)]
    (select-keys
     (merge subscription
            {:type (extract-type-from-metadata subscription)
             :name (extract-name-from-metadata subscription)
             :subscriber-id (extract-subscriber-id-from-metadata subscription)}
            (if (= subscription-type "collection")
              {:provider-id "CMR"}
              {:collection-concept-id (extract-collection-concept-id-from-metadata subscription)}))
     json-field-names)))

(defn assert-subscription-search
  "Verifies the subscription search results. The response must be provided in JSON format."
  [subscriptions response]
  (let [expected-items (-> (map get-expected-subscription-json subscriptions) seq set)
        expected-response {:status 200
                           :hits (count subscriptions)
                           :items expected-items}]
    (is (:took response))
    (is (= expected-response
           (-> response
               (select-keys [:status :hits :items])
               (update :items set))))))

(defn assert-subscription-references-match
  "Verifies the subscription references"
  [subscriptions response]
  (d/refs-match? subscriptions response))

(defn create-subscription-and-index
  [collection name user query]
  (let [concept {:Name (or name "test_sub_prov1")
                 :SubscriberId (or user "user2")
                 :CollectionConceptId (:concept-id collection)
                 :Query (or query "instrument=1B")}
        subscription (make-subscription-concept concept)
        options {:token "mock-echo-system-token"}
        result (ingest-subscription subscription options)]
    (index/wait-until-indexed)
    result))

(defn save-umm-granule
  "Saves a umm-granule concept.  If provided, attributes are merged with the concept
   and passed to metadata-db/concepts endpoint."
  ([provider-id umm-granule]
   (save-umm-granule provider-id umm-granule {}))
  ([provider-id umm-granule attributes]
   (let [parent-collection-id (get umm-granule :collection-concept-id)
         parent-entry-title (get-in umm-granule [:collection-ref :entry-title])
         granule-ur (:granule-ur umm-granule)
         xml (echo10/umm->echo10-xml umm-granule)
         gran (mdb/save-concept (merge
                                 {:concept-type :granule
                                  :provider-id provider-id
                                  :native-id granule-ur
                                  :format "application/echo10+xml"
                                  :metadata xml
                                  :revision-date "2000-01-01T10:00:00Z"
                                  :extra-fields {:parent-collection-id parent-collection-id
                                                 :parent-entry-title parent-entry-title
                                                 :granule-ur granule-ur}}
                                 attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status gran)))
     (merge umm-granule (select-keys gran [:concept-id :revision-id])))))
