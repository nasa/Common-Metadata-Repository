(ns cmr.system-int-test.bootstrap.fingerprint-test
  "Integration test for CMR fingerprint operations."
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.variable-util :as variable]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})]))

(deftest fingerprint-variable-by-concept-id
  (testing "Update fingerprint of variable by concept-id"
    (s/only-with-real-database
     (let [var1 (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 1)
           var2 (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 2)
           var1-concept-id (:concept-id var1)
           var1-concept (mdb/get-concept var1-concept-id)
           var1-fingerprint (get-in var1-concept [:extra-fields :fingerprint])
           var2-concept-id (:concept-id var2)
           var2-concept (mdb/get-concept var2-concept-id)
           var2-fingerprint (get-in var2-concept [:extra-fields :fingerprint])]
       ;; Sanity check variable concept retrieved from metadata-db
       (is (= var1-concept-id (:concept-id var1-concept)))
       (is (= 1 (:revision-id var1-concept)))

       (testing "without permission"
         (let [{:keys [status errors]} (bootstrap/fingerprint-variable-by-concept-id
                                        var1-concept-id nil)]
           (is (= [401 ["You do not have permission to perform that action."]]
                  [status errors]))))

       (testing "with permission, fingerprint didn't change, no revision is created."
         (let [{:keys [status errors]} (bootstrap/fingerprint-variable-by-concept-id
                                        var1-concept-id)
               latest-revision-concept (mdb/get-concept var1-concept-id)]
           (is (= [200 nil] [status errors]))
           (is (= 1 (:revision-id latest-revision-concept)))
           (is (= var1-fingerprint
                  (get-in latest-revision-concept [:extra-fields :fingerprint])))))

       (testing "with permission, fingerprint changed, new revision is created."
         (let [updated-concept (-> var1-concept
                                   (assoc :revision-id (inc (:revision-id var1-concept)))
                                   ;; update the existing fingerprint to a different value
                                   (assoc-in [:extra-fields :fingerprint] "dummy-fingerprint"))
               _ (mdb/save-concept updated-concept)
               updated-concept (mdb/get-concept var1-concept-id)]
           ;; the new revision is 2 which has a wrong fingerprint
           (is (= var1-concept-id (:concept-id updated-concept)))
           (is (= 2 (:revision-id updated-concept)))
           (is (= "dummy-fingerprint" (get-in updated-concept [:extra-fields :fingerprint])))
           ;; now update the fingerprint of the variable, a new revision of the variable concept
           ;; should be created and the fingerprint will be fixed.
           (let [{:keys [status]} (bootstrap/fingerprint-variable-by-concept-id var1-concept-id)
                 latest-revision-concept (mdb/get-concept var1-concept-id)]
             (is (= 200 status))
             (is (= var1-concept-id (:concept-id latest-revision-concept)))
             (is (= 3 (:revision-id latest-revision-concept)))
             (is (= var1-fingerprint
                    (get-in latest-revision-concept [:extra-fields :fingerprint]))))))

       (testing "update fingerprint of a deleted variable"
         (let [{:keys [concept-id revision-id]} (ingest/delete-concept var2-concept)
               latest-revision-concept (mdb/get-concept var2-concept-id)]
           (is (= var2-concept-id (:concept-id latest-revision-concept)))
           (is (= 2 (:revision-id latest-revision-concept)))
           (is (:deleted latest-revision-concept))
           (is (= var2-fingerprint
                  (get-in latest-revision-concept [:extra-fields :fingerprint])))
           (let [{:keys [status]} (bootstrap/fingerprint-variable-by-concept-id var2-concept-id)
                 latest-revision-concept (mdb/get-concept var2-concept-id)]
             (is (= 200 status))
             (is (= var2-concept-id (:concept-id latest-revision-concept)))
             (is (= 2 (:revision-id latest-revision-concept)))
             (is (:deleted latest-revision-concept))
             (is (= var2-fingerprint
                    (get-in latest-revision-concept [:extra-fields :fingerprint]))))))

       (testing "Update fingerprint of a non-existent variable"
         (let [{:keys [status errors]} (bootstrap/fingerprint-variable-by-concept-id "V1000-PROV1")]
           (is (= [422 ["Variable with concept-id [V1000-PROV1] does not exist"]]
                  [status errors]))))))))

