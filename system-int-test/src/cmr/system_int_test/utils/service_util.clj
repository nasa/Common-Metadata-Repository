(ns cmr.system-int-test.utils.service-util
  "This contains utilities for testing services."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer [is]]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.search.results-handlers.atom-results-handler :as handler]
   [cmr.system-int-test.data2.atom :as atom]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-json :as du]
   [cmr.system-int-test.data2.umm-spec-service :as data-umm-s]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.config :as config]
   [cmr.transmit.search :as transmit-search]
   [cmr.umm-spec.versioning :as versioning]))

(def versioned-content-type
  "A versioned default content type used in the tests."
  (mime-types/with-version
   mime-types/umm-json versioning/current-service-version))

(def default-ingest-opts
  "Default HTTP client options for use when ingesting services using the functions below."
  {:accept-format :json
   :content-type mime-types/umm-json})

(defn token-opts
  "A little testing utility function that adds a user token to the default
  headers (HTTP client options)."
  [token]
  (merge default-ingest-opts {:token token}))

(defn grant-all-service-fixture
  "A test fixture that grants all users the ability to create and modify services."
  [f]
  (echo-util/grant-all-service (s/context))
  (f))

(defn make-service-concept
  "Convenience function for creating a service concept"
  ([]
   (make-service-concept {}))
  ([metadata-attrs]
   (make-service-concept metadata-attrs {}))
  ([metadata-attrs attrs]
   (make-service-concept metadata-attrs attrs 0))
  ([metadata-attrs attrs idx]
   (-> (merge {:provider-id "PROV1"} metadata-attrs)
       (data-umm-s/service-concept idx)
       (assoc :format versioned-content-type)
       (merge attrs))))

(defn ingest-service
  "A convenience function for ingesting a service during tests."
  ([]
   (ingest-service (make-service-concept)))
  ([service-concept]
   (ingest-service service-concept default-ingest-opts))
  ([service-concept opts]
   (let [result (ingest-util/ingest-concept service-concept opts)
         attrs (select-keys service-concept
                            [:provider-id :native-id :metadata])]
     (merge result attrs))))

(defn ingest-service-with-attrs
  "Helper function to ingest a service with the given service attributes."
  ([metadata-attrs]
   (ingest-service (make-service-concept metadata-attrs)))
  ([metadata-attrs attrs]
   (ingest-service (make-service-concept metadata-attrs attrs)))
  ([metadata-attrs attrs idx]
   (ingest-service (make-service-concept metadata-attrs attrs idx))))

(defn search-refs
  "Searches for services using the given parameters and requesting the XML references format."
  ([]
   (search-refs {}))
  ([params]
   (search-refs params {}))
  ([params options]
   (search/find-refs :service params options)))

(defn search-json
  "Searches for services using the given parameters and requesting the JSON format."
  ([]
   (search-json {}))
  ([params]
   (search-json params {}))
  ([params options]
   (search/process-response
    (transmit-search/search-for-services
      (s/context) params (merge options
                                {:raw? true
                                 :http-options {:accept :json}})))))

(defn service-result->xml-result
  [service]
  (let [base-url (format "%s://%s:%s/concepts"
                         (config/search-protocol)
                         (config/search-host)
                         (config/search-port))
        id (:concept-id service)
        revision (:revision-id service)
        deleted (:deleted service)
        location (if deleted
                   []
                   [:location (format "%s/%s/%s" base-url id revision)])]
    (select-keys
      (apply assoc service (concat [:id id] location))
      [:id :revision-id :location :deleted])))

(def ^:private json-field-names
  "List of fields expected in a service JSON response."
  [:concept-id :revision-id :provider-id :native-id :deleted :name :long-name])

(defn extract-name-from-metadata
  "Pulls the name out of the metadata field in the provided service concept."
  [service]
  (:Name (json/parse-string (:metadata service) true)))

(defn extract-long-name-from-metadata
  "Pulls the long name out of the metadata field in the provided service concept."
  [service]
  (:LongName (json/parse-string (:metadata service) true)))

