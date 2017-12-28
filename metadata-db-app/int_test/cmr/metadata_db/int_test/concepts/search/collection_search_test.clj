(ns cmr.metadata-db.int-test.concepts.search.collection-search-test
  "Contains integration tests for searching collections."
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]
   [cmr.metadata-db.services.messages :as msg]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "REG_PROV1" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}
                                                 {:provider-id "SMAL_PROV2" :small true}))

(deftest find-collections
  (let [coll1 (concepts/create-and-save-concept
               :collection "REG_PROV" 1 1 {:extra-fields {:entry-id "entry-1"
                                                          :entry-title "et1"
                                                          :version-id "v1"
                                                          :short-name "s1"}})
        coll2-1 (concepts/create-and-save-concept
                 :collection "REG_PROV" 2 1 {:extra-fields {:entry-id "entry-2"
                                                            :entry-title "et2"
                                                            :version-id "v1"
                                                            :short-name "s2"}})
        coll2-2 (concepts/create-and-save-concept
                 :collection "REG_PROV" 2 1 {:extra-fields {:entry-id "entry-2"
                                                            :entry-title "et2"
                                                            :version-id "v2"
                                                            :short-name "s2"}})
        coll5-1 (concepts/create-and-save-concept
                 :collection "REG_PROV" 5 1 {:extra-fields {:entry-id "entry-5"
                                                            :entry-title "et5"
                                                            :version-id "v55"
                                                            :short-name "s5"}})
        _ (util/delete-concept (:concept-id coll5-1))
        coll5-2 (assoc coll5-1 :deleted true :revision-id 2 :metadata "" :user-id nil)
        coll3 (concepts/create-and-save-concept
               :collection "SMAL_PROV1" 3 1 {:extra-fields {:entry-id "entry-3"
                                                            :entry-title "et3"
                                                            :version-id "v3"
                                                            :short-name "s3"}})
        coll4-1 (concepts/create-and-save-concept
                 :collection "SMAL_PROV2" 4 1 {:extra-fields {:entry-id "entry-1"
                                                              :entry-title "et1"
                                                              :version-id "v3"
                                                              :short-name "s4"}})
        coll4-2 (concepts/create-and-save-concept
                 :collection "SMAL_PROV2" 4 1 {:extra-fields {:entry-id "entry-1"
                                                              :entry-title "et1"
                                                              :version-id "v4"
                                                              :short-name "s4"}})
        coll4-3 (concepts/create-and-save-concept
                 :collection "SMAL_PROV2" 4 1 {:extra-fields {:entry-id "entry-1"
                                                              :entry-title "et1"
                                                              :version-id "v5"
                                                              :short-name "s4"}})
        coll6-1 (concepts/create-and-save-concept
                 :collection "SMAL_PROV1" 6 1 {:extra-fields {:entry-id "entry-6"
                                                              :entry-title "et6"
                                                              :version-id "v6"
                                                              :short-name "s6"}})
        _ (util/delete-concept (:concept-id coll6-1))
        coll6-2 (assoc coll6-1 :deleted true :revision-id 2 :metadata "" :user-id nil)
        coll7 (concepts/create-and-save-concept
               :collection "REG_PROV1" 1 1 {:extra-fields {:entry-id "entry-7"
                                                           :entry-title "et7"
                                                           :version-id "v7"
                                                           :short-name "s7"}})]
    (testing "find-with-parameters"
      (are3 [collections params]
        (is (= (set collections)
               (set (-> (util/find-concepts :collection params)
                        :concepts
                        util/concepts-for-comparison))))
        "regular provider - provider-id"
        [coll1 coll2-2 coll5-2] {:provider-id "REG_PROV" :latest true}

        "small provider with tombstones - provider-id"
        [coll3 coll6-2] {:provider-id "SMAL_PROV1" :latest true}

        "small provider - provider-id"
        [coll4-3] {:provider-id "SMAL_PROV2" :latest true}

        "regular provider - provider-id, entry-title"
        [coll1] {:provider-id "REG_PROV" :entry-title "et1" :latest true}

        "small provider - provider-id, entry-title"
        [coll3] {:provider-id "SMAL_PROV1" :entry-title "et3" :latest true}

        "regular provider - provider-id, entry-id"
        [coll2-2] {:provider-id "REG_PROV" :entry-id "entry-2" :latest true}

        "small provider - provider-id, entry-id"
        [coll4-3] {:provider-id "SMAL_PROV2" :entry-id "entry-1" :latest true}

        "regular provider - short-name, version-id"
        [coll2-2] {:short-name "s2" :version-id "v2" :latest true}

        "small provider - short-name, version-id"
        [coll4-3] {:short-name "s4" :version-id "v5" :latest true}

        "small provider - match multiple - version-id"
        [coll3] {:version-id "v3" :latest true}

        ;; This test verifies that the provider-id is being used with the small_providers
        ;; table. Otherwise we would get both coll3 and coll4 back (see previous test).
        "small provider - provider-id, version-id"
        [coll4-3] {:provider-id "SMAL_PROV2" :version-id "v5" :latest true}

        "mixed providers - entry-title"
        [coll1 coll4-3] {:entry-title "et1" :latest true}

        "exclude-metadata=true"
        [(dissoc coll3 :metadata)]
        {:provider-id "SMAL_PROV1" :entry-id "entry-3" :exclude-metadata "true" :latest true}

        "exclude-metadata=false"
        [coll3]
        {:provider-id "SMAL_PROV1" :entry-id "entry-3" :exclude-metadata "false" :latest true}

        "regular provider - concept-id"
        [coll1] {:concept-id (:concept-id coll1) :latest true}

        "small provider - concept-id"
        [coll4-3] {:concept-id (:concept-id coll4-1) :latest true}

        "concept-id and version-id - latest"
        [coll4-3] {:concept-id (:concept-id coll4-1) :version-id "v5" :latest true}

        "find none - bad provider-id"
        [] {:provider-id "PROV_NONE" :latest true}

        "find none - provider-id, bad version-id"
        [] {:provider-id "REG_PROV" :version-id "v7" :latest true}

        ;; all revisions
        "provider-id - all revisions"
        [coll4-1 coll4-2 coll4-3] {:provider-id "SMAL_PROV2"}

        "entry-title - all revisions"
        [coll2-1 coll2-2] {:entry-title "et2"}

        "entry-title - all revisions - find latest false"
        [coll2-1 coll2-2] {:entry-title "et2" :latest false}

        "concept-id - all revisions"
        [coll4-1 coll4-2 coll4-3] {:concept-id (:concept-id coll4-1)}

        "concept-id and version-id - all revisions"
        [coll4-1] {:concept-id (:concept-id coll4-1) :version-id "v3"}

        "params containing a vector are ORed"
        [coll1 coll3 coll4-3] {:entry-title ["et1" "et3"] :latest true}

        "multiple ORed param lists are ANDed together"
        [coll3] {:entry-title ["et1" "et3"] :short-name ["s3" "s5"] :latest true}))))

