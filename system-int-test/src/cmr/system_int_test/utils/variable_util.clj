(ns cmr.system-int-test.utils.variable-util
  "This contains utilities for testing variables."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [is]]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-variable :as data-umm-v]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.search :as transmit-search]
   [cmr.umm-spec.versioning :as versioning]))

(def unique-index (atom 0))

(def content-type
  "The default content type used in the tests below."
  "application/vnd.nasa.cmr.umm+json")

(def versioned-content-type
  "A versioned default content type used in the tests."
  (mt/with-version content-type versioning/current-variable-version))

(def utf-versioned-content-type
  "A default versioned content type with the charset set to UTF-8."
  (str versioned-content-type "; charset=utf-8"))

(def default-opts
  "Default HTTP client options for use in the tests below."
  {:accept-format :json
   :content-type content-type})

(defn token-opts
  "A little testing utility function that adds a user token to the default
  headers (HTTP client options)."
  [token]
  (merge default-opts {:token token}))

(defn grant-all-variable-fixture
  "A test fixture that grants all users the ability to create and modify
  variables."
  [f]
  (echo-util/grant-all-variable (s/context))
  (f))

(defn setup-acl
  [group-name user-name user-type]
  (let [gid (echo-util/get-or-create-group (s/context) group-name)
        token (echo-util/login (s/context) user-name [gid])
        grant-id (echo-util/grant (assoc (s/context) :token token)
                                   [{:permissions [:read]
                                     :user_type user-type}]
                                   :system_identity
                                   {:target nil})]
     {:user-name user-name
      :group-name group-name
      :group-id gid
      :grant-id grant-id
      :token token}))

(defn setup-guest-acl
  [gid-name uid-name]
  (setup-acl gid-name uid-name :guest))

(defn setup-registered-acl
  [gid-name uid-name]
  (setup-acl gid-name uid-name :registered))

(defn setup-update-acl
  "Set up the ACLs for UMM-Var update permissions and return the ids+token"
  ([context provider-id]
   (setup-update-acl
    context provider-id :update "umm-var-user42" "umm-var-guid42"))
  ([context provider-id permission-type]
   (setup-update-acl
    context provider-id permission-type "umm-var-user42" "umm-var-guid42"))
  ([context provider-id user-name group-name]
   (setup-update-acl
    context provider-id :update user-name group-name))
  ([context provider-id permission-type user-name group-name]
   (let [update-group-id (echo-util/get-or-create-group context group-name)
         update-token (echo-util/login context user-name [update-group-id])
         token-context (assoc context :token update-token)
         update-grant-id (echo-util/grant-group-provider-admin
                          token-context update-group-id provider-id permission-type)]
     {:user-name user-name
      :group-name group-name
      :group-id update-group-id
      :grant-id update-grant-id
      :token update-token})))

(defn make-variable-concept
  ([]
    (make-variable-concept {}))
  ([metadata-attrs]
    (make-variable-concept metadata-attrs {}))
  ([metadata-attrs attrs]
    (-> (merge {:provider-id "PROV1"} metadata-attrs)
        (data-umm-v/variable-concept)
        (assoc :format versioned-content-type)
        (merge attrs)))
  ([metadata-attrs attrs idx]
    (-> (merge {:provider-id "PROV1"} metadata-attrs)
        (data-umm-v/variable-concept :umm-json idx)
        (assoc :format versioned-content-type)
        (merge attrs))))

(defn make-unique-variable-concept
  ([]
    (make-unique-variable-concept {} {}))
  ([metadata-attrs attrs]
    (swap! unique-index inc)
    (make-variable-concept metadata-attrs attrs @unique-index)))

(defn ingest-variable-with-association
  "A convenience function for ingesting a variable with collection association during tests."
  ([]
   (ingest-variable-with-association (make-variable-concept)))
  ([variable-concept]
   (ingest-variable-with-association
     variable-concept
     (assoc default-opts :token "mock-echo-system-token")))
  ([variable-concept opts]
   (let [result (ingest-util/ingest-variable variable-concept opts)
         attrs (select-keys variable-concept
                            [:provider-id :native-id :metadata])]
     (merge result attrs))))

(defn ingest-variable
  "A convenience function for ingesting a variable during tests."
  ([]
   (ingest-variable (make-variable-concept)))
  ([variable-concept]
   (ingest-variable variable-concept default-opts))
  ([variable-concept opts]
   (let [result (ingest-util/ingest-concept variable-concept opts)
         attrs (select-keys variable-concept
                            [:provider-id :native-id :metadata])]
     (merge result attrs))))

(defn ingest-variable-with-attrs
  "Helper function to ingest a variable with the given variable attributes"
  ([metadata-attrs]
   (ingest-variable (make-variable-concept metadata-attrs)))
  ([metadata-attrs attrs]
   (ingest-variable (make-variable-concept metadata-attrs attrs)))
  ([metadata-attrs attrs idx]
   (ingest-variable (make-variable-concept metadata-attrs attrs idx))))

(def ^:private json-field-names
  "List of fields expected in a variable JSON response."
  [:concept-id :revision-id :provider-id :native-id :deleted :name :long-name])

(defn extract-name-from-metadata
  "Pulls the name out of the metadata field in the provided variable concept."
  [variable]
  (:Name (json/parse-string (:metadata variable) true)))

(defn extract-long-name-from-metadata
  "Pulls the long name out of the metadata field in the provided variable concept."
  [variable]
  (:LongName (json/parse-string (:metadata variable) true)))

