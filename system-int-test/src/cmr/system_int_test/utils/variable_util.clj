(ns cmr.system-int-test.utils.variable-util
  "This contains utilities for testing variables."
  (:require
   [clojure.string :as string]
   [clojure.test :refer [is]]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-variable :as data-umm-v]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.transmit.variable :as transmit-variable]))

(def schema-version "1.9")
(def unique-index (atom 0))
(def content-type "application/vnd.nasa.cmr.umm+json")
(def default-opts {:accept-format :json
                   :content-type content-type})

(defn token-opts
  [token]
  (merge default-opts {:token token}))

(defn grant-all-variable-fixture
  "A test fixture that grants all users the ability to create and modify
  variables."
  [f]
  (echo-util/grant-all-variable (s/context))
  (f))

(defn setup-update-acl
  "Set up the ACLs for UMM-Var update permissions and return the ids+token"
  ([context provider-id]
   (setup-update-acl context provider-id "umm-var-user42" "umm-var-guid42"))
  ([context provider-id user-name group-name]
   (let [update-group-id (echo-util/get-or-create-group context group-name)
         update-token (echo-util/login context user-name [update-group-id])
         token-context (assoc context :token update-token)
         update-grant-id (echo-util/grant-group-provider-admin
                          token-context update-group-id provider-id :update)]
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
        (assoc :format (mt/with-version content-type schema-version))
        (merge attrs)))
  ([metadata-attrs attrs index]
    (-> (merge {:provider-id "PROV1"} metadata-attrs)
        (data-umm-v/variable-concept :umm-json index)
        (assoc :format (mt/with-version content-type schema-version))
        (merge attrs))))

(defn make-unique-variable-concept
  ([]
    (make-unique-variable-concept {} {}))
  ([metadata-attrs attrs]
    (swap! unique-index inc)
    (make-variable-concept metadata-attrs attrs @unique-index)))

(defn ingest-variable
  "A convenience function for ingesting a variable during tests."
  ([]
    (ingest-variable (make-variable-concept)))
  ([variable-concept]
    (ingest-variable variable-concept default-opts))
  ([variable-concept opts]
    (ingest-util/ingest-concept variable-concept opts)))

(defn ingest-variable-with-attrs
  "Helper function to ingest a variable with the given variable attributes"
  [attrs]
  (ingest-variable (make-variable-concept attrs)))

;; XXX This can be removed once variable associations have been updated to use the new
;; cmr.system-int-test.data2.umm-spec-variable namespace.
(def sample-variable
  {:Name "A-name"
   :LongName "A long UMM-Var name"
   :Units "m"
   :DataType "float32"
   :DimensionsName "H2OFunc"
   :Dimensions "11"
   :ValidRange {}
   :Scale "1.0"
   :Offset "0.0"
   :FillValue "-9999.0"
   :VariableType "SCIENCE_VARIABLE"
   :ScienceKeywords [{:Category "sk-A"
                       :Topic "sk-B"
                       :Term "sk-C"}]})

;; XXX This can be removed once variable associations have been updated to use the new
;; cmr.system-int-test.data2.umm-spec-variable namespace.
(defn make-variable
  "Makes a valid variable based on the given input"
  ([]
   (make-variable nil))
  ([attrs]
   (merge sample-variable attrs))
  ([index attrs]
   (merge
    sample-variable
    {:Name (str "Name" index)
     :LongName (str "Long UMM-Var name " index)}
    attrs)))

;; XXX This can be removed once variable associations have been updated to use the new
;; cmr.system-int-test.data2.umm-spec-variable namespace.
(defn create-variable
  "Creates a variable."
  ([token variable]
   (create-variable token variable nil))
  ([token variable options]
   (let [options (merge {:raw? true
                         :token token
                         :http-options {:content-type "application/vnd.nasa.cmr.umm+json"}}
                        options)]
     (ingest-util/parse-map-response
      (transmit-variable/create-variable (s/context) variable options)))))

;; XXX This can be removed once variable associations have been updated to use the new
;; cmr.system-int-test.data2.umm-spec-variable namespace.
(defn create-variable-with-attrs
  "Helper function to create a variable with the given variable attributes"
  [token attrs]
  (create-variable token (make-variable attrs)))

;; XXX This can be removed once variable associations have been updated to use the new
;; cmr.system-int-test.data2.umm-spec-variable namespace.
(defn update-variable
  "Updates a variable."
  ([token variable]
   (update-variable token (:variable-name variable) variable nil))
  ([token variable-name variable]
   (update-variable token variable-name variable nil))
  ([token variable-name variable options]
   (let [options (merge {:raw? true
                         :token token
                         :http-options {:content-type "application/vnd.nasa.cmr.umm+json"}}
                        options)]
     (ingest-util/parse-map-response
      (transmit-variable/update-variable (s/context) variable-name variable options)))))

;; XXX This can be removed once variable associations have been updated to use the new
;; cmr.system-int-test.data2.umm-spec-variable namespace.
(defn delete-variable
  "Deletes a variable"
  ([token variable-name]
   (delete-variable token variable-name nil))
  ([token variable-name options]
   (let [options (merge {:raw? true :token token} options)]
     (ingest-util/parse-map-response
      (transmit-variable/delete-variable (s/context) variable-name options)))))

(defn- associate-variable
  "Associate a variable with collections by the JSON condition.
  Valid association types are :query and :concept-ids."
  [association-type token variable-name condition options]
  (let [options (merge {:raw? true :token token} options)
        response (transmit-variable/associate-variable
                  association-type (s/context) variable-name condition options)]
    (index/wait-until-indexed)
    (ingest-util/parse-map-response response)))

