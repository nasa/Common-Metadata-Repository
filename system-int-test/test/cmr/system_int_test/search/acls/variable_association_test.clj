(ns cmr.system-int-test.search.acls.variable-association-test
  "Tests searching for variables associations with ACLs in place."
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.variable-util :as variable-util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}
                                          {:grant-all-search? false
                                           :grant-all-ingest? false}))

(deftest variable-and-association-acls-test
  ;; Association is created together with variable at variable ingest time,
  ;; therefore separate association/dissociation tests no longer apply.
  (let [{guest-token :token} (variable-util/setup-guest-acl
                              "umm-var-guid1" "umm-var-user1")
        {registered-token :token} (variable-util/setup-registered-acl
                                   "umm-var-guid2" "umm-var-user2")
        {update-token :token} (variable-util/setup-update-acl
                               (s/context) "PROV1")
        {create-token :token} (variable-util/setup-update-acl
                               (s/context) "PROV1" :create)
        coll-concept-id (->> {:token update-token}
                             (d/ingest "PROV1" (dc/collection))
                             :concept-id)
        _ (index/wait-until-indexed)
        var-concept (variable-util/make-variable-concept
                      {:Name "Variable1"}
                      {:native-id "var1"
                       :coll-concept-id coll-concept-id})
        system-token-response (variable-util/ingest-variable-with-association
                                var-concept)
        create-token-response (variable-util/ingest-variable-with-association
                                var-concept
                                {:token create-token})
        update-token-response (variable-util/ingest-variable-with-association
                                var-concept
                                {:token update-token})
        no-token-response (variable-util/ingest-variable-with-association
                            var-concept
                            {:token nil})
        guest-token-response (variable-util/ingest-variable-with-association
                               var-concept
                               {:token guest-token})
        registered-token-response (variable-util/ingest-variable-with-association
                                    var-concept
                                    {:token guest-token})]
    (index/wait-until-indexed)
      ;; ingest variable and association using system-token is successful.
      (is (= 201 (:status system-token-response)))

      ;; There is permission to ingest variable and association using create-token and update-token
      ;; but the associated collection is not visible.
      (is (= 422 (:status create-token-response)))
      (is (= 422 (:status update-token-response)))

      ;; No permissions to ingest variable and association using other tokens.
      (is (= 401 (:status no-token-response)))
      (is (= 401 (:status guest-token-response)))
      (is (= 401 (:status registered-token-response)))))