(defn get-expected-json-variables
  "Returns the expected variables to compare to results returned by a JSON search."
  [variables]
  (->> variables
       (map #(assoc % :name (extract-name-from-metadata %)))
       (map #(assoc % :long-name (extract-long-name-from-metadata %)))
       (map #(select-keys % json-field-names))))

(defn assert-variable-search
  "Verifies the variable search results"
  [variables response]
  (let [expected-items (->> (get-expected-json-variables variables)
                            seq
                            set)
        expected-response {:status 200
                           :hits (count variables)
                           :items expected-items}]
    (is (:took response))
    (is (= expected-response
           (-> response
               (select-keys [:status :hits :items])
               (update :items set))))))

(defn- add-name-to-variable
  "Add the Name field of the variable to itself.
  This function is used for checking if the result references match the response."
  [variable]
  (let [{:keys [metadata]} variable
        parsed (json/parse-string metadata true)]
    (assoc variable :variable-name (:Name parsed))))

(defn assert-variable-references-match
  "Verifies the variable references"
  [variables response]
  (let [variables (map add-name-to-variable variables)]
    (d/refs-match? variables response)))

(defn assert-variable-search-order
  "Verifies the searcch results are in the correct order"
  [variables response]
  (let [expected-items (seq (get-expected-json-variables variables))]
    (is (= expected-items (:items response)))))

(defn- coll-variable-association->expected-variable-association
  "Returns the expected variable association for the given collection concept id to
  variable association mapping, which is in the format of, e.g.
  {[C1200000000-CMR 1] {:concept-id \"VA1200000005-CMR\" :revision-id 1}}."
  [coll-variable-association error?]
  (let [[[coll-concept-id coll-revision-id] variable-association] coll-variable-association
        {:keys [concept-id revision-id]} variable-association
        associated-item (if coll-revision-id
                      {:concept-id coll-concept-id :revision-id coll-revision-id}
                      {:concept-id coll-concept-id})
        errors (select-keys variable-association [:errors :warnings])]
    (if (seq errors)
      (merge {:associated-item associated-item} errors)
      {:variable-association {:concept-id concept-id :revision-id revision-id}
       :associated-item associated-item})))

(defn- comparable-variable-associations
  "Returns the variable associations with the concept_id removed from the variable_association field.
  We do this to make comparision of created variable associations possible, as we can't assure
  the order of which the variable associations are created."
  [variable-associations]
  (let [fix-ta-fn (fn [ta]
                    (if (:variable-association ta)
                      (update ta :variable-association dissoc :concept-id)
                      ta))]
    (map fix-ta-fn variable-associations)))

(defn assert-variable-association-response-ok?
  "Assert the variable association response when status code is 200 is correct."
  ([coll-variable-associations response]
   (assert-variable-association-response-ok? coll-variable-associations response true))
  ([coll-variable-associations response error?]
   (let [{:keys [status body errors]} response
         expected-tas (map #(coll-variable-association->expected-variable-association % error?)
                           coll-variable-associations)]
     (is (= [200
             (set (comparable-variable-associations expected-tas))]
            [status (set (comparable-variable-associations body))])))))

(defn assert-variable-association-bad-request
  "Assert the variable association response when status code is 400 is correct."
  ([coll-variable-associations response]
   (assert-variable-association-bad-request coll-variable-associations response true))
  ([coll-variable-associations response error?]
   (let [{:keys [status body errors]} response
         expected-tas (map #(coll-variable-association->expected-variable-association % error?)
                           coll-variable-associations)]
     (is (= [400
             (set (comparable-variable-associations expected-tas))]
            [status (set (comparable-variable-associations body))])))))

(defn assert-variable-dissociation-response-ok?
  "Assert the variable association response when status code is 200 is correct."
  [coll-variable-associations response]
  (assert-variable-association-response-ok? coll-variable-associations response false))

(defn assert-variable-dissociation-bad-request
  "Assert the variable association error when status code is 400 is correct."
  [err-msg response]
  (assert-variable-association-bad-request err-msg response))

(defn assert-variable-associated-with-query
  "Assert the collections found by the variable query matches the given collection revisions"
  [token query expected-colls]
  (let [refs (search/find-refs :collection
                               (util/remove-nil-keys (assoc query :token token :page_size 30))
                               {:all-revisions true})]
    (is (nil? (:errors refs)))
    (is (d/refs-match? expected-colls refs))))

(defn search
  "Searches for variables using the given parameters."
  [params]
  (search/process-response
   (transmit-search/search-for-variables
    (s/context) params {:raw? true
                        :http-options {:accept :json}})))

(defn- matches-concept-id-and-revision-id?
  "Returns true if the item matches the provided concept-id and revision-id."
  [item concept-id revision-id]
  (let [metadata (:meta item)]
    (and (= concept-id (:concept-id metadata))
         (= revision-id (:revision-id metadata)))))

(defn- get-single-variable-from-umm-json
  "Returns a single variable from a UMM JSON response. Returns nil if the provided concept-id and
  revision-id are not found."
  [umm-json-response concept-id revision-id]
  (let [variables (filter #(matches-concept-id-and-revision-id? % concept-id revision-id)
                          (get-in umm-json-response [:results :items]))]
    ;; Sanity check that no more than one variable matches the concept-id and revision-id
    (is (<= 0 (count variables) 1))
    (first variables)))

(defn assert-variable-associations
  "Asserts that the expected variable associations are returned for the given variable concept."
  [variable expected-associations search-params]
  (let [umm-json-response (search/find-concepts-umm-json
                           :variable (merge search-params
                                            {:concept_id (:concept-id variable)}))
        variable-revision (get-single-variable-from-umm-json
                           umm-json-response (:concept-id variable) (:revision-id variable))]
    (is (= expected-associations (:associations variable-revision)))))
