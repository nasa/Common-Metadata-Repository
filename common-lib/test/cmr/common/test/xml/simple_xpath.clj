(ns cmr.common.test.xml.simple-xpath
  (:require [clojure.test :refer :all]
            [cmr.common.xml.simple-xpath :as sx]
            [clojure.data.xml :as x]
            [cmr.common.util :as u :refer [are2]]))

;; From Microsoft sample XML online
(def sample-xml
  (sx/create-xpath-context-for-xml
    "<catalog xmlns:foo=\"http://example.com/foo\">
      <book id=\"bk101\">
        <author>Gambardella, Matthew</author>
        <title>XML Developer's Guide</title>
        <genre>Computer</genre>
        <price>44.95</price>
        <dates>
          <publish_date>2000-10-01</publish_date>
        </dates>
      </book>
      <book id=\"bk102\" foo:bar=\"bat\">
        <author>Ralls, Kim</author>
        <title>Midnight Rain</title>
        <genre>Fantasy</genre>
        <price>5.95</price>
        <dates>
          <publish_date importance=\"high\">2000-12-16</publish_date>
        </dates>
      </book>
      <book id=\"bk103\">
        <author>Corets, Eva</author>
        <author>Lucy, Steven</author>
        <title>Maeve Ascendant</title>
        <genre>Fantasy</genre>
        <price>5.95</price>
        <dates>
          <publish_date importance=\"low\">2000-11-17</publish_date>
        </dates>
      </book>
      <book id=\"bk104\">
        <author>Corets, Eva</author>
        <title>Oberon's Legacy</title>
        <genre>Fantasy</genre>
        <price>5.95</price>
        <dates>
          <publish_date importance=\"high\">2001-03-10</publish_date>
        </dates>
      </book>
      <book id=\"bk105\">
        <author>Corets, Eva</author>
        <title>The Sundered Grail</title>
        <genre>Fantasy</genre>
        <price>5.95</price>
        <dates>
          <publish_date importance=\"medium\">2001-09-10</publish_date>
        </dates>
      </book>
    </catalog>"))

(def sample-data-structure
  (sx/create-xpath-context-for-data
    {:books
     [{:id "bk101"
       :author ["Gambardella, Matthew"]
       :title "XML Developer's Guide"
       :genre "Computer"
       :price 44.95
       :publish_date "2000-10-01"}
      {:id "bk102"
       :author ["Ralls, Kim"]
       :title "Midnight Rain"
       :genre "Fantasy"
       :price 5.95
       :publish_date "2000-12-16"}
      {:id "bk103"
       :author ["Corets, Eva" "Lucy, Steven"]
       :title "Maeve Ascendant"
       :genre "Fantasy"
       :price 5.95
       :publish_date "2000-11-17"}
      {:id "bk104"
       :author ["Corets, Eva"]
       :title "Oberon's Legacy"
       :genre "Fantasy"
       :price 5.95
       :publish_date "2001-03-10"}
      {:id "bk105"
       :author ["Corets, Eva"]
       :title "The Sundered Grail"
       :genre "Fantasy"
       :price 5.95
       :publish_date "2001-09-10"}]}))

(deftest xpaths-with-xml-test
  (testing "xpaths from root"
    (are [xpath value]
         (testing xpath
           (is (= value (:context (sx/evaluate sample-xml (sx/parse-xpath xpath))))))

         "/"
         (get-in sample-xml [:root :content])

         "/catalog"
         (get-in sample-xml [:root :content])

         "/catalog/book/author"
         (mapv x/parse-str ["<author>Gambardella, Matthew</author>"
                            "<author>Ralls, Kim</author>"
                            "<author>Corets, Eva</author>"
                            "<author>Lucy, Steven</author>"
                            "<author>Corets, Eva</author>"
                            "<author>Corets, Eva</author>"])

         ;; namespaces are ignored
         "/x:catalog/abc:book/author1:author"
         (mapv x/parse-str ["<author>Gambardella, Matthew</author>"
                            "<author>Ralls, Kim</author>"
                            "<author>Corets, Eva</author>"
                            "<author>Lucy, Steven</author>"
                            "<author>Corets, Eva</author>"
                            "<author>Corets, Eva</author>"])


         "/catalog/book[@id='bk101']/author"
         [(x/parse-str "<author>Gambardella, Matthew</author>")]

         "/catalog/book[price='5.95']/title"
         (mapv x/parse-str ["<title>Midnight Rain</title>"
                            "<title>Maeve Ascendant</title>"
                            "<title>Oberon's Legacy</title>"
                            "<title>The Sundered Grail</title>"])

         "/catalog/book[price!='5.95']/title"
         (mapv x/parse-str ["<title>XML Developer's Guide</title>"])

         "/catalog/book[1]/author"
         [(x/parse-str "<author>Gambardella, Matthew</author>")]

         "/catalog/book/author[1]"
         [(x/parse-str "<author>Gambardella, Matthew</author>")]

         "/catalog/book[2]/author"
         [(x/parse-str "<author>Ralls, Kim</author>")]

         ;; multiple nested nth
         "/catalog/book[3]/author[1]"
         [(x/parse-str "<author>Corets, Eva</author>")]

         "/catalog/book[3]/author[2]"
         [(x/parse-str "<author>Lucy, Steven</author>")]

         ;; Select index range
         "/catalog/book[1..3]/title"
         (mapv x/parse-str ["<title>XML Developer's Guide</title>"
                            "<title>Midnight Rain</title>"
                            "<title>Maeve Ascendant</title>"])

         "/catalog/book[2..4]/title"
         (mapv x/parse-str ["<title>Midnight Rain</title>"
                            "<title>Maeve Ascendant</title>"
                            "<title>Oberon's Legacy</title>"])

         ;; Range past the end of the list
         "/catalog/book[2..9]/title"
         (mapv x/parse-str ["<title>Midnight Rain</title>"
                            "<title>Maeve Ascendant</title>"
                            "<title>Oberon's Legacy</title>"
                            "<title>The Sundered Grail</title>"])

         ;; Select open ended index range
         "/catalog/book[2..]/title"
         (mapv x/parse-str ["<title>Midnight Rain</title>"
                            "<title>Maeve Ascendant</title>"
                            "<title>Oberon's Legacy</title>"
                            "<title>The Sundered Grail</title>"])

         ;; From very end
         "/catalog/book[5..]/title"
         [(x/parse-str "<title>The Sundered Grail</title>")]

         ;; Using the same index for both
         "/catalog/book[5..5]/title"
         [(x/parse-str "<title>The Sundered Grail</title>")]

         ;; Past the end of the list
         "/catalog/book[7..]/title"
         []
         "/catalog/book[7..9]/title"
         []

         ;; Uses nested elements in subselector
         "/catalog/book[dates/publish_date='2001-09-10']/title"
         [(x/parse-str "<title>The Sundered Grail</title>")]

         ;; Tests nested element subselector that finds multiple elements
         "/catalog/book[author='Lucy, Steven']/title"
         [(x/parse-str "<title>Maeve Ascendant</title>")]

         ;; Select an attribute
         "/catalog/book/dates/publish_date/@importance"
         ["high"
          "low"
          "high"
          "medium"]

         "/catalog/book[dates/publish_date/@importance='high']/title"
         [(x/parse-str "<title>Midnight Rain</title>")
          (x/parse-str "<title>Oberon's Legacy</title>")]


         ;; Doesn't reference a real element
         "/catalog/foo[1]"
         []

         ;; Doesn't reference a real element
         "/catalog/book[contains(@id, 'bk1')]/author"
         (mapv x/parse-str ["<author>Gambardella, Matthew</author>"
                            "<author>Ralls, Kim</author>"
                            "<author>Corets, Eva</author>"
                            "<author>Lucy, Steven</author>"
                            "<author>Corets, Eva</author>"
                            "<author>Corets, Eva</author>"])

         "/catalog/book[contains(genre, 'anta')]/author"
         (mapv x/parse-str ["<author>Ralls, Kim</author>"
                            "<author>Corets, Eva</author>"
                            "<author>Lucy, Steven</author>"
                            "<author>Corets, Eva</author>"
                            "<author>Corets, Eva</author>"]) ))

  (testing "xpaths within context"
    (let [xpath-context (sx/evaluate sample-xml (sx/parse-xpath "/catalog/book[1]"))]

      (are [xpath value]
           (= value (:context (sx/evaluate xpath-context (sx/parse-xpath xpath))))

           "/"
           (get-in sample-xml [:root :content])

           "."
           (:content (x/parse-str "<book id=\"bk101\">
                                  <author>Gambardella, Matthew</author>
                                  <title>XML Developer's Guide</title>
                                  <genre>Computer</genre>
                                  <price>44.95</price>
                                  <dates>
                                  <publish_date>2000-10-01</publish_date>
                                  </dates>
                                  </book>"))
           "author"
           [(x/parse-str "<author>Gambardella, Matthew</author>")]))))

(deftest xpaths-with-data-test
  (testing "xpaths from root"
    (are [xpath value]
         (is (= value (:context (sx/evaluate sample-data-structure (sx/parse-xpath xpath)))))

         "/"
         (get-in sample-data-structure [:root])

         "/books/author"
         ["Gambardella, Matthew"
          "Ralls, Kim"
          "Corets, Eva"
          "Lucy, Steven"
          "Corets, Eva"
          "Corets, Eva"]

         "/books[@id='bk101']/author"
         ["Gambardella, Matthew"]

         "/books[price='5.95']/title"
         ["Midnight Rain"
          "Maeve Ascendant"
          "Oberon's Legacy"
          "The Sundered Grail"]

         "/books[price!='5.95']/title"
         ["XML Developer's Guide"]

         ;; Tests nested element subselector that finds multiple elements
         "/books[author='Lucy, Steven']/title"
         ["Maeve Ascendant"]

         "/books[1]/author"
         ["Gambardella, Matthew"]

         "/books/author[1]"
         ["Gambardella, Matthew"]

         "/books[2]/author"
         ["Ralls, Kim"]

         ;; multiple nested nth
         "/books[3]/author[1]"
         ["Corets, Eva"]

         "/books[3]/author[2]"
         ["Lucy, Steven"]

         ;; Select index range
         "/books[1..3]/title"
         ["XML Developer's Guide"
          "Midnight Rain"
          "Maeve Ascendant"]

         "/books[2..4]/title"
         ["Midnight Rain"
          "Maeve Ascendant"
          "Oberon's Legacy"]

         ;; Range end past the end of the list
         "/books[2..10]/title"
         ["Midnight Rain"
          "Maeve Ascendant"
          "Oberon's Legacy"
          "The Sundered Grail"]

         ;; Select open ended index range
         "/books[2..]/title"
         ["Midnight Rain"
          "Maeve Ascendant"
          "Oberon's Legacy"
          "The Sundered Grail"]

         ;; Past the end of the list
         "/books[7..]/title"
         []
         "/books[7..9]/title"
         []

         ;; Doesn't reference a real element
         "/catalog/foo[1]"
         []))

  (testing "xpaths within context"
    (let [xpath-context (sx/evaluate sample-data-structure (sx/parse-xpath "/books[1]"))]

      (are [xpath value]
           (= value (:context (sx/evaluate xpath-context (sx/parse-xpath xpath))))

           "/"
           (get-in sample-data-structure [:root])

           "."
           [{:genre "Computer"
             :title "XML Developer's Guide"
             :author ["Gambardella, Matthew"]
             :id "bk101"
             :publish_date "2000-10-01"
             :price 44.95}]

           "author"
           ["Gambardella, Matthew"]))))

(deftest xml-attribute-predicates
  (are2 [xpath result-xml]
      (= [(x/parse-str result-xml)]
         (:context (sx/evaluate sample-xml (sx/parse-xpath xpath))))
    "XPath with attribute without namespace predicate"
    "/catalog/book[@id='bk101']/author"
    "<author>Gambardella, Matthew</author>"

    "XPath with attribute with namespace predicate"
    "/catalog/book[@foo:bar='bat']/title"
    "<title>Midnight Rain</title>"))
