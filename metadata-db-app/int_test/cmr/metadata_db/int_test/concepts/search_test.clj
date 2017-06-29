(ns cmr.metadata-db.int-test.concepts.search-test
  "Contains integration tests for getting concepts. Tests gets with various
  configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.data]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as msg]
            [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [cmr.common.util :refer [are3]]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "REG_PROV1" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}
                                                 {:provider-id "SMAL_PROV2" :small true}))

(defn concepts-for-comparison
  "Removes revision-date, transaction-id, and created-at from concepts so they can be compared."
  [concepts]
  (map #(dissoc % :revision-date :transaction-id :created-at) concepts))

(deftest search-by-concept-revision-id-tuples
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [coll1 (util/create-and-save-collection provider-id 1 3)
          coll2 (util/create-and-save-collection provider-id 2 3)
          coll3 (util/create-and-save-collection provider-id 3 3)
          gran1 (util/create-and-save-granule provider-id coll1 1 2)
          gran2 (util/create-and-save-granule provider-id coll2 2 2)
          group1 (util/create-and-save-group provider-id 4 3)]
      (are [item-revision-tuples]
           (let [tuples (map #(update-in % [0] :concept-id) item-revision-tuples)
                 {:keys [status concepts]} (util/get-concepts tuples)
                 expected-concepts (map (fn [[item revision]]
                                          (assoc (util/expected-concept item)
                                                 :revision-id revision))
                                        item-revision-tuples)]
             (and (= 200 status)
                  (= expected-concepts (concepts-for-comparison concepts))))
           ; one collection
           [[coll1 1]]
           ;; two collections
           [[coll1 2] [coll2 1]]
           ;; multiple versions of same collection
           [[coll1 2] [coll1 1]]
           ; granules and collections
           [[gran1 2] [gran1 1] [gran2 2] [coll3 3] [coll1 2]]
           ;; group revisions
           [[group1 2] [group1 1]]
           ;; group and collectionss
           [[group1 1] [coll1 2]]))))

(deftest get-concepts-with-one-invalid-revision-id-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [coll1 (util/create-and-save-collection provider-id 1)
          tuples [[(:concept-id coll1) 1]
                  [(:concept-id coll1) 2]
                  ["C2-REG_PROV" 1]]
          {:keys [status errors]} (util/get-concepts tuples)]
      (is (= 404 status))
      (is (= #{(msg/concept-with-concept-id-and-rev-id-does-not-exist (:concept-id coll1) 2)
               (msg/concept-with-concept-id-and-rev-id-does-not-exist "C2-REG_PROV" 1)}
             (set errors))))))

(deftest get-concepts-with-one-invalid-id-test-allow-missing
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [coll1 (util/create-and-save-collection provider-id 1)
          tuples [[(:concept-id coll1) 1]
                  [(:concept-id coll1) 2]
                  ["C2-REG_PROV" 1]]
          {:keys [status concepts]} (util/get-concepts tuples true)]
      (is (= 200 status))
      (is (= [(:concept-id coll1)] (map :concept-id concepts))))))

