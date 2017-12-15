(ns cmr.metadata-db.int-test.concepts.search.search-concept-test
  "Contains integration tests for searching concepts."
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]
   [cmr.metadata-db.services.messages :as msg]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(deftest search-by-concept-revision-id-tuples
  (doseq [provider-id ["REG_PROV" "SMAL_PROV1"]]
    (let [coll1 (concepts/create-and-save-concept :collection provider-id 1 3)
          coll2 (concepts/create-and-save-concept :collection provider-id 2 3)
          coll3 (concepts/create-and-save-concept :collection provider-id 3 3)
          gran1 (concepts/create-and-save-concept :granule provider-id coll1 1 2)
          gran2 (concepts/create-and-save-concept :granule provider-id coll2 2 2)
          group1 (concepts/create-and-save-concept :access-group provider-id 4 3)]
      (are [item-revision-tuples]
        (let [tuples (map #(update-in % [0] :concept-id) item-revision-tuples)
              {:keys [status concepts]} (util/get-concepts tuples)
              expected-concepts (map (fn [[item revision]]
                                       (assoc (util/expected-concept item)
                                              :revision-id revision))
                                     item-revision-tuples)]
          (and (= 200 status)
               (= expected-concepts (util/concepts-for-comparison concepts))))
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
    (let [coll1 (concepts/create-and-save-concept :collection provider-id 1)
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
    (let [coll1 (concepts/create-and-save-concept :collection provider-id 1)
          tuples [[(:concept-id coll1) 1]
                  [(:concept-id coll1) 2]
                  ["C2-REG_PROV" 1]]
          {:keys [status concepts]} (util/get-concepts tuples true)]
      (is (= 200 status))
      (is (= [(:concept-id coll1)] (map :concept-id concepts))))))

(deftest get-latest-by-concept-id
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1 3)
        coll2 (concepts/create-and-save-concept :collection "REG_PROV" 2 1)
        coll3 (concepts/create-and-save-concept :collection "SMAL_PROV1" 3 3)
        gran1 (concepts/create-and-save-concept :granule "REG_PROV" coll1 1 2)
        gran2 (concepts/create-and-save-concept :granule "REG_PROV" coll2 2 1)
        group1 (concepts/create-and-save-concept :access-group "REG_PROV" 4 1)]
    (are [item-revision-tuples]
      (let [ids (map #(:concept-id (first %)) item-revision-tuples)
            {:keys [status concepts]} (util/get-latest-concepts ids)
            expected-concepts (map (fn [[item revision]]
                                     (assoc (util/expected-concept item)
                                            :revision-id revision))
                                   item-revision-tuples)]
        (and (is (= 200 status))
             (is (= expected-concepts (util/concepts-for-comparison concepts)))))
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
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1)
        ids [(:concept-id coll1) "C1234-REG_PROV"]
        {:keys [status errors]} (util/get-latest-concepts ids)]
    (is (= 404 status))
    (is (= #{(msg/concept-does-not-exist "C1234-REG_PROV")}
           (set errors)))))

(deftest get-latest-concepts-with-missing-concept-allow-missing-test
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1)
        ids [(:concept-id coll1) "C1234-REG_PROV"]
        {:keys [status concepts]} (util/get-latest-concepts ids true)]
    (is (= 200 status))
    (is (= [(:concept-id coll1)] (map :concept-id concepts)))))
