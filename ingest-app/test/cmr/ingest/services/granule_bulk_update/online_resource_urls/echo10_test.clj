(ns cmr.ingest.services.granule-bulk-update.online-resource-urls.echo10-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.string :as string]
   [cmr.common.util :as util :refer [are3]]
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

(deftest update-online-resource-urls-test--success
  (let [concept {:concept-type :granule
             :metadata echo10-metadata}
        urls [{:from "https://link-1.com"
               :to "https://link-1-updated.com"}]]
    (is (= echo10-metadata-updated (string/trim (echo10/update-online-resource-url concept urls))))))
