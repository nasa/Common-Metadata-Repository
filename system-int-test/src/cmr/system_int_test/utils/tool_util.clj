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
