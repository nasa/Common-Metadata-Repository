(ns cmr.umm-spec.test.umm-g.granule
  "Tests parsing and generating UMM-G granule."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.date-time-parser :as p]
   [cmr.common.test.test-check-ext :refer [defspec]]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm.test.generators.granule :as gran-gen]
   [cmr.umm.umm-granule :as umm-lib-g]))

(def umm-g-coll-refs
  "Generator for UMM-G granule collection ref. It does not support entry-id,
  only entry title, short name & version."
  (gen/one-of [gran-gen/coll-refs-w-entry-title gran-gen/coll-refs-w-short-name-version]))

(def umm-g-granules
  "Generator for UMM-G granule"
  (gen/fmap #(assoc % :collection-ref (gen/generate umm-g-coll-refs)) gran-gen/granules))

(defn- umm->expected-parsed
  "Modifies the UMM record for testing UMM-G. As the fields are added to UMM-G support for
  parsing and generating in cmr.umm-spec.umm-g.granule, the fields should be taken off the
  excluded list below."
  [gran]
  (-> gran
      (dissoc :data-granule)
      (dissoc :access-value)
      (dissoc :temporal)
      (dissoc :spatial-coverage)
      (dissoc :related-urls)
      (dissoc :orbit-calculated-spatial-domains)
      (dissoc :platform-refs)
      (dissoc :project-refs)
      (dissoc :product-specific-attributes)
      (dissoc :cloud-cover)
      (dissoc :two-d-coordinate-system)
      (dissoc :measured-parameters)
      umm-lib-g/map->UmmGranule))

(defspec generate-granule-is-valid-umm-g-test 100
  (for-all [granule umm-g-granules]
    (let [metadata (core/generate-metadata {} granule :umm-json)]
      (empty? (core/validate-metadata :granule :umm-json metadata)))))

(defspec generate-and-parse-umm-g-granule-test 100
  (for-all [granule (gen/no-shrink  umm-g-granules)]
    (let [umm-g-metadata (core/generate-metadata {} granule :umm-json)
          parsed (core/parse-metadata {} :granule :umm-json umm-g-metadata)
          expected-parsed (umm->expected-parsed granule)]
      (= parsed expected-parsed))))

(def sample-umm-g-granule
  (slurp (io/file (io/resource "example-data/umm-json/granule/v1.4/GranuleExample.json"))))

(def expected-granule
  (umm-lib-g/map->UmmGranule
   {:granule-ur "Unique_Granule_UR"
    :data-provider-timestamps (umm-lib-g/map->DataProviderTimestamps
                               {:insert-time (p/parse-datetime "2018-08-19T01:00:00Z")
                                :update-time (p/parse-datetime "2018-09-19T02:00:00Z")
                                :delete-time (p/parse-datetime "2030-08-19T03:00:00Z")})
    :collection-ref (umm-lib-g/map->CollectionRef
                     {:entry-title nil
                      :short-name "CollectionShortName"
                      :version-id "Version"})
    :data-granule nil
    :access-value nil
    :temporal nil
    :spatial-coverage nil
    :related-urls nil}))

(deftest parse-granule-test
  (testing "parse granule"
    (is (= expected-granule (core/parse-metadata {} :granule :umm-json sample-umm-g-granule)))))
