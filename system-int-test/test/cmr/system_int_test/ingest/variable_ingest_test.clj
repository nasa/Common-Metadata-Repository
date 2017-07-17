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
   [cmr.mock-echo.client.echo-util :as echo-util]
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
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}
                                          {:grant-all-ingest? false}))

(deftest variable-ingest-test
  (let [{token :token} (variable-util/setup-update-acl
                        (s/context) "PROV1" "user1" "update-group")]
    (testing "ingest of a new variable concept"
      (let [concept (variable-util/make-variable-concept)
            {:keys [concept-id revision-id]} (variable-util/ingest-variable
                                               concept
                                               (variable-util/token-opts token))]
        (index/wait-until-indexed)
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))))
    (testing "ingest of a variable concept with a revision id"
      (let [concept (variable-util/make-variable-concept {} {:revision-id 5})
            {:keys [concept-id revision-id]} (variable-util/ingest-variable
                                              concept
                                              (variable-util/token-opts token))]
        (index/wait-until-indexed)
        (is (= 5 revision-id))
        (is (mdb/concept-exists-in-mdb? concept-id 5))))))

;; Verify that user-id is saved from User-Id or token header
(deftest variable-ingest-token-vs-user-id-test
  (testing "user id from token"
    (let [{token :token} (variable-util/setup-update-acl
                          (s/context) "PROV1" "user1" "update-group")
          concept (variable-util/make-variable-concept)
          opts (merge variable-util/default-opts {:token token})
          {:keys [concept-id revision-id]} (variable-util/ingest-variable
                                            concept opts)]
      (index/wait-until-indexed)
      (ingest/assert-user-id concept-id revision-id "user1")))
  (testing (str "both user-id and token in the header results in the revision "
                "getting user id from user-id header")
    (variable-util/setup-update-acl (s/context) "PROV1" "user4" "update-group")
    (let [{token :token} (variable-util/setup-update-acl
                          (s/context) "PROV1" "user5" "update-group")
          concept (variable-util/make-variable-concept)
          opts (merge variable-util/default-opts {:user-id "user4"
                                                  :token token})
          {:keys [concept-id revision-id]} (variable-util/ingest-variable
                                            concept opts)]
      (index/wait-until-indexed)
      (ingest/assert-user-id concept-id revision-id "user4")))
  (testing "neither user-id nor token in the header"
    (let [concept (variable-util/make-variable-concept)
          opts variable-util/default-opts
          {status :status} (variable-util/ingest-variable concept opts)]
      (is (= 401 status)))))

(deftest update-concept-with-new-user-from-token
  (util/are3 [ingest-header1 expected-user-id1
              ingest-header2 expected-user-id2
              ingest-header3 expected-user-id3]
    (let [concept (variable-util/make-variable-concept)
          {:keys [concept-id revision-id]} (variable-util/ingest-variable
                                            concept
                                            (merge variable-util/default-opts
                                                   ingest-header1))]
      (ingest/ingest-concept concept (merge variable-util/default-opts
                                            ingest-header2))
      (ingest/ingest-concept concept (merge variable-util/default-opts
                                            ingest-header3))
      (index/wait-until-indexed)
      (ingest/assert-user-id concept-id revision-id expected-user-id1)
      (ingest/assert-user-id concept-id (inc revision-id) expected-user-id2)
      (ingest/assert-user-id concept-id (inc (inc revision-id)) expected-user-id3))
    "user id from token"
    (variable-util/setup-update-acl
      (s/context) "PROV1" "user1" "update-group") "user1"
    (variable-util/setup-update-acl
      (s/context) "PROV1" "user2" "update-group") "user2"
    (variable-util/setup-update-acl
      (s/context) "PROV1" "user3" "update-group") "user3"))

;; XXX write `update-concept-with-new-user-from-user-id`
    ; "user id from user-id header"
    ; {:user-id "user1"} "user1"
    ; {:user-id "user2"} "user2"
    ; {:user-id "user3"} "user3"
    ; {:user-id nil} nil))