(deftest fingerprint-variables-for-provider
  (testing "Update fingerprint of variables for a single provider"
    (s/only-with-real-database
     (let [var1 (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 1)
           var2 (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 2)
           var3 (variable/ingest-variable-with-attrs {:provider-id "PROV2"} {} 1)
           var1-concept-id (:concept-id var1)
           var1-concept (mdb/get-concept var1-concept-id)
           var1-fingerprint (get-in var1-concept [:extra-fields :fingerprint])
           var2-concept-id (:concept-id var2)
           var2-concept (mdb/get-concept var2-concept-id)
           var2-fingerprint (get-in var2-concept [:extra-fields :fingerprint])
           var3-concept-id (:concept-id var3)
           var3-concept (mdb/get-concept var3-concept-id)
           var3-fingerprint (get-in var3-concept [:extra-fields :fingerprint])
           ;; now change the fingerprint of var1 and var3 to a different vaule
           latest-var1 (-> var1-concept
                           (assoc :revision-id (inc (:revision-id var1-concept)))
                           ;; update the existing fingerprint to a different value
                           (assoc-in [:extra-fields :fingerprint] "dummy-fingerprint"))
           _ (mdb/save-concept latest-var1)
           latest-var1-concept (mdb/get-concept var1-concept-id)
           latest-var3 (-> var3-concept
                           (assoc :revision-id (inc (:revision-id var3-concept)))
                           ;; update the existing fingerprint to a different value
                           (assoc-in [:extra-fields :fingerprint] "dummy-fingerprint"))
           _ (mdb/save-concept latest-var3)
           latest-var3-concept (mdb/get-concept var3-concept-id)]

       ;; Sanity check variable concepts retrieved from metadata-db
       (is (= var1-concept-id (:concept-id latest-var1-concept)))
       (is (= 2 (:revision-id latest-var1-concept)))
       (is (= "dummy-fingerprint" (get-in latest-var1-concept [:extra-fields :fingerprint])))
       (is (= var3-concept-id (:concept-id latest-var3-concept)))
       (is (= 2 (:revision-id latest-var3-concept)))
       (is (= "dummy-fingerprint" (get-in latest-var3-concept [:extra-fields :fingerprint])))

       (testing "without permission"
         (let [{:keys [status errors]} (bootstrap/fingerprint-variables-by-provider "PROV1" nil)
               latest-var1-concept (mdb/get-concept var1-concept-id)]
           (is (= 401 status))
           (is (= ["You do not have permission to perform that action."] errors))
           ;; var1 fingerprint is not updated
           (is (= var1-concept-id (:concept-id latest-var1-concept)))
           (is (= 2 (:revision-id latest-var1-concept)))
           (is (= "dummy-fingerprint" (get-in latest-var1-concept [:extra-fields :fingerprint])))))

       (testing "with permission"
         (let [{:keys [status errors]} (bootstrap/fingerprint-variables-by-provider "PROV1")
               latest-var1-concept (mdb/get-concept var1-concept-id)
               latest-var2-concept (mdb/get-concept var2-concept-id)
               latest-var3-concept (mdb/get-concept var3-concept-id)]
           (is (= 200 status))
           (is (nil? errors))
           ;; var1 fingerprint is updated
           (is (= var1-concept-id (:concept-id latest-var1-concept)))
           (is (= 3 (:revision-id latest-var1-concept)))
           (is (= var1-fingerprint (get-in latest-var1-concept [:extra-fields :fingerprint])))
           ;; var2 fingerprint does not need update
           (is (= var2-concept-id (:concept-id latest-var2-concept)))
           (is (= 1 (:revision-id latest-var2-concept)))
           (is (= var2-fingerprint (get-in latest-var2-concept [:extra-fields :fingerprint])))
           ;; var3 fingerprint is not updated
           (is (= var3-concept-id (:concept-id latest-var3-concept)))
           (is (= 2 (:revision-id latest-var3-concept)))
           (is (= "dummy-fingerprint" (get-in latest-var3-concept [:extra-fields :fingerprint])))))

       (testing "Update fingerprint on a non-existent provider"
         (let [{:keys [status errors]} (bootstrap/fingerprint-variables-by-provider "PROVX")]
           (is (= 422 status))
           (is (= ["Provider [PROVX] does not exist"] errors))))))))

