(ns cmr.metadata-db.int-test.concepts.search.variable-search-test
  "Contains integration tests for searching variables."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(deftest find-variables
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1 1)
        coll1-concept-id (:concept-id coll1)
        coll2 (concepts/create-and-save-concept :collection "SMAL_PROV1" 1 1)
        coll2-concept-id (:concept-id coll2)
        ;; create two variables with the same native-id on different providers with multiple revisions
        variable1 (concepts/create-and-save-concept :variable "REG_PROV" 1 3 {:coll-concept-id coll1-concept-id})
        variable2 (concepts/create-and-save-concept :variable "SMAL_PROV1" 1 2 {:coll-concept-id coll2-concept-id})]
    (testing "find latest revisions"
      (are3 [variables params]
        (is (= (set (map #(dissoc % :coll-concept-id :variable-association) variables))
               (set (->> (util/find-latest-concepts :variable params)
                         :concepts
                         util/concepts-for-comparison
                         (map #(dissoc % :coll-concept-id))))))

        "with metadata search by provider-id"
        [variable1] {:provider-id "REG_PROV"}

        "with metadata search by concept-id"
        [variable1] {:concept-id (:concept-id variable1)}

        "with metadata search by native-id"
        [variable1 variable2] {:native-id (:native-id variable1)}

        "with metadata search by provider-id and native-id"
        [variable2] {:provider-id "SMAL_PROV1"
                     :native-id (:native-id variable1)}

        "exclude metadata search by concept-id"
        [(dissoc variable1 :metadata)] {:concept-id (:concept-id variable1)
                                        :exclude-metadata true}

        "exclude metadata search by provider-id and native-id"
        [(dissoc variable2 :metadata)] {:provider-id "SMAL_PROV1"
                                        :native-id (:native-id variable1)
                                        :exclude-metadata true}

        "no match by native id"
        [] {:native-id "foo"}

        "no match by provider-id"
        []
        {:provider-id "PROV_NONE"}))

    (testing "find all revisions"
      (let [num-of-variables (-> :variable
                                 (util/find-concepts {:native-id (:native-id variable1)})
                                 :concepts
                                 count)]
        (is (= 5 num-of-variables))))))