(deftest get-expired-collections-concept-ids
  (let [time-now (tk/now)
        make-coll-expiring-in (fn [prov uniq-num num-revisions num-secs]
                                (let [expire-time (t/plus time-now (t/seconds num-secs))]
                                  (concepts/create-and-save-concept :collection
                                    prov uniq-num num-revisions
                                    {:extra-fields {:delete-time (str expire-time)}})))
        ;; Expired a long time ago.
        coll1 (make-coll-expiring-in "REG_PROV" 1 1 -600000)
        coll2 (make-coll-expiring-in "REG_PROV" 2 2 -600000)
        ;; Expires in the far future
        coll3 (make-coll-expiring-in "REG_PROV" 3 1 500000)
        ;; Doesn't have an expiration date
        coll4 (concepts/create-and-save-concept :collection "REG_PROV" 4 1)
        ;; Small providers
        coll5 (make-coll-expiring-in "SMAL_PROV1" 5 1 -600000)
        coll6 (concepts/create-and-save-concept :collection "SMAL_PROV1" 6 1)
        coll7 (make-coll-expiring-in "SMAL_PROV2" 7 1 -600000)
        coll8 (concepts/create-and-save-concept :collection "SMAL_PROV2" 8 1)]

    (testing "get expired collection concept ids"
      (are [provider-id collections]
           (let [{:keys [status concept-ids]} (util/get-expired-collection-concept-ids provider-id)]
             (= [200 (set (map :concept-id collections))]
                [status (set concept-ids)]))
           "REG_PROV" [coll1 coll2]
           "SMAL_PROV1" [coll5]
           "SMAL_PROV2" [coll7]))

    (testing "invalid or missing provider id"
      (is (= {:status 404, :errors ["Provider with provider-id [PROVNONE] does not exist."]}
             (util/get-expired-collection-concept-ids "PROVNONE")))

      (is (= {:status 400, :errors ["A provider parameter was required but was not provided."]}
             (util/get-expired-collection-concept-ids nil))))))

(deftest find-collections-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [collection] with parameters [foo] is not supported."]}
           (util/find-concepts :collection {:provider-id "REG_PROV"
                                            :short-name "f"
                                            :version-id "v"
                                            :foo "foo"})))))
