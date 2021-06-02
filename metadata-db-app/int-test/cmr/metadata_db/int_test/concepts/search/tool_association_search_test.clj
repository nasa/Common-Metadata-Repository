(ns cmr.metadata-db.int-test.concepts.search.tool-association-search-test
  "Contains integration tests for searching tool associations."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(deftest find-tool-associations
  (let [coll1 (concepts/create-and-save-concept
               :collection "REG_PROV" 1 1 {:extra-fields {:entry-id "entry-1"
                                                          :entry-title "et1"
                                                          :version-id "v1"
                                                          :short-name "tl1"}})
        coll2 (concepts/create-and-save-concept
               :collection "REG_PROV" 2 1 {:extra-fields {:entry-id "entry-2"
                                                          :entry-title "et2"
                                                          :version-id "v1"
                                                          :short-name "tl2"}})
        associated-tool (concepts/create-and-save-concept :tool "REG_PROV" 1)
        tool-association1 (concepts/create-and-save-concept :tool-association coll1 associated-tool 1 3)
        tool-association2 (concepts/create-and-save-concept :tool-association coll2 associated-tool 2 2)]

    (testing "find latest revisions"
      (are3 [tool-associations params]
        (is (= (set tool-associations)
               (set (->> (util/find-latest-concepts :tool-association params)
                         :concepts
                         (map #(dissoc % :provider-id :revision-date :transaction-id))))))

        "by associated-concept-id"
        [tool-association1]
        {:associated-concept-id (:concept-id coll1)}

        "by associated-concept-id, multiple"
        [tool-association1 tool-association2]
        {:associated-concept-id [(:concept-id coll1) (:concept-id coll2)]}

        "by tool-concept-id"
        [tool-association1 tool-association2]
        {:tool-concept-id (:concept-id associated-tool)}

        "by native-id"
        [tool-association1]
        {:native-id (:native-id tool-association1)}

        "by tool-concept-id not found"
        []
        {:tool-concept-id "TL100-REG_PROV"}

        "search for all, with metadata"
        [tool-association1 tool-association2]
        {}

        "exclude metadata"
        [(dissoc tool-association1 :metadata) (dissoc tool-association2 :metadata)]
        {:exclude-metadata true}))

    (testing "find all revisions"
      (let [num-of-tool-associations (-> (util/find-concepts :tool-association {})
                                            :concepts
                                            count)]
        (is (= 5 num-of-tool-associations))))))

(deftest find-tool-associations-with-invalid-parameters
  (testing "extra parameters"
    (is (= {:status 400
            :errors ["Finding concept type [tool-association] with parameters [provider-id] is not supported."]}
           (util/find-concepts :tool-association {:provider-id "REG_PROV"})))))
