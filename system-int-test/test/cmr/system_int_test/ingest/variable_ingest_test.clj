(ns cmr.system-int-test.ingest.variable-ingest-test
  "CMR variable ingest integration tests."
  (:require
   [clj-http.client :as client]
   [clj-time.core :as t]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as ac]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.date-time-parser :as p]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.common.util :refer [are3]]
   [cmr.ingest.config :as config]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.data2.umm-spec-variable :as data-umm-v]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.system-int-test.utils.variable-util :as variable-util]
   [cmr.transmit.config :as transmit-config]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn]
   [cmr.umm-spec.test.expected-conversion :as exc]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}
                                          {:grant-all-ingest? false}))

(deftest variable-ingest-test
  (testing "ingest of a new variable concept"
    (let [{:keys [concept-id revision-id]} (variable-util/ingest-variable)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id))))
  (testing "ingest of a variable concept with a revision id"
    (let [concept (variable-util/make-variable-concept {} {:revision-id 5})
          {:keys [concept-id revision-id]} (variable-util/ingest-variable
                                            concept
                                            variable-util/default-opts)]
      (index/wait-until-indexed)
      (is (= 5 revision-id))
      (is (mdb/concept-exists-in-mdb? concept-id 5)))))

;; Verify that user-id is saved from User-Id or token header
(deftest variable-ingest-user-id-test
  (testing "ingest of new variable concept"
    (util/are3 [ingest-headers expected-user-id]
      (let [concept (variable-util/make-variable-concept)
            {:keys [concept-id revision-id]} (variable-util/ingest-variable
                                              concept
                                              ingest-headers)]
        (index/wait-until-indexed)
        (ingest/assert-user-id concept-id revision-id expected-user-id))
      "user id from token"
      {:token (e/login (s/context) "user1")} "user1"
      "user id from user-id header"
      {:user-id "user2"} "user2"
      "both user-id and token in the header results in the revision getting user id from user-id header"
      {:token (e/login (s/context) "user3")
       :user-id "user4"} "user4"
      "neither user-id nor token in the header"
      {} nil))
  (testing "update of existing concept with new user-id"
    (util/are3 [ingest-header1 expected-user-id1
                ingest-header2 expected-user-id2
                ingest-header3 expected-user-id3
                ingest-header4 expected-user-id4]
      (let [concept (variable-util/make-variable-concept)
            {:keys [concept-id revision-id]} (variable-util/ingest-variable
                                              concept
                                              ingest-header1)]
        (ingest/ingest-concept concept ingest-header2)
        (ingest/ingest-concept concept ingest-header3)
        (ingest/ingest-concept concept ingest-header4)
        (index/wait-until-indexed)
        (ingest/assert-user-id concept-id revision-id expected-user-id1)
        (ingest/assert-user-id concept-id (inc revision-id) expected-user-id2)
        (ingest/assert-user-id concept-id (inc (inc revision-id)) expected-user-id3)
        (ingest/assert-user-id concept-id (inc (inc (inc revision-id))) expected-user-id4))
      "user id from token"
      {:token (e/login (s/context) "user1")} "user1"
      {:token (e/login (s/context) "user2")} "user2"
      {:token (e/login (s/context) "user3")} "user3"
      {:token nil} nil
      "user id from user-id header"
      {:user-id "user1"} "user1"
      {:user-id "user2"} "user2"
      {:user-id "user3"} "user3"
      {:user-id nil} nil)))

;; Variable with concept-id ingest and update scenarios.
(deftest variable-w-concept-id-ingest-test
  (let [supplied-concept-id "V1000-PROV1"
        concept (variable-util/make-variable-concept
                 {}
                 {:concept-id supplied-concept-id
                  :native-id "Atlantic-1"})]
    (testing "ingest of a new variable concept with concept-id present"
      (let [{:keys [concept-id revision-id]} (variable-util/ingest-variable concept)]
        (index/wait-until-indexed)
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= [supplied-concept-id 1] [concept-id revision-id]))))

    (testing "Update the concept with the concept-id"
      (let [{:keys [concept-id revision-id]} (variable-util/ingest-variable concept)]
        (index/wait-until-indexed)
        (is (= [supplied-concept-id 2] [concept-id revision-id]))))

    (testing "update the concept without the concept-id"
      (let [{:keys [concept-id revision-id]} (variable-util/ingest-variable
                                              (dissoc concept :concept-id))]
        (index/wait-until-indexed)
        (is (= [supplied-concept-id 3] [concept-id revision-id]))))

    (testing "update concept with a different concept-id is invalid"
      (let [{:keys [status errors]} (variable-util/ingest-variable
                                     (assoc concept :concept-id "V1111-PROV1")
                                     {:accept-format :json
                                      :content-type variable-util/content-type})]
        (index/wait-until-indexed)
        (is (= [409 [(str "A concept with concept-id [V1000-PROV1] and "
                          "native-id [Atlantic-1] already exists for "
                          "concept-type [:variable] provider-id [PROV1]. "
                          "The given concept-id [V1111-PROV1] and native-id "
                          "[Atlantic-1] would conflict with that one.")]]
               [status errors]))))))

