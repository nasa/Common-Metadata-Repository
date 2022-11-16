(ns cmr.common.test.generics-documentation
  (:require
   [cmr.common.generics-documentation :as gdocs]
   [clojure.string :as string]
   [clojure.test :refer :all]))

;; These two functions are stubs to be passed to ensure
;; funtions use correct spacing
(def options-map-ingest {:spacer #(- (* % 4) 4)})
(def options-map-search {:spacer #(- (* % 4) 12)})

(deftest read-generic-doc-file-test
  (testing "Reads the markdown file for the given generic if the generic is not in the system or it is the wrong version, return empty string"
    (is (= true (string/includes? (gdocs/read-generic-doc-file "ingest" :grid "0.0.1") "Create / Update a Grid")))
    (is (pos? (count (gdocs/read-generic-doc-file "ingest" :grid "0.0.1"))))
    (is (= true (string/includes? (gdocs/read-generic-doc-file "search" :grid "0.0.1") "Searching for Grids")))
    (is (pos? (count (gdocs/read-generic-doc-file "search" :grid "0.0.1"))))
    (is (= 0 (count (gdocs/read-generic-doc-file "ingest" :notGenericInSystem "0.0.1"))))
    ;;This is testing if a version for a generic which does not exist is requested
    (is (= 0 (count (gdocs/read-generic-doc-file "ingest" :grid "8.23.1"))))
    (is (= 0 (count (gdocs/read-generic-doc-file "non-real-docs" :grid "0.0.1"))))
    (is (= "" (gdocs/read-generic-doc-file "non-real-docs" :grid "0.0.1")))))

;;This function is concatenating all of the generic documentation into one string to be passed into the api docs
(deftest all-generic-docs-test
  (testing "This string is ensuring that all the generics have their text concatanated together"
    (is (= true (string/includes? (gdocs/all-generic-docs "ingest") "Grid")))))

(def header-extract-full "### <a name=\"link\"></a> content\n Other items should not be indcluded")

(deftest get-toc-headers-from-markdown
  (testing "Ensure we can extact header information from the markdown to build a toc")
  (is (= (gdocs/get-toc-headers-from-markdown header-extract-full) (list "### <a name=\"link\"></a> content"))))

(def header-extract-formatted-search "#### <a name=\"link\"></a> content")
(def header-extract-formatted-ingest "### <a name=\"link\"></a> content")

(deftest get-toc-data
  (testing "Ensuring that we can extract the necessary components from a toc header")
  (is (= (gdocs/get-toc-data header-extract-formatted-search "search" (get options-map-search :spacer (fn [x] x))) "    * [content](#link)\n"))
  (is (= (gdocs/get-toc-data header-extract-formatted-ingest "ingest" (get options-map-ingest :spacer (fn [x] x))) "        * [content](#link)\n")))

(deftest build-markdown-toc-test
  (testing "Ensuring that the markdown can be created from all of the headers for a given generic
    retrived from the ingest or search .md")
  (is (= (gdocs/build-markdown-toc 4 "GridInfo" "grid-link") "    * [grid-link](#GridInfo)\n")))

(def formatted-search-grid-markdown "* [Grid](#grid)\n    * [Searching for Grids](#searching-for-grids)\n        * [Grid Search Parameters](#grid-search-params)\n        * [Grid Search Response](#grid-search-response)\n    * [Retrieving All Revisions of a Grid](#retrieving-all-revisions-of-a-grid)\n    * [Sorting Grid Results](#sorting-grid-results)\n")
(def formatted-ingest-grid-markdown "    * [Grid](#grid)\n            * [/providers/&lt;provider-id&gt;/grids/&lt;native-id&gt;](#provider-info-grid)\n        * [PUT - Create / Update a Grid](#create-update-grid)\n        * [DELETE - Delete a Grid](#delete-grid)\n")
(def default-formatted-ingest-default "  * [Grid](#grid)\n    * [/providers/&lt;provider-id&gt;/grids/&lt;native-id&gt;](#provider-info-grid)\n   * [PUT - Create / Update a Grid](#create-update-grid)\n   * [DELETE - Delete a Grid](#delete-grid)\n")

(deftest format-generic-toc-test
  (testing "Ensuring that the generic-toc returns a formatted markdown")
  (is (= formatted-search-grid-markdown (gdocs/format-generic-toc "search" "grid" "0.0.1" options-map-search)))
  (is (= formatted-ingest-grid-markdown (gdocs/format-generic-toc "ingest" "grid" "0.0.1" options-map-ingest)))
  ;;Coverge if the function is passed with a map with the spacer function
  (is (= default-formatted-ingest-default (gdocs/format-generic-toc "ingest" "grid" "0.0.1" {})))
  (is (= "" (gdocs/format-generic-toc "none" "grid" "0.0.1" options-map-ingest))))

(deftest all-generic-docs-toc-test
  (testing "Ensuring that the generic-toc returns a formatted markdown")
  (is (= true (string/includes? (gdocs/all-generic-docs-toc "search" options-map-search) "[Grid](#grid)"))))

(def buried-html-item "<ul><li>Grid html</li></ul>")
(def buried-html-item-newline "<ul> \n <li>Grid html</li></ul>")

(deftest format-toc-into-doc-test
  (testing "Due to preexisting formatting in the api.md documents we need to
            remove the outer list item and unordered list item tags to ensure
            that the generics, table of contents does NOT break formatting on
            the documentation")
  (is (= "Grid html" (gdocs/format-toc-into-doc buried-html-item)))
  (is (= "Grid html" (gdocs/format-toc-into-doc buried-html-item-newline))))

(deftest generic-document-versions->markdown-test
  (testing "Produce a markdown list containing at least the Generic Grid"
    (is (string/includes? (gdocs/generic-document-versions->markdown)
                          "\n* grid: 0.0.1"))
    (is (string/includes? (gdocs/generic-document-versions->markdown)
                          "\n* data-quality-summary: 1.0.0"))))
