(ns cmr.ingest.services.granule-bulk-update.mime-type.echo10-test
  (:require
   [clojure.data.xml :as xml]
   [clojure.test :refer [deftest testing is]]
   [cmr.common.util :as util :refer [are3]]
   [cmr.common.xml :as cx]
   [cmr.ingest.services.granule-bulk-update.mime-type.echo10 :as echo10]))

(def echo10-metadata "
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
</Granule>")

(def echo10-metadata-no-access "
<Granule>
    <GranuleUR>20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</GranuleUR>
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
</Granule>")

(def echo10-metadata-no-resources "
<Granule>
    <GranuleUR>20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</GranuleUR>
    <OnlineAccessURLs>
        <OnlineAccessURL>
            <URL>https://podaac-tools.jpl.nasa.gov/drive/files/allData/ghrsst/data/GDS2/L4/GLOB/JPL/MUR/v4.1/2002/153/20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</URL>
            <URLDescription>The HTTP location for the granule.</URLDescription>
        </OnlineAccessURL>
        <OnlineAccessURL>
            <URL>https://link-3</URL>
            <URLDescription>The HTTP location for the granule.</URLDescription>
            <MimeType>text/html</MimeType>
        </OnlineAccessURL>
    </OnlineAccessURLs>
    <DataFormat>NETCDF</DataFormat>
</Granule>")

(def echo10-metadata-no-resources-no-access "
<Granule>
    <GranuleUR>20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc</GranuleUR>
    <DataFormat>NETCDF</DataFormat>
</Granule>")

(deftest update-mime-type-test
  (testing "single link provided to update"
    (let [granule {:metadata echo10-metadata}

          updated-gran (echo10/update-mime-type granule
                                                [{:URL "https://link-1.com"
                                                  :MimeType "application/echo10+xml"}])

          metadata (:metadata updated-gran)
          online-resources (cx/elements-at-path
                            (xml/parse-str metadata)
                            [:OnlineResources :OnlineResource])
          access-urls (cx/elements-at-path
                       (xml/parse-str metadata)
                       [:OnlineAccessURLs :OnlineAccessURL])]

      (is (not-empty updated-gran))
      (is (string? metadata))
      (is (= 2 (count online-resources)))
      (is (= 2 (count access-urls)))

      (testing "only the specified link is updated"
        (is (= "application/echo10+xml" (cx/string-at-path (first online-resources) [:MimeType])))
        (is (= "text/html" (cx/string-at-path (second online-resources) [:MimeType]))))))

  (testing "multiple links provided"
    (let [granule {:metadata echo10-metadata}

          updated-gran (echo10/update-mime-type granule
                                                [{:URL "https://link-1.com"
                                                  :MimeType "application/echo10+xml"}
                                                 {:URL "https://link-3.com"
                                                  :MimeType "application/gzip"}])

          metadata (:metadata updated-gran)
          online-resources (cx/elements-at-path
                            (xml/parse-str metadata)
                            [:OnlineResources :OnlineResource])
          access-urls (cx/elements-at-path
                       (xml/parse-str metadata)
                       [:OnlineAccessURLs :OnlineAccessURL])]

      (testing "the specified links are updated"
        (is (= "application/echo10+xml" (cx/string-at-path (first online-resources) [:MimeType])))
        (is (= "application/gzip" (cx/string-at-path (second access-urls) [:MimeType]))))))

  (testing "throws exceptions when duplicate URLs are provided"
    (let [granule {:metadata echo10-metadata}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Update failed - duplicate URLs provided.*"
                            (echo10/update-mime-type
                             granule
                             [{:URL "https://link-1.com"
                               :MimeType "application/echo10+xml"}
                              {:URL "https://link-1.com"
                               :MimeType "application/gzip"}])))))

  (testing "Handles xml regardless of which elements are present where"
    (are3 [metadata]
          (let [granule {:metadata metadata}
                output (echo10/update-mime-type
                        granule
                        [{:URL "https://foo.com" :MimeType "application/baz+xml"}])]
            (is (string? (:metadata output))))

          "Both OnlineResources and OnlineAccessURLs present" echo10-metadata
          "Only OnlineAccessURLs" echo10-metadata-no-resources
          "Only OnlineResoruces" echo10-metadata-no-access
          "No Online elements" echo10-metadata-no-resources-no-access)))
