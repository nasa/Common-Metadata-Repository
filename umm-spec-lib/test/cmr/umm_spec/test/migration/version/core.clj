(ns cmr.umm-spec.test.migration.version.variable
  (:require
   [clojure.test :refer :all]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.versioning :as v]))


(deftest test-version-steps
  (with-bindings {#'v/versions {:collection ["1.14" "1.15" "1.15.1" "1.15.2"
                                             "1.15.3" "1.15.4" "1.15.5" "1.16"]}}
    (is (= [] (#'vm/version-steps :variable "1.14" "1.14")))
    (is (= [["1.14" "1.15"] ["1.15" "1.15.1"] ["1.15.1" "1.15.2"]]
           (#'vm/version-steps :variable "1.14" "1.15.2")))
    (is (= [["1.16" "1.15.5"]] (#'vm/version-steps :variable "1.16" "1.15.5")))
    (is (= [["1.16.1" "1.16"]["1.16" "1.15.5"] ["1.15.5" "1.15.4"] ["1.15.4" "1.15.3"] ["1.15.3" "1.15.2"]
            ["1.15.2" "1.15.1"] ["1.15.1" "1.15"] ["1.15" "1.14"]]
           (#'vm/version-steps :variable "1.16.1" "1.14")))))