(deftest get-latest-by-concept-id
  (let [coll1 (util/create-and-save-collection "REG_PROV" 1 3)
        coll2 (util/create-and-save-collection "REG_PROV" 2 1)
        coll3 (util/create-and-save-collection "SMAL_PROV1" 3 3)
        gran1 (util/create-and-save-granule "REG_PROV" coll1 1 2)
        gran2 (util/create-and-save-granule "REG_PROV" coll2 2 1)
        group1 (util/create-and-save-group "REG_PROV" 4 1)]
    (are [item-revision-tuples]
         (let [ids (map #(:concept-id (first %)) item-revision-tuples)
               {:keys [status concepts]} (util/get-latest-concepts ids)
               expected-concepts (map (fn [[item revision]]
                                        (assoc (util/expected-concept item)
                                               :revision-id revision))
                                      item-revision-tuples)]
           (and (is (= 200 status))
                (is (= expected-concepts (concepts-for-comparison concepts)))))
         ; one collection
         [[coll1 3]]
         ;; two collections
         [[coll1 3] [coll2 1]]
         ;; granules
         [[gran1 2] [gran2 1]]
         ; granules and collections
         [[gran1 2] [gran2 1] [coll3 3] [coll2 1]]
         ;; groups
         [[group1 1]]
         ;; granules and groups
         [[gran1 2] [group1 1]])))

(deftest get-latest-concepts-with-missing-concept-test
  (let [coll1 (util/create-and-save-collection "REG_PROV" 1)
        ids [(:concept-id coll1) "C1234-REG_PROV"]
        {:keys [status errors]} (util/get-latest-concepts ids)]
    (is (= 404 status))
    (is (= #{(msg/concept-does-not-exist "C1234-REG_PROV")}
           (set errors)))))

(deftest get-latest-concepts-with-missing-concept-allow-missing-test
  (let [coll1 (util/create-and-save-collection "REG_PROV" 1)
        ids [(:concept-id coll1) "C1234-REG_PROV"]
        {:keys [status concepts]} (util/get-latest-concepts ids true)]
    (is (= 200 status))
    (is (= [(:concept-id coll1)] (map :concept-id concepts)))))

(defn- save-collection
  "Given a provider-id, unique number, and attribute map, save a version of the collection."
  [provider-id uniq-num attributes]
  (let [concept (util/collection-concept provider-id uniq-num attributes)
        {:keys [concept-id revision-id]} (util/save-concept concept)]
    (assoc concept :revision-id revision-id :concept-id concept-id)))

(deftest find-collections
  (let [coll1 (save-collection "REG_PROV" 1 {:extra-fields {:entry-id "entry-1"
                                                            :entry-title "et1"
                                                            :version-id "v1"
                                                            :short-name "s1"}})
        coll2-1 (save-collection "REG_PROV" 2 {:extra-fields {:entry-id "entry-2"
                                                              :entry-title "et2"
                                                              :version-id "v1"
                                                              :short-name "s2"}})
        coll2-2 (save-collection "REG_PROV" 2 {:extra-fields {:entry-id "entry-2"
                                                              :entry-title "et2"
                                                              :version-id "v2"
                                                              :short-name "s2"}})
        coll5-1 (save-collection "REG_PROV" 5 {:extra-fields {:entry-id "entry-5"
                                                              :entry-title "et5"
                                                              :version-id "v55"
                                                              :short-name "s5"}})
        _ (util/delete-concept (:concept-id coll5-1))
        coll5-2 (assoc coll5-1 :deleted true :revision-id 2 :metadata "" :user-id nil)
        coll3 (save-collection "SMAL_PROV1" 3 {:extra-fields {:entry-id "entry-3"
                                                              :entry-title "et3"
                                                              :version-id "v3"
                                                              :short-name "s3"}})
        coll4-1 (save-collection "SMAL_PROV2" 4 {:extra-fields {:entry-id "entry-1"
                                                                :entry-title "et1"
                                                                :version-id "v3"
                                                                :short-name "s4"}})
        coll4-2 (save-collection "SMAL_PROV2" 4 {:extra-fields {:entry-id "entry-1"
                                                                :entry-title "et1"
                                                                :version-id "v4"
                                                                :short-name "s4"}})
        coll4-3 (save-collection "SMAL_PROV2" 4 {:extra-fields {:entry-id "entry-1"
                                                                :entry-title "et1"
                                                                :version-id "v5"
                                                                :short-name "s4"}})
        coll6-1 (save-collection "SMAL_PROV1" 6 {:extra-fields {:entry-id "entry-6"
                                                                :entry-title "et6"
                                                                :version-id "v6"
                                                                :short-name "s6"}})
        _ (util/delete-concept (:concept-id coll6-1))
        coll6-2 (assoc coll6-1 :deleted true :revision-id 2 :metadata "" :user-id nil)
        coll7 (save-collection "REG_PROV1" 1 {:extra-fields {:entry-id "entry-7"
                                                             :entry-title "et7"
                                                             :version-id "v7"
                                                             :short-name "s7"}})]
    (testing "find-with-parameters"
      (are3 [collections params]
            (= (set collections)
               (set (-> (util/find-concepts :collection params)
                        :concepts
                        concepts-for-comparison)))
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
                                  (util/create-and-save-collection
                                    prov uniq-num num-revisions
                                    {:extra-fields {:delete-time (str expire-time)}})))
        ;; Expired a long time ago.
        coll1 (make-coll-expiring-in "REG_PROV" 1 1 -600000)
        coll2 (make-coll-expiring-in "REG_PROV" 2 2 -600000)
        ;; Expires in the far future
        coll3 (make-coll-expiring-in "REG_PROV" 3 1 500000)
        ;; Doesn't have an expiration date
        coll4 (util/create-and-save-collection "REG_PROV" 4 1)
        ;; Small providers
        coll5 (make-coll-expiring-in "SMAL_PROV1" 5 1 -600000)
        coll6 (util/create-and-save-collection "SMAL_PROV1" 6 1)
        coll7 (make-coll-expiring-in "SMAL_PROV2" 7 1 -600000)
        coll8 (util/create-and-save-collection "SMAL_PROV2" 8 1)]

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

(deftest find-granules
  (let [coll1 (util/create-and-save-collection "REG_PROV" 1 1)
        gran1 (util/create-and-save-granule "REG_PROV"
                                            coll1
                                            1
                                            3
                                            {:native-id "G1-NAT"
                                             :extra-fields {:granule-ur "G1-UR"}})
        gran2 (util/create-and-save-granule "REG_PROV"
                                            coll1
                                            2
                                            2
                                            {:native-id "G2-NAT"
                                             :extra-fields {:granule-ur "G2-UR"}})]
    (testing "find with parameters"
      (testing "latest revsions"
        (are3 [granules params]
              (= (set (map util/expected-concept granules))
                 (set (-> (util/find-latest-concepts :granule params)
                          :concepts
                          concepts-for-comparison)))
              ;; These are the only valid combinations for granules
              "provider-id, granule-ur"
              [gran1] {:provider-id "REG_PROV" :granule-ur "G1-UR"}

              "provider-id, native-id"
              [gran2] {:provider-id "REG_PROV" :native-id "G2-NAT"}

              "no metadata"
              [(dissoc gran1 :metadata)] {:provider-id "REG_PROV"
                                          :granule-ur "G1-UR"
                                          :exclude-metadata true}))

      (testing "all revisions"
        (are3 [rev-count params]
              (= rev-count
                 (count (-> (util/find-concepts :granule params)
                            :concepts)))
              "provider-id, granule-ur - two revisions"
              2 {:provider-id "REG_PROV" :granule-ur "G2-UR"}

              "provider-id, native-id - three revisons"
              3 {:provider-id "REG_PROV":native-id "G1-NAT"})))))


(deftest find-collections-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [collection] with parameters [foo] is not supported."]}
           (util/find-concepts :collection {:provider-id "REG_PROV"
                                            :short-name "f"
                                            :version-id "v"
                                            :foo "foo"})))))