(defn get-expected-service-json
  "For the given service return the expected service JSON."
  [service]
  (let [service-json-fields (select-keys
                             (assoc service
                                    :name (extract-name-from-metadata service)
                                    :long-name (extract-long-name-from-metadata service))
                             json-field-names)]
    (if (:deleted service-json-fields)
      (dissoc service-json-fields :long-name)
      service-json-fields)))

(defn assert-service-search
  "Verifies the service search results. The response must be provided in JSON format."
  [services response]
  (let [expected-items (-> (map get-expected-service-json services) seq set)
        expected-response {:status 200
                           :hits (count services)
                           :items expected-items}]
    (is (:took response))
    (is (= expected-response
           (-> response
               (select-keys [:status :hits :items])
               (update :items set))))))

(defn assert-service-references-match
  "Verifies the service references"
  [services response]
  (d/refs-match? services response))

(defn- coll-service-association->expected-service-association
  "Returns the expected service association for the given collection concept id to
  service association mapping, which is in the format of, e.g.
  {[C1200000000-CMR 1] {:concept-id \"SA1200000005-CMR\" :revision-id 1}}."
  [coll-service-association error?]
  (let [[[coll-concept-id coll-revision-id] service-association] coll-service-association
        {:keys [concept-id revision-id]} service-association
        associated-item (if coll-revision-id
                          {:concept-id coll-concept-id :revision-id coll-revision-id}
                          {:concept-id coll-concept-id})
        errors (select-keys service-association [:errors :warnings])]
    (if (seq errors)
      (merge {:associated-item associated-item} errors)
      {:service-association {:concept-id concept-id :revision-id revision-id}
       :associated-item associated-item})))

(defn- comparable-service-associations
  "Returns the service associations with the concept_id removed from the service_association field.
  We do this to make comparision of created service associations possible, as we can't assure
  the order of which the service associations are created."
  [service-associations]
  (let [fix-sa-fn (fn [sa]
                    (if (:service-association sa)
                      (update sa :service-association dissoc :concept-id)
                      sa))]
    (map fix-sa-fn service-associations)))

(defn assert-service-association-response-ok?
  "Assert the service association response when status code is 200 is correct."
  ([coll-service-associations response]
   (assert-service-association-response-ok? coll-service-associations response true))
  ([coll-service-associations response error?]
   (let [{:keys [status body errors]} response
         expected-sas (map #(coll-service-association->expected-service-association % error?)
                           coll-service-associations)]
     (is (= [200
             (set (comparable-service-associations expected-sas))]
            [status (set (comparable-service-associations body))])))))

(defn assert-service-association-bad-request
  "Assert the service association response when status code is 200 is correct."
  ([coll-service-associations response]
   (assert-service-association-bad-request coll-service-associations response true))
  ([coll-service-associations response error?]
   (let [{:keys [status body errors]} response
         expected-sas (map #(coll-service-association->expected-service-association % error?)
                           coll-service-associations)]
     (is (= [400
             (set (comparable-service-associations expected-sas))]
            [status (set (comparable-service-associations body))])))))


(defn assert-service-dissociation-response-ok?
  "Assert the service association response when status code is 200 is correct."
  [coll-service-associations response]
  (assert-service-association-response-ok? coll-service-associations response false))

(defn assert-service-dissociation-bad-request
  "Assert the service association response when status code is 400 is correct."
  [coll-service-associations response]
  (assert-service-association-bad-request coll-service-associations response true))

(defn- search-for-service-associations
  "Searches for service associations in metadata db using the given parameters."
  [params]
  (let [response (client/request {:url (url/mdb-service-association-search-url)
                                  :method :get
                                  :accept :json
                                  :throw-exceptions false
                                  :connection-manager (s/conn-mgr)
                                  :query-params  params})]
    (search/process-response
     (update-in response [:body] #(json/decode % true)))))

(defn assert-service-associated-with-query
  "Assert the collections found by the service query matches the given collection revisions.
  Temporary using search metadata-db for service associations. Will change to search search-app
  for collections once that is implemented in issues like CMR-4280."
  [token query expected-colls]
  (let [{:keys [status body]} (search-for-service-associations (assoc query :latest true))
        colls (->> body
                   (filter #(= false (:deleted %)))
                   (map #(get-in % [:extra-fields :associated-concept-id])))]
    (is (= 200 status))
    (is (= (set (map :concept-id expected-colls))
           (set colls)))))

(defn- assert-collection-atom-result
  "Verify the collection ATOM response has-formats, has-variables, has transforms fields
  have the correct values"
  [coll expected-fields]
  (let [coll-with-extra-fields (merge coll expected-fields)
        {:keys [entry-title]} coll
        coll-atom (atom/collections->expected-atom
                   [coll-with-extra-fields]
                   (format "collections.atom?entry_title=%s" entry-title))
        {:keys [status results]} (search/find-concepts-atom
                                  :collection {:entry-title entry-title})]

    (is (= [200 coll-atom]
           [status results]))))

(defn- assert-collection-json-result
  "Verify the collection JSON response associations related fields have the correct values"
  [coll expected-fields serv-concept-ids var-concept-ids]
  (let [coll-with-extra-fields (merge coll
                                      expected-fields
                                      {:services serv-concept-ids
                                       :variables var-concept-ids})
        {:keys [entry-title]} coll
        coll-json (atom/collections->expected-json
                   [coll-with-extra-fields]
                   (format "collections.json?entry_title=%s" entry-title))
        {:keys [status results]} (search/find-concepts-json
                                  :collection {:entry-title entry-title})]
    (is (= [200 coll-json]
           [status results])
        "JSON Result failed")))

(defn- assert-collection-atom-json-result
  "Verify collection in ATOM and JSON response has-formats, has-variables, has-transforms,
  has-spatial-subsetting, has-temporal-subsetting and associations fields"
  [coll expected-fields serv-concept-ids var-concept-ids]
  (let [service-features {:opendap (merge handler/base-has-features
                                          (get-in expected-fields [:service-features :opendap]))
                          :esi (merge handler/base-has-features
                                      (get-in expected-fields [:service-features :esi]))
                          :harmony (merge handler/base-has-features
                                          (get-in expected-fields [:service-features :harmony]))}
        expected-fields (-> (merge handler/base-has-features
                                   {:has-variables (some? (seq var-concept-ids))}
                                   expected-fields)
                            (assoc :service-features service-features))]
    (assert-collection-atom-result coll expected-fields)
    (assert-collection-json-result coll expected-fields serv-concept-ids var-concept-ids)))

(defn- assert-collection-umm-json-result
  "Verify collection in UMM JSON response has-formats, has-variables, has-transforms,
  has-spatial-subsetting, has-temporal-subsetting and associations fields"
  [coll expected-fields serv-concept-ids var-concept-ids]
  (let [expected-fields (merge handler/base-has-features
                               {:has-variables (some? (seq var-concept-ids))}
                               expected-fields)
        coll-with-extra-fields (merge coll
                                      expected-fields
                                      {:services serv-concept-ids
                                       :variables var-concept-ids})
        options {:accept (mt/with-version mt/umm-json-results versioning/current-collection-version)}
        {:keys [entry-title]} coll
        response (search/find-concepts-umm-json :collection {:entry-title entry-title} options)]
    (du/assert-umm-jsons-match
     versioning/current-collection-version [coll-with-extra-fields] response)))

(defn assert-collection-search-result
  "Verify collection in ATOM, JSON and UMM JSON response has-formats, has-variables,
  has-transforms, has-spatial-subsetting, has-temporal-subsetting and associations fields"
  ([coll expected-fields serv-concept-ids]
   (assert-collection-search-result coll expected-fields serv-concept-ids nil))
  ([coll expected-fields serv-concept-ids var-concept-ids]
   (assert-collection-atom-json-result coll expected-fields serv-concept-ids var-concept-ids)
   (assert-collection-umm-json-result coll expected-fields serv-concept-ids var-concept-ids)))