;; Verify that the accept header works
(deftest variable-ingest-accept-header-response-test
  (testing "json response"
    (let [response (variable-util/ingest-variable
                    (variable-util/make-variable-concept)
                    {:accept-format :json
                     :raw? true})]
      (is (= 1
             (:revision-id (ingest/parse-ingest-body :json response))))))
  (testing "xml response"
    (let [response (variable-util/ingest-variable
                    (variable-util/make-variable-concept)
                    {:accept-format :xml
                     :raw? true})]
      (is (= 2
             (:revision-id (ingest/parse-ingest-body :xml response)))))))

;; Verify that the accept header works with returned errors
(deftest variable-ingest-with-errors-accept-header-test
  (testing "json response"
    (let [concept-no-metadata (assoc (variable-util/make-variable-concept)
                                     :metadata "")
          response (variable-util/ingest-variable
                    concept-no-metadata
                    {:accept-format :json
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :json response)]
      (is (re-find #"Request content is too short." (first errors)))))
  (testing "xml response"
    (let [concept-no-metadata (assoc (variable-util/make-variable-concept)
                                     :metadata "")
          response (variable-util/ingest-variable
                    concept-no-metadata
                    {:accept-format :xml
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (re-find #"Request content is too short." (first errors))))))

;; Ingest same concept N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-variable-ingest-test
  (testing "ingest same concept n times ..."
    (let [n 4
          created-concepts (take n (repeatedly n #'variable-util/ingest-variable))]
      (index/wait-until-indexed)
      (is (apply = (map :concept-id created-concepts)))
      (is (= (range 1 (inc n)) (map :revision-id created-concepts))))))

;; Verify ingest is successful for request with content type that has parameters
(deftest content-type-with-parameter-ingest-test
  (let [concept (assoc (variable-util/make-variable-concept)
                       :format (str (mt/with-version
                                      variable-util/content-type
                                      variable-util/schema-version)
                                    "; charset=utf-8"))
        {:keys [status]} (ingest/ingest-concept concept)]
    (index/wait-until-indexed)
    (is (= 201 status))))

(deftest delete-variable-ingest-test
  (testing "Variable ingest:"
    (let [acl-data (variable-util/setup-update-acl (s/context) "PROV1")
          {:keys [user-name group-name group-id token grant-id]} acl-data
          concept (variable-util/make-variable-concept)
          _ (variable-util/ingest-variable concept {:token token})
          _ (index/wait-until-indexed)
          response (ingest/delete-concept concept {:token token})
          {:keys [status concept-id revision-id errors]} response
          _ (println "errors:" errors)
          fetched (mdb/get-concept concept-id revision-id)]
      (testing "delete a variable"
        (is (= 200 status))
        (is (= 2 revision-id))
        (is (:deleted fetched))
        (is (= (:native-id concept)
               (:native-id fetched)))
        (is (:deleted fetched)))
      (testing "create a variable over a variable's tombstone"
        (let [response (variable-util/ingest-variable
                        (variable-util/make-variable-concept) {:token token})
              {:keys [status concept-id revision-id]} response]
          (is (= 200 status))
          (is (= 3 revision-id)))))))

(deftest variable-ingest-permissions-test
  (testing "Variable ingest permissions:"
    (let [;; Groups
          guest-group-id (e/get-or-create-group
                          (s/context) "umm-var-guid1")
          reg-user-group-id (e/get-or-create-group
                             (s/context) "umm-var-guid2")
          ;; Tokens
          guest-token (e/login
                       (s/context) "umm-var-user1" [guest-group-id])
          reg-user-token (e/login
                          (s/context) "umm-var-user2" [reg-user-group-id])
          ;; Grants
          guest-grant-id (e/grant
                          (assoc (s/context) :token guest-token)
                          [{:permissions [:read]
                            :user_type :guest}]
                          :system_identity
                          {:target nil})
          reg-user-grant-id (e/grant
                             (assoc (s/context) :token reg-user-token)
                             [{:permissions [:read]
                               :user_type :registered}]
                             :system_identity
                             {:target nil})
          {update-user-name :user-name
           update-group-name :group-name
           update-token :token
           update-grant-id :grant-id
           update-group-id :group-id} (variable-util/setup-update-acl
                                       (s/context) "PROV1")
          concept (variable-util/make-variable-concept)]
      (testing "disallowed create responses:"
        (are3 [token expected]
          (let [response (variable-util/ingest-variable
                          concept (merge variable-util/default-opts
                                         {:token token}))]
            (is (= expected (:status response))))
          "no token provided"
          nil 401
          "guest user denied"
          guest-token 401
          "regular user denied"
          reg-user-token 401))
       (testing "disallowed delete responses:"
        (are3 [token expected]
          (let [response (ingest/delete-concept
                          concept
                          (merge variable-util/default-opts {:token token}))]
            (is (= expected (:status response))))
          "no token provided"
          nil 401
          "guest user denied"
          guest-token 401
          "regular user denied"
          reg-user-token 401))
      (testing "allowed responses:"
        (let [create-response (variable-util/ingest-variable
                               concept {:token update-token})
              delete-response (ingest/delete-concept concept {:token update-token})]
          (testing "create variable status"
            (is (= 201 (:status create-response))))
          (testing "update variable status"
            (is (= 200 (:status delete-response)))))))))
