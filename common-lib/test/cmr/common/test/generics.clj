(ns cmr.common.test.generics
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.generics :as gconfig]
   [cmr.common.util :as cutil]))


(deftest fast-generic-test
  "Make sure that highly used generic functions are memoized and are faster (or
   at least not worse) with a second call"
  (testing "don't take to long - "

    (cmr.common.util/are3
     [body]
     (let [one (do (cutil/time-execution body))
           two (do (cutil/time-execution body))]
       (is (<= (first two) (first one))))

     "approved"
     (gconfig/approved-generic? :grid "0.0.1")

     "latest docs"
     (gconfig/latest-approved-documents)

     "read schema"
     (gconfig/read-schema-file "metadata" :grid, "0.0.1")

     "approved prefixes"
     (gconfig/approved-generic-concept-prefixes))))
