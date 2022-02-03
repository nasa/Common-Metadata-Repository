(ns cmr.system-int-test.utils.tool-util
  "This contains utilities for testing tools."
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
   [cmr.system-int-test.data2.umm-spec-tool :as data-umm-t]
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
   mime-types/umm-json versioning/current-tool-version))

(def default-ingest-opts
  "Default HTTP client options for use when ingesting tools using the functions below."
  {:accept-format :json
   :content-type mime-types/umm-json})

(defn token-opts
  "A little testing utility function that adds a user token to the default
  headers (HTTP client options)."
  [token]
  (merge default-ingest-opts {:token token}))

(defn grant-all-tool-fixture
  "A test fixture that grants all users the ability to create and modify tools."
  [f]
  (echo-util/grant-all-tool (s/context))
  (f))

(defn make-tool-concept
  "Convenience function for creating a tool concept"
  ([]
   (make-tool-concept {}))
  ([metadata-attrs]
   (make-tool-concept metadata-attrs {}))
  ([metadata-attrs attrs]
   (make-tool-concept metadata-attrs attrs 0))
  ([metadata-attrs attrs idx]
   (-> (merge {:provider-id "PROV1"} metadata-attrs)
       (data-umm-t/tool-concept idx)
       (assoc :format versioned-content-type)
       (merge attrs))))

(defn ingest-tool
  "A convenience function for ingesting a tool during tests."
  ([]
   (ingest-tool (make-tool-concept)))
  ([tool-concept]
   (ingest-tool tool-concept default-ingest-opts))
  ([tool-concept opts]
   (let [result (ingest-util/ingest-concept tool-concept opts)
         attrs (select-keys tool-concept
                            [:provider-id :native-id :metadata])]
     (merge result attrs))))

(defn ingest-tool-with-attrs
  "Helper function to ingest a tool with the given tool attributes."
  ([metadata-attrs]
   (ingest-tool (make-tool-concept metadata-attrs)))
  ([metadata-attrs attrs]
   (ingest-tool (make-tool-concept metadata-attrs attrs)))
  ([metadata-attrs attrs idx]
   (ingest-tool (make-tool-concept metadata-attrs attrs idx))))

(defn search-refs
  "Searches for tools using the given parameters and requesting the XML references format."
  ([]
   (search-refs {}))
  ([params]
   (search-refs params {}))
  ([params options]
   (search/find-refs :tool params options)))

(defn search-json
  "Searches for tools using the given parameters and requesting the JSON format."
  ([]
   (search-json {}))
  ([params]
   (search-json params {}))
  ([params options]
   (search/process-response
    (transmit-search/search-for-tools
      (s/context) params (merge options
                                {:raw? true
                                 :http-options {:accept :json}})))))

(defn tool-result->xml-result
  [tool]
  (let [base-url (format "%s://%s:%s/concepts"
                         (config/search-protocol)
                         (config/search-host)
                         (config/search-port))
        id (:concept-id tool)
        revision (:revision-id tool)
        deleted (:deleted tool)
        location (if deleted
                   []
                   [:location (format "%s/%s/%s" base-url id revision)])]
    (select-keys
      (apply assoc tool (concat [:id id] location))
      [:id :revision-id :location :deleted])))

(def ^:private json-field-names
  "List of fields expected in a tool JSON response."
  [:concept-id :revision-id :provider-id :native-id :deleted :name :long-name])

(defn extract-name-from-metadata
  "Pulls the name out of the metadata field in the provided tool concept."
  [tool]
  (:Name (json/parse-string (:metadata tool) true)))

(defn extract-long-name-from-metadata
  "Pulls the long name out of the metadata field in the provided tool concept."
  [tool]
  (:LongName (json/parse-string (:metadata tool) true)))

(defn get-expected-tool-json
  "For the given tool return the expected tool JSON."
  [tool]
  (let [tool-json-fields (select-keys
                             (assoc tool
                                    :name (extract-name-from-metadata tool)
                                    :long-name (extract-long-name-from-metadata tool))
                             json-field-names)]
    (if (:deleted tool-json-fields)
      (dissoc tool-json-fields :long-name)
      tool-json-fields)))

(defn assert-tool-search
  "Verifies the tool search results. The response must be provided in JSON format."
  [tools response]
  (let [expected-items (-> (map get-expected-tool-json tools) seq set)
        expected-response {:status 200
                           :hits (count tools)
                           :items expected-items}]
    (is (:took response))
    (is (= expected-response
           (-> response
               (select-keys [:status :hits :items])
               (update :items set))))))

(defn assert-tool-references-match
  "Verifies the tool references"
  [tools response]
  (d/refs-match? tools response))


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
  [coll expected-fields tool-concept-ids var-concept-ids]
  (let [coll-with-extra-fields (merge coll
                                      expected-fields
                                      {:tools tool-concept-ids
                                       :variables var-concept-ids})
        {:keys [entry-title]} coll
        coll-json (atom/collections->expected-json
                   [coll-with-extra-fields]
                   (format "collections.json?entry_title=%s" entry-title))
        {:keys [status results]} (search/find-concepts-json
                                  :collection {:entry-title entry-title})]

    (is (= [200 coll-json]
           [status results]))))