(defn associate-by-query
  "Associates a variable with collections found with a JSON query"
  ([token variable-name condition]
   (associate-by-query token variable-name condition nil))
  ([token variable-name condition options]
   (associate-variable :query token variable-name {:condition condition} options)))

(defn associate-by-concept-ids
  "Associates a variable with collections by collection concept ids"
  ([token variable-name coll-concept-ids]
   (associate-by-concept-ids token variable-name coll-concept-ids nil))
  ([token variable-name coll-concept-ids options]
   (associate-variable :concept-ids token variable-name coll-concept-ids options)))

(defn- dissociate-variable
  "Dissociates a variable with collections found with a JSON query"
  [association-type token variable-name condition options]
  (let [options (merge {:raw? true :token token} options)
        response (transmit-variable/dissociate-variable
                  association-type (s/context) variable-name condition options)]
    (index/wait-until-indexed)
    (ingest-util/parse-map-response response)))

(defn dissociate-by-query
  "Dissociates a variable with collections found with a JSON query"
  ([token variable-name condition]
   (dissociate-by-query token variable-name condition nil))
  ([token variable-name condition options]
   (dissociate-variable :query token variable-name {:condition condition} options)))

(defn dissociate-by-concept-ids
  "Dissociates a variable with collections by collection concept ids"
  ([token variable-name coll-concept-ids]
   (dissociate-by-concept-ids token variable-name coll-concept-ids nil))
  ([token variable-name coll-concept-ids options]
   (dissociate-variable :concept-ids token variable-name coll-concept-ids options)))

(defn save-variable
  "A helper function for creating or updating variables for search tests.
   If the variable does not have a :concept-id it saves it. If the variable has a :concept-id,
   it updates the variable. Returns the saved variable along with :concept-id,
   :revision-id, :errors, and :status"
  ([token variable]
   (let [variable-to-save (select-keys variable [:variable-name :description :revision-date])
         response (if-let [concept-id (:concept-id variable)]
                    (update-variable token (:variable-name variable) variable-to-save)
                    (create-variable token variable-to-save))
         variable (into variable
                        (select-keys response [:status :errors :concept-id :revision-id]))]
     (if (= (:revision-id variable) 1)
       ;; Get the originator id for the variable
       (assoc variable :originator-id (tokens/get-user-id (s/context) token))
       variable)))
  ([token variable associated-collections]
   (let [saved-variable (save-variable token variable)
         ;; Associate the variable with the collections using a query by concept id
         condition {:or (map #(hash-map :concept_id (:concept-id %)) associated-collections)}
         response (associate-by-query token (:variable-name saved-variable) condition)]
     (assert (= 200 (:status response)) (pr-str condition))
     (assoc saved-variable :revision-id (:revision-id response)))))

(defn expected-concept
  "Create an expected concept given a service, its concept-id, a revision-id,
  and a user-id."
  [variable concept-id revision-id user-id]
  (let [native-id (string/lower-case (:Name variable))]
    {:concept-type :variable
     :native-id native-id
     :provider-id "CMR"
     ;; XXX The next two lines will change very soon, with the implementation
     ;;     of CMR-4204.
     :format mt/edn
     :metadata (pr-str (assoc (util/kebab-case-data variable)
                              :originator-id user-id
                              :native-id native-id))
     :user-id user-id
     :deleted false
     :concept-id concept-id
     :revision-id revision-id}))

(defn assert-variable-saved
  "Checks that a variable was persisted correctly in metadata db. The variable should already
   have originator id set correctly. The user-id indicates which user updated this revision."
  [variable user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)]
    (is (= (expected-concept variable concept-id revision-id user-id)
           (dissoc concept :revision-date :created-at :transaction-id :extra-fields)))))

(defn assert-variable-deleted
  "Checks that a variable tombstone was persisted correctly in metadata db."
  [variable user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)]
    (is (= (-> variable
               (expected-concept concept-id revision-id user-id)
               (assoc :metadata "" :deleted true))
           (dissoc concept :revision-date :created-at :transaction-id :extra-fields)))))

(defn sort-expected-variables
  "Sorts the variables using the expected default sort key."
  [variables]
  (sort-by identity
           (fn [t1 t2]
             (compare (:variable-name t1) (:variable-name t2)))
           variables))

(def variable-names-in-expected-response
  [:concept-id :revision-id :variable-name :description :originator-id])

(defn assert-variable-search
  "Verifies the variable search results"
  ([variables response]
   (assert-variable-search nil variables response))
  ([expected-hits variables response]
   (let [expected-items (->> variables
                             sort-expected-variables
                             (map #(select-keys % variable-names-in-expected-response)))
         expected-response {:status 200
                            :hits (or expected-hits (:hits response))
                            :items expected-items}]
     (is (:took response))
     (is (= expected-response (dissoc response :took))))))

(defn- coll-variable-association->expected-variable-association
  "Returns the expected variable association for the given collection concept id to
  variable association mapping, which is in the format of, e.g.
  {[C1200000000-CMR 1] {:concept-id \"TA1200000005-CMR\" :revision-id 1}}."
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

(defn assert-variable-dissociation-response-ok?
  "Assert the variable association response when status code is 200 is correct."
  [coll-variable-associations response]
  (assert-variable-association-response-ok? coll-variable-associations response false))

(defn assert-invalid-data-error
  "Assert variable association response when status code is 422 is correct"
  [expected-errors response]
  (let [{:keys [status body errors]} response]
    (is (= [422 (set expected-errors)]
           [status (set errors)]))))

(defn assert-variable-associated-with-query
  "Assert the collections found by the variable query matches the given collection revisions"
  [token query expected-colls]
  (let [refs (search/find-refs :collection
                               (util/remove-nil-keys (assoc query :token token :page_size 30))
                               {:all-revisions true})]
    (is (nil? (:errors refs)))
    (is (d/refs-match? expected-colls refs))))
