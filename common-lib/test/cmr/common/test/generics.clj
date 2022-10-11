(ns cmr.common.test.generics
  (:require
   [cmr.common.generics :as gconfig]
   [clojure.string :as string]
   [clojure.test :refer :all]))

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
    ))

(deftest get-generics-with-documenation-test
  (testing "This should return a list of the names of the documents currently in the system, ensure that grid is in the system"
    (is (= "grid" (some #{"grid"} (gconfig/get-generics-with-documenation (gconfig/all-generic-docs "ingest")))))))

(deftest table-of-contents-html-test
  (testing "Ensure that the strings have been replaced given a generic type in the system with documentation")
  (is (= true (string/includes? (gconfig/table-of-contents-html gconfig/ingest-table-of-contents-template "grid") "Grid")))
  (is (= true (string/includes? (gconfig/table-of-contents-html gconfig/ingest-table-of-contents-template "grid") "grid")))
  (is (= true (string/includes? (gconfig/table-of-contents-html gconfig/ingest-table-of-contents-template "grid") "Grids")))
  (is (= true (string/includes? (gconfig/table-of-contents-html gconfig/ingest-table-of-contents-template "grid") "grids")))
  )

;As more generic documentation items are added we can add more concepts to the tests
(deftest all-generic-table-of-contents-test
  (testing "Ensure that the combined html is returned that will be passed to the api docuement including all the generic's which have documentation are loaded into the system")
  (is (= true (string/includes? (gconfig/all-generic-table-of-contents gconfig/ingest-table-of-contents-template) "grids"))))
