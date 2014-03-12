(ns cmr.umm.test.echo10.collection
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            [cmr.umm.test.generators :as umm-gen]

            ;; my code
            [cmr.umm.echo10.collection :as c]
            ))

(defspec generate-collection-is-valid-xml-test 10
  (for-all [collection umm-gen/collections]
    (let [xml (c/generate-collection collection)]
      (and
        (> (count xml) 0)
        (= 0 (count (c/validate-xml xml)))))))

(comment

(c/validate-xml (c/generate-collection (first (gen/sample-seq umm-gen/collections))))


  )
