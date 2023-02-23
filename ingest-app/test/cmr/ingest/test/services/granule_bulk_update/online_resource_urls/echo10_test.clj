(ns cmr.ingest.test.services.granule-bulk-update.online-resource-urls.echo10-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.ingest.services.granule-bulk-update.online-resource-url.echo10 :as echo10]))

(def echo10-metadata (string/trim "
<Granule>
    <GranuleUR>20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</GranuleUR>
    <OnlineAccessURLs>
        <OnlineAccessURL>
            <URL>https://podaac-tools.jpl.nasa.gov/drive/files/allData/ghrsst/data/GDS2/L4/GLOB/JPL/MUR/v4.1/2002/153/20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</URL>
            <URLDescription>The HTTP location for the granule.</URLDescription>
        </OnlineAccessURL>
        <OnlineAccessURL>
            <URL>https://link-3.com</URL>
            <URLDescription>The HTTP location for the granule.</URLDescription>
            <MimeType>text/html</MimeType>
        </OnlineAccessURL>
    </OnlineAccessURLs>
    <OnlineResources>
        <OnlineResource>
            <URL>https://link-1.com</URL>
            <Description>The link to be updated</Description>
            <Type>USE SERVICE API : OPENDAP DATA</Type>
            <MimeType>application/error+json</MimeType>
        </OnlineResource>
        <OnlineResource>
            <URL>https://link-2.com</URL>
            <Description>The link to be ignored</Description>
            <Type>documentation</Type>
            <MimeType>text/html</MimeType>
        </OnlineResource>
    </OnlineResources>
    <DataFormat>NETCDF</DataFormat>
</Granule>"))

(def echo10-metadata-updated (string/trim "
<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</GranuleUR>
   <OnlineAccessURLs>
      <OnlineAccessURL>
         <URL>https://podaac-tools.jpl.nasa.gov/drive/files/allData/ghrsst/data/GDS2/L4/GLOB/JPL/MUR/v4.1/2002/153/20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</URL>
         <URLDescription>The HTTP location for the granule.</URLDescription>
      </OnlineAccessURL>
      <OnlineAccessURL>
         <URL>https://link-3.com</URL>
         <URLDescription>The HTTP location for the granule.</URLDescription>
         <MimeType>text/html</MimeType>
      </OnlineAccessURL>
   </OnlineAccessURLs>
   <OnlineResources>
      <OnlineResource>
         <URL>https://link-1-updated.com</URL>
         <Description>The link to be updated</Description>
         <Type>USE SERVICE API : OPENDAP DATA</Type>
         <MimeType>application/error+json</MimeType>
      </OnlineResource>
      <OnlineResource>
         <URL>https://link-2.com</URL>
         <Description>The link to be ignored</Description>
         <Type>documentation</Type>
         <MimeType>text/html</MimeType>
      </OnlineResource>
   </OnlineResources>
   <DataFormat>NETCDF</DataFormat>
</Granule>"))

(def echo10-browse-metadata (string/trim "
<Granule>
  <GranuleUR>20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</GranuleUR>
  <AssociatedBrowseImageUrls>
    <ProviderBrowseUrl>
      <URL>https://link-4.com</URL>
      <FileSize>20</FileSize>
      <Description>A test Browse URL 1 for this test.</Description>
      <MimeType>html/text4</MimeType>
    </ProviderBrowseUrl>
    <ProviderBrowseUrl>
      <URL>https://link-5.com</URL>
      <FileSize>20</FileSize>
      <Description>A test Browse URL 2 for this test.</Description>
      <MimeType>html/text5</MimeType>
    </ProviderBrowseUrl>
  </AssociatedBrowseImageUrls>
</Granule>"))

(deftest update-online-resource-urls-test
  (let [concept {:concept-type :granule
                 :metadata echo10-metadata
                 :revision-id 1}
        urls [{:from "https://link-1.com"
               :to "https://link-1-updated.com"}]
        access-urls [{:from "https://link-3.com" 
                      :to "https://link-3-updated.com"}]
        access-upated (-> echo10-metadata-updated
                          (string/replace "link-3" "link-3-updated")
                          (string/replace "link-1-updated" "link-1"))
        user-id "george"
        actual-online-resources (echo10/update-url concept urls [:OnlineResources :OnlineResource] user-id)
        actual-online-access (echo10/update-url concept access-urls [:OnlineAccessURLs :OnlineAccessURL] user-id)]
    (is (= echo10-metadata-updated (string/trim (:metadata actual-online-resources))))
    (is (= access-upated (string/trim (:metadata actual-online-access)))))

  (let [concept {:concept-type :granule
                 :metadata echo10-browse-metadata
                 :revision-id 1}
        browse-urls [{:from "https://link-4.com"
                      :to "https://link-4-updated.com"}]
        user-id "george"
        actual-browse (echo10/update-url concept browse-urls [:AssociatedBrowseImageUrls :ProviderBrowseUrl] user-id)]
    (is (string/includes?
         (:metadata actual-browse)
         "<URL>https://link-4-updated.com</URL>"))))

(deftest add-online-resource-urls-test
  (let [concept {:concept-type :granule
                 :metadata echo10-metadata
                 :revision-id 1}
        browse-urls [{:URL "http://some-test-link.com"
                     :MimeType "application/test"
                     :Description "Some dummy URL."
                     :Size 1}]
        user-id "george"
        actual (echo10/add-url concept browse-urls [:AssociatedBrowseImageUrls :ProviderBrowseUrl] user-id)]
    (is (string/includes?
         (string/trim (:metadata actual))
         "<URL>http://some-test-link.com</URL>"))))

(deftest remove-browse-resource-urls-test
  (let [concept {:concept-type :granule
                 :metadata echo10-browse-metadata
                 :revision-id 1}
        browse-urls [{:URL "https://link-4.com"}]
        user-id "george"
        actual (echo10/remove-url concept browse-urls [:AssociatedBrowseImageUrls :ProviderBrowseUrl] user-id)
        actual-metadata (string/trim (:metadata actual))]
    (is (string/includes? actual-metadata "https://link-5.com"))
    (is (not (string/includes? actual-metadata "https://link-4.com")))))