(deftest find-granules-with-invalid-parameters
  (testing "invalid combination"
    (is (= {:status 400
            :errors ["Finding concept type [granule] with parameter combination [provider-id, granule-ur, native-id] is not supported."]}
           (util/find-concepts :granule {:provider-id "REG_PROV"
                                         :granule-ur "GRAN_UR"
                                         :native-id "NV1"})))))

(deftest find-tags
  (let [tag1 (util/create-and-save-tag 1 3)
        tag2 (util/create-and-save-tag 2 2)]
    (testing "find latest revsions"
      (are3 [tags params]
            (= (set tags)
               (set (->> (util/find-latest-concepts :tag params)
                         :concepts
                         (map #(dissoc % :provider-id :revision-date :transaction-id)))))
            "with metadata"
            [tag1 tag2] {}

            "exclude metadata"
            [(dissoc tag1 :metadata) (dissoc tag2 :metadata)] {:exclude-metadata true}))

    (testing "find all revisions"
      (let [num-of-tags (-> (util/find-concepts :tag)
                            :concepts
                            count)]
        (is (= 5 num-of-tags))))))

(deftest find-tags-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [tag] with parameters [provider-id] is not supported."]}
           (util/find-concepts :tag {:provider-id "REG_PROV"})))))

(deftest find-tag-associations
  (let [coll1 (save-collection "REG_PROV" 1 {:extra-fields {:entry-id "entry-1"
                                                            :entry-title "et1"
                                                            :version-id "v1"
                                                            :short-name "s1"}})
        coll2 (save-collection "REG_PROV" 2 {:extra-fields {:entry-id "entry-2"
                                                              :entry-title "et2"
                                                              :version-id "v1"
                                                              :short-name "s2"}})
        associated-tag (util/create-and-save-tag 1)
        tag-association1 (util/create-and-save-tag-association coll1 associated-tag 1 3)
        tag-association2 (util/create-and-save-tag-association coll2 associated-tag 2 2)]
    (testing "find latest revisions"
      (are3 [tag-associations params]
            (= (set tag-associations)
               (set (->> (util/find-latest-concepts :tag-association params)
                         :concepts
                         (map #(dissoc % :provider-id :revision-date :transaction-id)))))

            "by associated-concept-id"
            [tag-association1] {:associated-concept-id "C1200000000-REG_PROV"}

            "with metadata"
            [tag-association1 tag-association2] {}

            "exclude metadata"
            [(dissoc tag-association1 :metadata) (dissoc tag-association2 :metadata)] {:exclude-metadata true}))

    (testing "find all revisions"
      (let [num-of-tag-associations (-> (util/find-concepts :tag-association {})
                                        :concepts
                                        count)]
        (is (= 5 num-of-tag-associations))))))

(deftest find-tag-associations-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [tag-association] with parameters [provider-id] is not supported."]}
           (util/find-concepts :tag-association {:provider-id "REG_PROV"})))))

;; Failing in external Oracle
;; CMR-4335 - services
#_(deftest find-services
    (let [serv1 (util/create-and-save-service 1 3)
          serv2 (util/create-and-save-service 2 2)]
      (testing "find latest revsions"
        (are3 [servs params]
              (= (set servs)
                 (set (->> (util/find-latest-concepts :service params)
                           :concepts
                           (map #(dissoc % :provider-id :revision-date :transaction-id)))))
              "with metadata search by concept-id"
              [serv1] {:concept-id (:concept-id serv1)}

              "with metadata search by native-id"
              [serv2] {:native-id (:native-id serv2)}

              "exclude metadata search by concept-id"
              [(dissoc serv1 :metadata)] {:concept-id (:concept-id serv1)
                                          :exclude-metadata true}

              "exclude metadata search by native-id"
              [(dissoc serv2 :metadata)] {:native-id (:native-id serv2)
                                          :exclude-metadata true}

              "no match"
              [] {:native-id "foo"}))

      (testing "find all revisions"
        (let [num-of-servs (-> (util/find-concepts :service {})
                              :concepts
                              count)]
          (is (= 5 num-of-servs))))))

(deftest find-services-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [service] with parameters [short-name] is not supported."]}
           (util/find-concepts :service {:short-name "SN1"})))))

(deftest find-groups
  (let [group1 (util/create-and-save-group "REG_PROV" 1 3)
        group2 (util/create-and-save-group "CMR" 2 2)]
    (testing "find with parameters"
      (testing "latest revisions"
        (are3 [groups params]
              (= (set (map util/expected-concept groups))
                 (set (-> (util/find-latest-concepts :access-group params)
                          :concepts
                          concepts-for-comparison)))
              ;; These are the only valid combinations for groups
              "provider-id, native-id"
              [group1] {:provider-id "REG_PROV" :native-id "native-id 1"}

              "no metadata"
              [(dissoc group1 :metadata)] {:provider-id "REG_PROV"
                                           :exclude-metadata true}))

      (testing "all revisions"
        (are3 [rev-count params]
              (= rev-count
                 (count (-> (util/find-concepts :access-group params)
                            :concepts)))
              "provider-id, native-id - three revisons"
              3 {:provider-id "REG_PROV" :native-id "native-id 1"}
              "everything"
              5 {})))))

(deftest find-variables
  (let [variable1 (util/create-and-save-variable "REG_PROV" 1 3)
        variable2 (util/create-and-save-variable "REG_PROV" 2 2)]
    (testing "find latest revsions"
      (are3 [variables params]
        (= (set variables)
           (set (->> (util/find-latest-concepts :variable params)
                     :concepts
                     (map #(dissoc % :provider-id :revision-date :transaction-id :created-at)))))

        "with metadata"
        [variable1 variable2]
        {:provider-id "REG_PROV"}

        "find none - bad provider-id"
        []
        {:provider-id "PROV_NONE"}

        "exclude metadata"
        [(dissoc variable1 :metadata) (dissoc variable2 :metadata)]
        {:provider-id "REG_PROV" :exclude-metadata true}))
    (testing "find all revisions"
      (let [num-of-variables (-> :variable
                                 (util/find-concepts {:provider-id "REG_PROV"})
                                 :concepts
                                 count)]
        (is (= 5 num-of-variables))))))

(deftest find-variable-associations
  (let [coll1 (save-collection "REG_PROV" 1 {:extra-fields {:entry-id "entry-1"
                                                            :entry-title "et1"
                                                            :version-id "v1"
                                                            :short-name "s1"}})
        coll2 (save-collection "REG_PROV" 2 {:extra-fields {:entry-id "entry-2"
                                                            :entry-title "et2"
                                                            :version-id "v1"
                                                            :short-name "s2"}})
        associated-variable (util/create-and-save-variable "REG_PROV" 1)
        var-association1 (util/create-and-save-variable-association coll1 associated-variable 1 3)
        var-association2 (util/create-and-save-variable-association coll2 associated-variable 2 2)]
    (testing "find latest revisions"
      (are3 [variable-associations params]
        (= (set variable-associations)
           (set (->> (util/find-latest-concepts :variable-association params)
                     :concepts
                     (map #(dissoc % :provider-id :revision-date :transaction-id)))))

        "by associated-concept-id"
        [var-association1]
        {:associated-concept-id "C1200000000-REG_PROV"}

        "with metadata"
        [var-association1 var-association2]
        {}

        "exclude metadata"
        [(dissoc var-association1 :metadata) (dissoc var-association2 :metadata)]
        {:exclude-metadata true}))

    (testing "find all revisions"
      (let [num-of-variable-associations (-> (util/find-concepts :variable-association {})
                                             :concepts
                                             count)]
        (is (= 5 num-of-variable-associations))))))

(deftest find-variable-associations-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [variable-association] with parameters [provider-id] is not supported."]}
           (util/find-concepts :variable-association {:provider-id "REG_PROV"})))))
