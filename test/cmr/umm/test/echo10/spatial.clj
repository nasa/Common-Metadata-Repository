(ns cmr.umm.test.echo10.spatial
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [cmr.umm.echo10.spatial :as s]))

(defspec double->string-test 1000
  (for-all [d (gen/fmap double gen/ratio)]
    (let [^String double-str (s/double->string d)
          parsed (Double. double-str)]
      ;; Check it should contain an exponent and it doesn't lose precision.
      (and (not (re-find #"[eE]" double-str))
           (= parsed d)))))

