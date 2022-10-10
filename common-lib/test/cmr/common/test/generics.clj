(ns cmr.common.test.generics
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [cmr.common.generics :as gconfig]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.config :as c :refer [defconfig]]
   [cmr.common.test.test-util :refer [with-env-vars]]))

(deftest read-generic-doc-file-test
  (testing "Reads the markdown file for the given generic if the generic is not in the system or it is the wrong version, return empty string"
    (is (= true (string/includes? (gconfig/read-generic-doc-file "ingest" :grid "0.0.1") "Create / Update a Grid")))
    (is (pos? (count (gconfig/read-generic-doc-file "ingest" :grid "0.0.1"))))
    (is (= true (string/includes? (gconfig/read-generic-doc-file "search" :grid "0.0.1") "Searching for grids")))
    (is (pos? (count (gconfig/read-generic-doc-file "search" :grid "0.0.1"))))
    (is (= 0 (count (gconfig/read-generic-doc-file "ingest" :notGenericInSystem "0.0.1"))))
    (is (= 0 (count (gconfig/read-generic-doc-file "ingest" :grid "8.23.1"))))
    (is (= 0 (count (gconfig/read-generic-doc-file "non-real-docs" :grid "0.0.1"))))))

;;This function is concatenating all of the generic documentation into one string to be passed into the api docs
(deftest all-generic-docs-test
  (testing "This string is ensuring that all the generics have their text concatanated together"
    (is (= true (string/includes? (gconfig/all-generic-docs "ingest") "Grid")))
    (is (= true (string/includes? (gconfig/all-generic-docs "ingest") "dataquality")))))
