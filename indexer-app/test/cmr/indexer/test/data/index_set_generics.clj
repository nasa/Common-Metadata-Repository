(ns cmr.indexer.test.data.index-set-generics
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.index-set-generics :as gen]))

(def index-definition-def
  {:MetadataSpecification
   {:URL "https://cdn.earthdata.nasa.gov/generics/data-quality-summary/v1.0.0"
    :Name "Data Quality Summary",
    :Version "1.0.0"}
   :SubConceptType "DQS",
   :IndexSetup {:index {:number_of_shards 5, :number_of_replicas 1, :refresh_interval "1s"}},
   :Indexes [{:Description "Name", :Field ".Name", :Name "Name", :Mapping "string"}
             {:Description "Id - This is the old ECHO 10 GUID.", :Field ".Id", :Name "Id", :Mapping "string"}]})

(deftest get-settings-test
  ;; Test getting the settings from either the config file, the environment variable, or the default value.

  (testing "Test that concept prefixes can be looked up from either a configuration file or be assumed"
    (is (= {:index
            {:number_of_shards 5, :number_of_replicas 1, :refresh_interval "1s"}}
           (gen/get-settings index-definition-def))))

  (testing "Test that a different shard number in the configuration file takes precedence."
    (let [index-def-2 (assoc-in index-definition-def [:IndexSetup :index :number_of_shards] 4)
          actual (gen/get-settings index-def-2)]
      (is (= (get-in index-def-2 [:IndexSetup :index :number_of_shards])
             (get-in actual [:index :number_of_shards])))))

  (testing "Test when a shard number doesn't exist. It should use the default."
    (let [index-def-2 (assoc-in index-definition-def [:IndexSetup :index :number_of_shards] nil)
          actual (gen/get-settings index-def-2)]
      (is (= gen/default-generic-index-num-shards
             (get-in actual [:index :number_of_shards])))))

  (testing "Test when IndexSetup doesn't exist. It should use the default."
    (let [index-def-2 (dissoc index-definition-def :IndexSetup)
          actual (gen/get-settings index-def-2)]
      (is (= {:index
              {:number_of_shards 5, :number_of_replicas 1, :refresh_interval "1s"}}
             (gen/get-settings index-definition-def))))))

(deftest read-schema-definition-test
  ;; Testing the read schema functionality.

  (testing "Testing when a file exists."
    (let [gen-name "grid"
          gen-version "0.0.1"]
      (is (some? (gen/read-schema-definition gen-name gen-version)))))

  (testing "Testing a bad file."
    (let [gen-name "grid2"
          gen-version "0.0.1"]
      (is (nil? (gen/read-schema-definition gen-name gen-version))))))

(deftest generic-mappings-generator-test
  (testing "check that grid is present"
    (let [result (:generic-grid (gen/generic-mappings-generator))]
      (is (some? result) "generator did not return grid")
      (is (= "generic-grid" (get-in result [:indexes 0 :name])) "bad index name")
      (is (= "keyword" (get-in result [:mapping :properties :provider-id-lowercase :type]))
          "could not find a common field type"))))
