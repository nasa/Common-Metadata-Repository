(ns cmr.system-int-test.utils.variable-util
  "This contains utilities for testing variables."
  (:require
   [clojure.string :as string]
   [clojure.test :refer [is]]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.transmit.variable :as transmit-variable]))

;; XXX Note that once the code in cmr.ingest.api.variables was changed to
;;     generate an api response using cmr.ingest.api.core/generate-ingest-response
;;     instead of a custom, private function, the assertion function below
;;     no longer worked since it couldn't check the key values before they were
;;     transformed by `generate-ingest-response`. If this isn't important, we
;;     can delete this function as well as util/assert-convert-kebab-case.
(defn assert-convert-kebab-case
  [data]
  (ingest-util/assert-convert-kebab-case
   [:concept-id :revision-id
    :variable-name :originator-id
    :variable-association :associated-item]
   data))

(defn grant-all-variable-fixture
  "A test fixture that grants all users the ability to create and modify variables."
  [f]
  (echo-util/grant-all-variable (s/context))
  (f))

(def sample-variable
  {:Name "A name"
   :LongName "A long UMM-Var name"
   :Units "m"
   :DataType "float32"
   :DimensionsName ["H2OFunc"
                    "H2OPressureLay"
                    "MWHingeSurf"
                    "Cloud"]
   :Dimensions ["11" "14" "7" "2"]
   :ValidRange []
   :Scale 1.0
   :Offset 0.0
   :FillValue -9999.0
   :VariableType "SCIENCE_VARIABLE"})

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
     :Version (str "V" index)
     :LongName (str "Long UMM-Var name " index)}
    attrs)))

(defn create-variable
  "Creates a variable."
  ([token variable]
   (create-variable token variable nil))
  ([token variable options]
   (let [options (merge {:raw? true :token token} options)]
     (ingest-util/parse-map-response
      (transmit-variable/create-variable (s/context) variable options)))))

(defn update-variable
  "Updates a variable."
  ([token variable]
   (update-variable token (:variable-name variable) variable nil))
  ([token variable-name variable]
   (update-variable token variable-name variable nil))
  ([token variable-name variable options]
   (let [options (merge {:raw? true :token token} options)]
     (ingest-util/parse-map-response
      (transmit-variable/update-variable (s/context) variable-name variable options)))))

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
         variable (-> variable
                 (update :variable-name string/lower-case)
                 (into (select-keys response [:status :errors :concept-id :revision-id])))]

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

(defn assert-variable-saved
  "Checks that a variable was persisted correctly in metadata db. The variable should already
   have originator id set correctly. The user-id indicates which user updated this revision."
  [variable user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)]
    (is (= {:concept-type :variable
            :native-id (string/lower-case (:variable-name variable))
            :provider-id "CMR"
            :format mt/edn
            :metadata (pr-str variable)
            :user-id user-id
            :deleted false
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))

(defn assert-variable-deleted
  "Checks that a variable tombstone was persisted correctly in metadata db."
  [variable user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)]
    (is (= {:concept-type :variable
            :native-id (:variable-name variable)
            :provider-id "CMR"
            :metadata ""
            :format mt/edn
            :user-id user-id
            :deleted true
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))

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
