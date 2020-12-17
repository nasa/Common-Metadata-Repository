(ns cmr.search.test.services.humanizers.humanizer-range-facet-service
  "Testing functions used for verifying the humanizer report"
  (:require [clojure.test :refer :all]
            [cmr.common-app.humanizer :as h]
            [cmr.common-app.test.sample-humanizer :as sh]
            [cmr.common.util :refer [are3]]
            [cmr.search.services.humanizers.humanizer-range-facet-service :as hrfs]))

(deftest create-facet-range-test
  "Testing converting the humanizers to search facets"

  (are3 [expected humanizer]
    (is (= expected
           (hrfs/create-facet-range humanizer)))

    "Test only with no upper value"
    {:key "1 meter & above", :from 1.0 :to (Float/MAX_VALUE)}
    {:source_value "1 meter & above",
     :replacement_value "1 meter & above",
     :field "horizontal_range_facets",
     :type "horizontal_range_facets",
     :reportable false,
     :order 0}

    "Test with both lower and upper value with only 1 unit"
    {:key "0 to 1 meter", :from 0.0 :to (+ 1.0 hrfs/addition-factor)}
    {:source_value "0 to 1 meter",
     :replacement_value "0 to 1 meter",
     :field "horizontal_range_facets",
     :type "horizontal_range_facets",
     :reportable false,
     :order 0}

    "Test with both lower and upper value with 2 units"
    {:key "0 meters to 1 meter", :from 0.0 :to (+ 1.0 hrfs/addition-factor)}
    {:source_value "0 meters to 1 meter",
     :replacement_value "0 meters to 1 meter",
     :field "horizontal_range_facets",
     :type "horizontal_range_facets",
     :reportable false,
     :order 0}

    "Test with lower and upper value conversions with 2 units"
    {:key "0 degr to 1 km", :from 0.0 :to (+ 1000.0 hrfs/addition-factor)}
    {:source_value "0 degr to 1 km",
     :replacement_value "0 degr to 1 km",
     :field "horizontal_range_facets",
     :type "horizontal_range_facets",
     :reportable false,
     :order 0}))

(defn- get-sample-range-facet-humanizers
  "Get the sample humanizers that work for range facets."
  []
  (for [humanizer sh/sample-humanizers
        :when (= "horizontal_range_facets" (:type humanizer))]
    humanizer))

(deftest get-range-facets-test
  "Testing converting the humanizers to search facets using the cache."
  (let [context {:system {:caches {hrfs/range-facet-cache-key (hrfs/create-range-facet-cache)}}}]
    (hrfs/store-range-facets context
      (hrfs/create-range-facets-from-humanizers context (get-sample-range-facet-humanizers)))
    (is (= [{:key "0 to 1 meter", :from 0.0, :to (+ 1.0 hrfs/addition-factor)}
            {:key "1 to 30 meters", :from 1.0, :to (+ 30.0 hrfs/addition-factor)}
            {:key "30 to 100 meters", :from 30.0, :to (+ 100.0 hrfs/addition-factor)}
            {:key "100 to 250 meters", :from 100.0, :to (+ 250.0 hrfs/addition-factor)}
            {:key "250 to 500 meters", :from 250.0, :to (+ 500.0 hrfs/addition-factor)}
            {:key "500 to 1000 meters", :from 500.0, :to (+ 1000.0 hrfs/addition-factor)}
            {:key "1 to 10 km", :from 1000.0, :to (+ 10000.0 hrfs/addition-factor)}
            {:key "10 to 50 km", :from 10000.0, :to (+ 50000.0 hrfs/addition-factor)}
            {:key "50 to 100 km", :from 50000.0, :to (+ 100000.0 hrfs/addition-factor)}
            {:key "100 to 250 km", :from 100000.0, :to (+ 250000.0 hrfs/addition-factor)}
            {:key "250 to 500 km", :from 250000.0, :to (+ 500000.0 hrfs/addition-factor)}
            {:key "500 to 1000 km", :from 500000.0, :to (+ 1000000.0 hrfs/addition-factor)}
            {:key "1000 km & above", :from 1000000.0 :to (Float/MAX_VALUE)}]
           (hrfs/get-range-facets context)))))
