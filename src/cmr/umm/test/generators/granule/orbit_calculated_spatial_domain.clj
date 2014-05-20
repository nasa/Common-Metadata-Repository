(ns cmr.umm.test.generators.granule.orbit-calculated-spatial-domain
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [clj-time.core :as t]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.granule :as g]))

(def longitude
  (ext-gen/choose-double -180 180))

(def orbital-model-name
  (ext-gen/string-ascii 1 80))

(def orbit-number
  gen/int)

(def s-orbit-number
  "start/stop orbit number"
  (gen/fmap double gen/ratio))

(def orbit-calculated-spatial-domains
  (gen/fmap (fn [[omn on son spon ecl ecdt]]
              (g/->OrbitCalculatedSpatialDomain omn on son spon ecl ecdt))
            (gen/tuple orbital-model-name
                       orbit-number
                       s-orbit-number
                       s-orbit-number
                       longitude
                       ext-gen/date-time)))


