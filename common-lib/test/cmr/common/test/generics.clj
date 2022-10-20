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
    (is (= true (string/includes? (gconfig/all-generic-docs "ingest") "Grid")))))

(deftest table-of-contents-html-test
  (testing "Ensure that the strings have been replaced given a generic type in the system with documentation")
  (def gen-string "/%generic%/%uc-generic%/%plural-generic%/%uc-plural-generic%")
  (is (= true (string/includes? (gconfig/fill-in-generic-name gen-string "grid") "grid")))
  (is (= true (string/includes? (gconfig/fill-in-generic-name gen-string "grid") "grids")))
  (is (= true (string/includes? (gconfig/fill-in-generic-name gen-string "grid") "Grids")))
  (is (= true (string/includes? (gconfig/fill-in-generic-name gen-string "grid") "Grid"))))


(def header-extract-full "### <a name=\"link\"></a> content\n Other items should not be indcluded")
(deftest get-toc-headers-from-markdown
  (testing "Ensure we can extact header information from the markdown to build a toc")
  (is (= (gconfig/get-toc-headers-from-markdown header-extract-full) (list "### <a name=\"link\"></a> content"))))

(def header-extract-formatted-search "#### <a name=\"link\"></a> content")
(def header-extract-formatted-ingest "### <a name=\"link\"></a> content")

(deftest get-toc-data
  (testing "Ensuring that we can extract the necessary components from a toc header")
  (is (= (gconfig/get-toc-data header-extract-formatted-search "search") "    * [content](#link)\n"))
  (is (= (gconfig/get-toc-data header-extract-formatted-ingest "ingest") "        * [content](#link)\n")))

(deftest build-markdown-toc-test
  (testing "Ensuring that the markdown can be created from all of the headers for a given generic
    retrived from the ingest or search .md")
  (is (= (gconfig/build-markdown-toc 4 "GridInfo" "grid-link") "    * [grid-link](#GridInfo)\n")))

(def formatted-search-grid-markdown "* [Grid](#grid)\n    * [Searching for grids](#searching-for-grids)\n        * [Grid Search Parameters](#grid-search-params)\n        * [Grid Search Response](#grid-search-response)\n    * [Retrieving All Revisions of a Grid](#retrieving-all-revisions-of-a-grid)\n    * [Sorting Grid Results](#sorting-grid-results)\n")
(def formatted-ingest-grid-markdown "* Grids\n    * /providers/\\<provider-id>/grids/\\<native-id>\n        * [PUT - Create / Update a Grid](#create-update-grid)\n        * [DELETE - Delete a Grid](#delete-grid)\n")

(deftest format-generic-toc-test
  (testing "Ensuring that the generic-toc returns a formatted markdown")
  (is (= (gconfig/format-generic-toc "search" "grid" "0.0.1") formatted-search-grid-markdown))
  (is (= (gconfig/format-generic-toc "ingest" "grid" "0.0.1") formatted-ingest-grid-markdown)))

(deftest all-generic-docs-toc-test
  (testing "Ensuring that the generic-toc returns a formatted markdown")
  (is (= true (string/includes? (gconfig/all-generic-docs-toc "search") "[Grid](#grid)"))))

(def buried-html-item "<ul><li>Grid html</li></ul>")
(deftest format-toc-into-doc-test
  (testing "Due to preexisting formatting in the api.md documents we need to
  remove the outer list item and unordered list item tags to ensure that the generics,
            table of contents does NOT break formatting on the documentation")
(is (= "Grid html" (gconfig/format-toc-into-doc buried-html-item))))
