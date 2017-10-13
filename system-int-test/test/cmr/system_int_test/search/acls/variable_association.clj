(ns cmr.system-int-test.search.acls.variable-association
  "Tests searching for variables associations with ACLs in place."
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as variable-util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}
                                          {:grant-all-search? false
                                           :grant-all-ingest? false}))

(deftest variable-association-acls-test
  (let [{guest-token :token} (variable-util/setup-guest-acl
                              "umm-var-guid1" "umm-var-user1")
        {registered-token :token} (variable-util/setup-registered-acl
                                   "umm-var-guid2" "umm-var-user2")
        {update-token :token} (variable-util/setup-update-acl
                               (s/context) "PROV1")
        {create-token :token} (variable-util/setup-update-acl
                               (s/context) "PROV1" :create)
        {delete-token :token} (variable-util/setup-update-acl
                               (s/context) "PROV1" :delete)
        coll-concept-id (->> {:token update-token}
                             (d/ingest "PROV1" (dc/collection))
                             :concept-id)
        var-concept (variable-util/make-variable-concept)
        {var-concept-id :concept-id} (variable-util/ingest-variable
                                      var-concept
                                      {:token update-token})]
    (index/wait-until-indexed)
    (testing "disallowed variable association responses"
      (are3 [token expected]
        (let [response (au/associate-by-concept-ids
                        token
                        var-concept-id
                        [{:concept-id coll-concept-id}])]
          (is (= expected (:status response))))
        "no token provided"
        nil 401
        "guest user denied"
        guest-token 401
        "regular user denied"
        registered-token 401))
    (testing "disallowed variable dissociation responses"
      (are3 [token expected]
        (let [response (au/dissociate-by-concept-ids
                        token
                        var-concept-id
                        [{:concept-id coll-concept-id}])]
          (is (= expected (:status response)))
          (is (string/includes?
               "You do not have permission to perform that action."
               (first (:errors response)))))
        "no token provided"
        nil 401
        "guest user denied"
        guest-token 401
        "regular user denied"
        registered-token 401))
    (testing "allowed variable association responses"
      (let [response (au/associate-by-concept-ids
                      create-token
                      var-concept-id
                      [{:concept-id coll-concept-id}])]
        (is (= 200 (:status response)))))
    (testing "allowed variable dissociation responses"
      (let [response (au/dissociate-by-concept-ids
                      delete-token
                      var-concept-id
                      [{:concept-id coll-concept-id}])]
        (is (= 200 (:status response)))))))
