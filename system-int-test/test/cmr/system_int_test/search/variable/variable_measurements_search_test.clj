(ns cmr.system-int-test.search.variable.variable-measurements-search-test
  "Integration test for CMR variable search by measurement identifiers"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are3]]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-variable :as umm-spec-v]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.system-int-test.utils.variable-util :as variables]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1"})
                variables/grant-all-variable-fixture]))

(defn- ingest-variable-with-measurements
  "Ingest the variables with the given unique id and measurement identifiers"
  [id measurements]
  (let [coll1-PROV1-1 (d/ingest-umm-spec-collection
                        "PROV1"
                        (data-umm-c/collection {:EntryTitle "E1" :ShortName "S1"}))
        _ (index/wait-until-indexed)
        variable (variables/make-variable-concept
                   {:Name (str "Variable" id)
                    :LongName (str "Long" id)
                    :MeasurementIdentifiers measurements}
                   {:native-id (str "Var" id)
                    :coll-concept-id (:concept-id coll1-PROV1-1)})]
    (variables/ingest-variable-with-association variable)))

(deftest search-variable-measurement-identifiers-test
  (let [m1 (umm-spec-v/measurement-identifier {:context-medium "Atmosphere"
                                               :object "air"
                                               :quantities ["pressure_anomoly"]})
        m2 (umm-spec-v/measurement-identifier {:context-medium "Atmosphere"
                                               :object "cloud"})
        m21 (umm-spec-v/measurement-identifier {:context-medium "soil"
                                                :object "air"})
        m3 (umm-spec-v/measurement-identifier {:context-medium "ocean"
                                               :object "sea_ice"})
        m4 (umm-spec-v/measurement-identifier {:context-medium "ocean"
                                               :object "sea_ice"
                                               :quantities ["albedo"]})
        m5 (umm-spec-v/measurement-identifier {:context-medium "ocean"
                                               :object "sea_ice"
                                               :quantities ["area"]})
        m6 (umm-spec-v/measurement-identifier {:context-medium "planetary_surface"
                                               :object "canopy"
                                               :quantities ["albedo" "area_fraction"]})
        m7 (umm-spec-v/measurement-identifier {:context-medium "planetary_surface"
                                               :object "soil"
                                               :quantities ["albedo"]})
        var1 (ingest-variable-with-measurements 1 [m1])
        ;; var2 has context-medium "Atmosphere" and object "air" accross measurement identifiers
        var2 (ingest-variable-with-measurements 2 [m2 m21])
        var3 (ingest-variable-with-measurements 3 [m3])
        var4 (ingest-variable-with-measurements 4 [m4])
        var5 (ingest-variable-with-measurements 5 [m5])
        var6 (ingest-variable-with-measurements 6 [m6])
        var7 (ingest-variable-with-measurements 7 [m7])
        var-no-measurements (ingest-variable-with-measurements 100 nil)]
    (index/wait-until-indexed)

    (testing "search variables by measurement identifier."
      (are [measurement-field value items]
        (d/refs-match? items
                       (search/find-refs
                        :variable
                        {:measurement-identifiers {:0 {measurement-field value}}}))
        :contextmedium "Atmosphere" [var1 var2]
        :contextmedium "soil" [var2]
        :object "air" [var1 var2]
        :object "soil" [var7]
        :quantity "albedo" [var4 var6 var7]
        :quantity "area" [var5]

        ;; search is case insensitive by default
        :contextmedium "atmosphere" [var1 var2]
        :object "SOIL" [var7]
        :quantity "AreA" [var5]))

    (testing "search by measurement identifier, combined."
      (are3 [field-params items]
        (is (d/refs-match? items
                           (search/find-refs
                            :variable
                            {:measurement-identifiers {:0 field-params}})))

        "match on all fields"
        {:contextmedium "Atmosphere"
         :object "air"
         :quantity "pressure_anomoly"}
        [var1]

        "match on contextmedium and object"
        {:contextmedium "Atmosphere"
         :object "air"}
        [var1]

        "match on contextmedium and quantity"
        {:contextmedium "Atmosphere"
         :quantity "pressure_anomoly"}
        [var1]

        "match on object and quantity"
        {:object "air"
         :quantity "pressure_anomoly"}
        [var1]))

    (testing "search by measurement identifiers, ignore case"
      (are [items measurement-field value ignore-case]
        (d/refs-match? items
                       (search/find-refs
                        :variable
                        {:measurement-identifiers {:0 {measurement-field value}}
                          "options[measurement-identifiers][ignore-case]" ignore-case}))

        [var2] :contextmedium "soil" false
        [] :contextmedium "SOIL" false
        [var2] :contextmedium "SOIL" true))

    (testing "search by measurement identifiers, multiple, default is ANDed."
      (is (d/refs-match? [var2]
                         (search/find-refs
                          :variable
                          {:measurement-identifiers {:0 {:contextmedium "Atmosphere"}
                                                     :1 {:contextmedium "soil"
                                                         :object "air"}}}))))

    (testing "search by measurement identifiers, multiple with :or options"
      (is (d/refs-match? [var2]
                         (search/find-refs
                          :variable
                          {:measurement-identifiers {:0 {:contextmedium "Atmosphere"}
                                                     :1 {:contextmedium "soil"
                                                         :object "air"}}
                            "options[measurement-identifiers][or]" "false"})))
      (is (d/refs-match? [var1 var2]
                         (search/find-refs
                          :variable
                          {:measurement-identifiers {:0 {:contextmedium "Atmosphere"}
                                                     :1 {:contextmedium "soil"
                                                         :object "air"}}
                            "options[measurement-identifiers][or]" "true"}))))))

(deftest search-measurements-error-scenarios
  (testing "search by invalid format."
    (let [{:keys [status errors]} (search/find-refs
                                   :variable
                                   {:measurement-identifiers "atmosphere"})]
      (is (= 400 status))
      (is (re-find #"Parameter \[measurement_identifiers] must include a nested key."
                   (first errors))))

    (let [{:keys [status errors]} (search/find-refs
                                   :variable
                                   {:measurement-identifiers {:0 {:term "value"}}})]
      (is (= 400 status))
      (is (re-find #"Parameter \[term\] is not a valid \[measurement_identifiers\] search term."
                   (first errors))))))
