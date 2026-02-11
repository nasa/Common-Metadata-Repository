(ns cmr.ingest.test.services.granule-bulk-update.utils.echo10-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is testing]]
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.ingest.services.granule-bulk-update.utils.echo10 :as echo10-util]))

(def echo10-metadata-place-at-end 
  (string/trim "
<Granule>
  <GranuleUR>20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</GranuleUR>
</Granule>"))

(def echo10-metadata-place-in-the-middle
  (string/trim "
<Granule>
  <GranuleUR>20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</GranuleUR>
  <OnlineResources>
    <OnlineResource>
      <URL>https://link-1.com</URL>
      <Description>The link to be updated</Description>
      <Type>USE SERVICE API : OPENDAP DATA</Type>
      <MimeType>application/test+json</MimeType>
    </OnlineResource>
  </OnlineResources>
</Granule>"))

(def echo10-metadata-remove
  (string/trim "
<Granule>
  <GranuleUR>20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</GranuleUR>
  <OnlineResources>
    <OnlineResource>
      <URL>https://link-1.com</URL>
      <Description>The link to be updated</Description>
      <Type>USE SERVICE API : OPENDAP DATA</Type>
      <MimeType>application/test+json</MimeType>
    </OnlineResource>
    <OnlineResource>
      <URL>https://link-2.com</URL>
      <Description>The second link to be updated</Description>
      <Type>USE SERVICE API : OPENDAP DATA2</Type>
      <MimeType>application/test+json2</MimeType>
    </OnlineResource>
  </OnlineResources>
</Granule>"))

(deftest add-tree-at-the-end-test
  (let [parsed (cx/parse-str echo10-metadata-place-at-end)
        items (echo10-util/links->provider-browse-urls [{:URL "http://some-test-link.com"
                                                         :MimeType "application/test"
                                                         :Description "Some dummy URL."
                                                         :Size 1}])]
    (testing "Testing when right-loc is nil and insert at the end."
      (is (string/includes?
           (xml/indent-str (echo10-util/add-in-tree parsed :AssociatedBrowseImageUrls items))
           "</AssociatedBrowseImageUrls>\n</Granule>")))))

(deftest add-tree-in-the-middle-test
  (let [parsed (cx/parse-str echo10-metadata-place-in-the-middle)
        items (echo10-util/links->online-access-urls [{:URL "http://some-test-link.com"
                                                       :MimeType "application/test"
                                                       :Description "Some dummy URL."}])]
    (testing "Testing when element after current element but before others."
      (is (string/includes?
           (xml/indent-str (echo10-util/add-in-tree parsed :OnlineAccessURLs items))
           "</OnlineAccessURLs>\n   <OnlineResources>")))))

(deftest add-tree-appending-child-test
  (let [parsed (cx/parse-str echo10-metadata-place-in-the-middle)
        items (echo10-util/links->online-resources [{:URL "http://some-test-link.com"
                                                     :MimeType "application/test"
                                                     :Type "SomeType"
                                                     :Description "Some dummy URL."}])]
    (testing "Testing when inserting at element at the end of all children."
      (is (string/includes?
           (xml/indent-str (echo10-util/add-in-tree parsed :OnlineResources items))
           "<MimeType>application/test</MimeType>\n      </OnlineResource>\n   </OnlineResources>\n</Granule>")))))

(deftest remove-element-from-tree-test
  (let [parsed (cx/parse-str echo10-metadata-remove)]

    (testing "Testing Nothing to remove"
      (let [items [{:URL "http://some-test-link.com"
                    :MimeType "application/test"
                    :Type "SomeType"
                    :Description "Some dummy URL."}]
            actual (xml/indent-str
                    (echo10-util/remove-from-tree parsed [:OnlineResources :OnlineResource] items))]
        (is (string/includes? actual "https://link-1.com"))
        (is (string/includes? actual "https://link-2.com"))))
    
    (testing "Testing removing the first online resource."
      (let [items [{:URL "https://link-1.com"}]
            actual (xml/indent-str
                    (echo10-util/remove-from-tree parsed [:OnlineResources :OnlineResource] items))]
        (is (string/includes? actual "https://link-2.com"))
        (is (not (string/includes? actual "https://link-1.com")))))
    
    (testing "Testing removing the last online resource."
      (let [items [{:URL "https://link-2.com"}]
            actual (xml/indent-str
                    (echo10-util/remove-from-tree parsed [:OnlineResources :OnlineResource] items))]
        (is (string/includes? actual "https://link-1.com"))
        (is (not (string/includes? actual "https://link-2.com")))))

       (testing "Testing removing both online resources."
         (let [items [{:URL "https://link-2.com"}
                      {:URL "https://link-1.com"}]
               actual (xml/indent-str
                       (echo10-util/remove-from-tree parsed [:OnlineResources :OnlineResource] items))]
           (is (not (string/includes? actual "https://link-1.com")))
           (is (not (string/includes? actual "https://link-2.com")))))))