(deftest fingerprint-all-variables
  (testing "Update fingerprint of all variables"
    (s/only-with-real-database
     (let [var1 (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 1)
           var2 (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 2)
           var3 (variable/ingest-variable-with-attrs {:provider-id "PROV2"} {} 1)
           var1-concept-id (:concept-id var1)
           var1-concept (mdb/get-concept var1-concept-id)
           var1-fingerprint (get-in var1-concept [:extra-fields :fingerprint])
           var2-concept-id (:concept-id var2)
           var2-concept (mdb/get-concept var2-concept-id)
           var2-fingerprint (get-in var2-concept [:extra-fields :fingerprint])
           var3-concept-id (:concept-id var3)
           var3-concept (mdb/get-concept var3-concept-id)
           var3-fingerprint (get-in var3-concept [:extra-fields :fingerprint])
           ;; now change the fingerprint of var1 and var3 to a different vaule
           latest-var1 (-> var1-concept
                           (assoc :revision-id (inc (:revision-id var1-concept)))
                           ;; update the existing fingerprint to a different value
                           (assoc-in [:extra-fields :fingerprint] "dummy-fingerprint"))
           _ (mdb/save-concept latest-var1)
           latest-var1-concept (mdb/get-concept var1-concept-id)
           latest-var3 (-> var3-concept
                           (assoc :revision-id (inc (:revision-id var3-concept)))
                           ;; update the existing fingerprint to a different value
                           (assoc-in [:extra-fields :fingerprint] "dummy-fingerprint"))
           _ (mdb/save-concept latest-var3)
           latest-var3-concept (mdb/get-concept var3-concept-id)]

       ;; Sanity check variable concepts retrieved from metadata-db
       (is (= var1-concept-id (:concept-id latest-var1-concept)))
       (is (= 2 (:revision-id latest-var1-concept)))
       (is (= "dummy-fingerprint" (get-in latest-var1-concept [:extra-fields :fingerprint])))
       (is (= var3-concept-id (:concept-id latest-var3-concept)))
       (is (= 2 (:revision-id latest-var3-concept)))
       (is (= "dummy-fingerprint" (get-in latest-var3-concept [:extra-fields :fingerprint])))

       (testing "without permission"
         (let [{:keys [status errors]} (bootstrap/fingerprint-all-variables nil)
               latest-var1-concept (mdb/get-concept var1-concept-id)]
           (is (= 401 status))
           (is (= ["You do not have permission to perform that action."] errors))
           ;; var1 fingerprint is not updated
           (is (= var1-concept-id (:concept-id latest-var1-concept)))
           (is (= 2 (:revision-id latest-var1-concept)))
           (is (= "dummy-fingerprint" (get-in latest-var1-concept [:extra-fields :fingerprint])))))

       (testing "with permission"
         (let [{:keys [status errors]} (bootstrap/fingerprint-all-variables)
               latest-var1-concept (mdb/get-concept var1-concept-id)
               latest-var2-concept (mdb/get-concept var2-concept-id)
               latest-var3-concept (mdb/get-concept var3-concept-id)]
           (is (= 200 status))
           (is (nil? errors))
           ;; var1 fingerprint is updated
           (is (= var1-concept-id (:concept-id latest-var1-concept)))
           (is (= 3 (:revision-id latest-var1-concept)))
           (is (= var1-fingerprint (get-in latest-var1-concept [:extra-fields :fingerprint])))
           ;; var2 fingerprint does not need update
           (is (= var2-concept-id (:concept-id latest-var2-concept)))
           (is (= 1 (:revision-id latest-var2-concept)))
           (is (= var2-fingerprint (get-in latest-var2-concept [:extra-fields :fingerprint])))
           ;; var3 fingerprint is updated
           (is (= var3-concept-id (:concept-id latest-var3-concept)))
           (is (= 3 (:revision-id latest-var3-concept)))
           (is (= var3-fingerprint (get-in latest-var3-concept [:extra-fields :fingerprint])))))))))
