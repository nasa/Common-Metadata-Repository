(ns cmr.metadata-db.int-test.concepts.search.tag-search-test
  "Contains integration tests for searching tags."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(deftest find-tags
  (let [tag1 (concepts/create-and-save-concept :tag "CMR" 1 3)
        tag2 (concepts/create-and-save-concept :tag "CMR" 2 2)]
    (testing "find latest revsions"
      (are3 [tags params]
        (is (= (set tags)
               (set (->> (util/find-latest-concepts :tag params)
                         :concepts
                         (map #(dissoc % :provider-id :revision-date :transaction-id))))))
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