;; Variable with concept-id ingest and update scenarios.
(deftest variable-w-concept-id-ingest-test
  (let [{token :token} (variable-util/setup-update-acl
                        (s/context) "PROV1" "user1" "update-group")
        supplied-concept-id "V1000-PROV1"
        concept (variable-util/make-variable-concept
                 {}
                 {:concept-id supplied-concept-id
                  :native-id "Atlantic-1"})]
    (testing "ingest of a new variable concept with concept-id present"
      (let [{:keys [concept-id revision-id]} (variable-util/ingest-variable
                                               concept
                                               (variable-util/token-opts token))]
        (index/wait-until-indexed)
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= [supplied-concept-id 1] [concept-id revision-id]))))

    (testing "Update the concept with the concept-id"
      (let [{:keys [concept-id revision-id]} (variable-util/ingest-variable
                                              concept
                                              (variable-util/token-opts token))]
        (index/wait-until-indexed)
        (is (= [supplied-concept-id 2] [concept-id revision-id]))))

    (testing "update the concept without the concept-id"
      (let [{:keys [concept-id revision-id]} (variable-util/ingest-variable
                                              (dissoc concept :concept-id)
                                              (variable-util/token-opts token))]
        (index/wait-until-indexed)
        (is (= [supplied-concept-id 3] [concept-id revision-id]))))

    (testing "update concept with a different concept-id is invalid"
      (let [{:keys [status errors]} (variable-util/ingest-variable
                                     (assoc concept :concept-id "V1111-PROV1")
                                     (variable-util/token-opts token))]
        (index/wait-until-indexed)
        (is (= [409 [(str "A concept with concept-id [V1000-PROV1] and "
                          "native-id [Atlantic-1] already exists for "
                          "concept-type [:variable] provider-id [PROV1]. "
                          "The given concept-id [V1111-PROV1] and native-id "
                          "[Atlantic-1] would conflict with that one.")]]
               [status errors]))))))

;; Verify that the accept header works
(deftest variable-ingest-accept-header-response-test
  (let [{token :token} (variable-util/setup-update-acl
                        (s/context) "PROV1" "user1" "update-group")]
    (testing "json response"
      (let [response (variable-util/ingest-variable
                      (variable-util/make-variable-concept)
                      (merge (variable-util/token-opts token)
                             {:raw? true}))]
        (is (= 1
               (:revision-id (ingest/parse-ingest-body :json response))))))
    (testing "xml response"
      (let [response (variable-util/ingest-variable
                      (variable-util/make-variable-concept)
                      (merge (variable-util/token-opts token)
                             {:accept-format :xml
                              :raw? true}))]
        (is (= 2
               (:revision-id (ingest/parse-ingest-body :xml response))))))))

;; Verify that the accept header works with returned errors
(deftest variable-ingest-with-errors-accept-header-test
  (let [{token :token} (variable-util/setup-update-acl
                        (s/context) "PROV1" "user1" "update-group")]
    (testing "json response"
      (let [concept-no-metadata (assoc (variable-util/make-variable-concept)
                                       :metadata "")
            response (variable-util/ingest-variable
                      concept-no-metadata
                      (merge (variable-util/token-opts token)
                             {:raw? true}))
            {:keys [errors]} (ingest/parse-ingest-body :json response)]
        (is (re-find #"Request content is too short." (first errors)))))
    (testing "xml response"
      (let [concept-no-metadata (assoc (variable-util/make-variable-concept)
                                       :metadata "")
            response (variable-util/ingest-variable
                      concept-no-metadata
                      (merge (variable-util/token-opts token)
                             {:accept-format :xml
                              :raw? true}))
            {:keys [errors]} (ingest/parse-ingest-body :xml response)]
        (is (re-find #"Request content is too short." (first errors)))))))

;; Ingest same concept N times and verify same concept-id is returned and
;; revision id is 1 greater on each subsequent ingest
(deftest repeat-same-variable-ingest-test
  (testing "ingest same concept n times ..."
    (let [{token :token} (variable-util/setup-update-acl
                          (s/context) "PROV1" "user1" "update-group")
          ingester #(variable-util/ingest-variable
                     (variable-util/make-variable-concept)
                     (variable-util/token-opts token))
          n 4
          created-concepts (take n (repeatedly n ingester))]
      (index/wait-until-indexed)
      (is (apply = (map :concept-id created-concepts)))
      (is (= (range 1 (inc n)) (map :revision-id created-concepts))))))

;; Verify ingest is successful for request with content type that has parameters
(deftest content-type-with-parameter-ingest-test
  (let [{token :token} (variable-util/setup-update-acl
                          (s/context) "PROV1" "user1" "update-group")
        concept (assoc (variable-util/make-variable-concept)
                       :format (str (mt/with-version
                                      variable-util/content-type
                                      variable-util/schema-version)
                                    "; charset=utf-8"))
        {:keys [status]} (ingest/ingest-concept
                          concept
                          (variable-util/token-opts token))]
    (index/wait-until-indexed)
    (is (= 201 status))))

(deftest delete-variable-ingest-test
  (testing "Variable ingest:"
    (let [{token :token} (variable-util/setup-update-acl (s/context) "PROV1")
          concept (variable-util/make-variable-concept)
          _ (variable-util/ingest-variable concept {:token token})
          _ (index/wait-until-indexed)
          response (ingest/delete-concept concept {:token token})
          {:keys [status concept-id revision-id errors]} response
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
