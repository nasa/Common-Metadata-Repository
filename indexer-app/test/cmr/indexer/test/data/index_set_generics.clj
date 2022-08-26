(ns cmr.indexer.test.data.index-set-generics
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.index-set-generics :as gen]))
   ;[cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

(def index-definition-def 
  {:MetadataSpecification 
   {:URL "https://cdn.earthdata.nasa.gov/generics/dataqualitysummary/v1.0.0"
    :Name "DataQuailtySummary",
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

;; TODO: Generic work: move this test to a system-int test.
;  (testing "Test that setting the environment variable takes precedence"
;    (dev-sys-util/eval-in-dev-sys `(gen/set-elastic-generic-index-num-shards! 3))
;    (is (= 3 (get-in (gen/get-settings index-definition-def) [:index :number_of_shards])))
;    ;; Saving the original value doesn't work, because you can't use the variable to set it back. So the default is being used.
;    (dev-sys-util/eval-in-dev-sys `(gen/set-elastic-generic-index-num-shards! gen/default-generic-index-num-shards))))

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