(defn- assert-collection-atom-json-result
  "Verify collection in ATOM and JSON response has-formats, has-variables, has-transforms,
  has-spatial-subsetting, has-temporal-subsetting and associations fields"
  [coll expected-fields tool-concept-ids var-concept-ids]
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
    (assert-collection-json-result coll expected-fields tool-concept-ids var-concept-ids)))

(defn- assert-collection-umm-json-result
  "Verify collection in UMM JSON response has-formats, has-variables, has-transforms,
  has-spatial-subsetting, has-temporal-subsetting and associations fields"
  [coll expected-fields tool-concept-ids var-concept-ids]
  (let [expected-fields (merge handler/base-has-features
                               {:has-variables (some? (seq var-concept-ids))}
                               expected-fields)
        coll-with-extra-fields (merge coll
                                      expected-fields
                                      {:tools tool-concept-ids
                                       :variables var-concept-ids})
        options {:accept (mt/with-version mt/umm-json-results versioning/current-collection-version)}
        {:keys [entry-title]} coll
        response (search/find-concepts-umm-json :collection {:entry-title entry-title} options)]
    (du/assert-umm-jsons-match
     versioning/current-collection-version [coll-with-extra-fields] response)))

(defn assert-collection-search-result
  "Verify collection in ATOM, JSON and UMM JSON response has-formats, has-variables,
  has-transforms, has-spatial-subsetting, has-temporal-subsetting and associations fields"
  ([coll expected-fields tool-concept-ids]
   (assert-collection-search-result coll expected-fields tool-concept-ids nil))
  ([coll expected-fields tool-concept-ids var-concept-ids]
   (assert-collection-atom-json-result coll expected-fields tool-concept-ids var-concept-ids)
   (assert-collection-umm-json-result coll expected-fields tool-concept-ids var-concept-ids)))

(defn- coll-tool-association->expected-tool-association
  "Returns the expected tool association for the given collection concept id to
  tool association mapping, which is in the format of, e.g.
  {[C1200000000-CMR 1] {:concept-id \"TLA1200000005-CMR\" :revision-id 1}}."
  [coll-tool-association error?]
  (let [[[coll-concept-id coll-revision-id] tool-association] coll-tool-association
        {:keys [concept-id revision-id]} tool-association
        associated-item (if coll-revision-id
                          {:concept-id coll-concept-id :revision-id coll-revision-id}
                          {:concept-id coll-concept-id})
        errors (select-keys tool-association [:errors :warnings])]
    (if (seq errors)
      (merge {:associated-item associated-item} errors)
      {:tool-association {:concept-id concept-id :revision-id revision-id}
       :associated-item associated-item})))

(defn- comparable-tool-associations
  "Returns the tool associations with the concept_id removed from the tool_association field.
  We do this to make comparision of created tool associations possible, as we can't assure
  the order of which the tool associations are created."
  [tool-associations]
  (let [fix-tla-fn (fn [tla]
                    (if (:tool-association tla)
                      (update tla :tool-association dissoc :concept-id)
                      tla))]
    (map fix-tla-fn tool-associations)))

(defn assert-tool-association-response-ok?
  "Assert the tool association response when status code is 200 is correct."
  ([coll-tool-associations response]
   (assert-tool-association-response-ok? coll-tool-associations response true))
  ([coll-tool-associations response error?]
   (let [{:keys [status body errors]} response
         expected-sas (map #(coll-tool-association->expected-tool-association % error?)
                           coll-tool-associations)]
     (is (= [200
             (set (comparable-tool-associations expected-sas))]
            [status (set (comparable-tool-associations body))])))))

(defn assert-tool-association-bad-request
  "Assert the tool association response when status code is 200 is correct."
  ([coll-tool-associations response]
   (assert-tool-association-bad-request coll-tool-associations response true))
  ([coll-tool-associations response error?]
   (let [{:keys [status body errors]} response
         expected-tas (map #(coll-tool-association->expected-tool-association % error?)
                           coll-tool-associations)]
     (is (= [400
             (set (comparable-tool-associations expected-tas))]
            [status (set (comparable-tool-associations body))])))))

(defn- search-for-tool-associations
  "Searches for tool associations in metadata db using the given parameters."
  [params]
  (let [response (client/request {:url (url/mdb-tool-association-search-url)
                                  :method :get
                                  :accept :json
                                  :throw-exceptions false
                                  :connection-manager (s/conn-mgr)
                                  :query-params  params})]
    (search/process-response
     (update-in response [:body] #(json/decode % true)))))

(defn assert-tool-associated-with-query
  "Assert the collections found by the tool query matches the given collection revisions.
  Temporary using search metadata-db for tool associations. Will change to search search-app
  for collections once that is implemented in issues like CMR-4280."
  [token query expected-colls]
  (let [{:keys [status body]} (search-for-tool-associations (assoc query :latest true))
        colls (->> body
                   (filter #(= false (:deleted %)))
                   (map #(get-in % [:extra-fields :associated-concept-id])))]
    (is (= 200 status))
    (is (= (set (map :concept-id expected-colls))
           (set colls)))))

(defn assert-tool-dissociation-bad-request
  "Assert the tool association response when status code is 400 is correct."
  [coll-tool-associations response]
  (assert-tool-association-bad-request coll-tool-associations response true))
