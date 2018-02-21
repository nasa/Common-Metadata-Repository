(ns cmr.metadata-db.int-test.concepts.search.tag-association-search-test
  "Contains integration tests for searching tag associations."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(deftest find-tag-associations
  (let [coll1 (concepts/create-and-save-concept
               :collection "REG_PROV" 1 1 {:extra-fields {:entry-id "entry-1"
                                                          :entry-title "et1"
                                                          :version-id "v1"
                                                          :short-name "s1"}})
        coll2 (concepts/create-and-save-concept
               :collection "REG_PROV" 2 1 {:extra-fields {:entry-id "entry-2"
                                                          :entry-title "et2"
                                                          :version-id "v1"
                                                          :short-name "s2"}})
        associated-tag (concepts/create-and-save-concept :tag "CMR" 1)
        tag-association1 (concepts/create-and-save-concept :tag-association coll1 associated-tag 1 3)
        tag-association2 (concepts/create-and-save-concept :tag-association coll2 associated-tag 2 2)]
    (testing "find latest revisions"
      (are3 [tag-associations params]
        (is (= (set tag-associations)
               (set (->> (util/find-latest-concepts :tag-association params)
                         :concepts
                         (map #(dissoc % :provider-id :revision-date :transaction-id))))))

        "by associated-concept-id"
        [tag-association1] {:associated-concept-id "C1200000000-REG_PROV"}

        "with metadata"
        [tag-association1 tag-association2] {}

        "exclude metadata"
        [(dissoc tag-association1 :metadata) (dissoc tag-association2 :metadata)]
        {:exclude-metadata true}))

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
