(ns cmr.ingest.services.granule-bulk-update.mime-type.echo10-test
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
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

(deftest update-mime-type-test
  (let [granule {:metadata echo10-metadata}

        updated-gran(echo10/update-mime-type granule {"https://link-1.com" "application/echo10+xml"})

        metadata (:metadata updated-gran)
        online-resources (cx/elements-at-path
                          (xml/parse-str metadata)
                          [:OnlineResources :OnlineResource])]
    (is (not-empty updated-gran))
    (is (string? metadata))
    (is (= 2 (count online-resources)))

    (testing "only the specified link is updated"
      (is (= "application/echo10+xml" (cx/string-at-path (first online-resources) [:MimeType])))
      (is (= "text/html" (cx/string-at-path (second online-resources) [:MimeType]))))))
