(ns cmr.ingest.services.granule-bulk-update.opendap.umm-g
  "Contains functions to update UMM-G granule metadata for OPeNDAP url bulk update."
  (:require
   [cmr.common.services.errors :as errors]
   [cmr.ingest.services.granule-bulk-update.opendap.opendap-util :as opendap-util]))

(def ^:private OPENDAP_RELATEDURL_TYPE
  "RelatedUrl Type of OPenDAP url in UMM-G granule schema"
  "USE SERVICE API")

(def ^:private OPENDAP_RELATEDURL_SUBTYPE
  "RelatedUrl Subtype of OPenDAP url in UMM-G granule schema"
  "OPENDAP DATA")

(defn- is-opendap?
  "Returns true if the given related-url is an OPeNDAP url.
   An UMM-G RelatedUrl is an OPeNDAP url if its Subtype is OPENDAP DATA"
  [related-url]
  (= OPENDAP_RELATEDURL_SUBTYPE (:Subtype related-url)))

(defn- update-opendap-url*
  "Returns the related url with given OPeNDAP url merged into the related-url."
  [url related-url]
  (if url
    (if related-url
      (assoc related-url :URL url :Type OPENDAP_RELATEDURL_TYPE)
      {:URL url :Type OPENDAP_RELATEDURL_TYPE :Subtype OPENDAP_RELATEDURL_SUBTYPE})
    related-url))

(defn- urls->updated-url
  "Returns the updated OPeNDAP type (cloud or on-prem) url based on the opendap urls,
  the opendap type and the url-map that is parsed from the update url value."
  [opendap-type opendap-urls url-map]
  (->> opendap-urls
       (filter #(= opendap-type (opendap-util/url->opendap-type (:URL %))))
       first
       (update-opendap-url* (first (opendap-type url-map)))))

(defn- updated-related-urls
  "Take the RelatedUrls, update any existing opendap url with the given url-map."
  [related-urls url-map]
  (let [opendap-urls (filter is-opendap? related-urls)
        other-urls (remove is-opendap? related-urls)
        cloud-url (urls->updated-url :cloud opendap-urls url-map)
        on-prem-url (urls->updated-url :on-prem opendap-urls url-map)
        updated-urls (conj other-urls cloud-url on-prem-url)]
    (remove nil? updated-urls)))

(defn- appended-related-urls
  "Take the RelatedUrls, append opendap urls with the given url-map. URLs
  already present will be ignored."
  [related-urls url-map]
  (let [opendap-resources (filter is-opendap? related-urls)
        opendap-urls (map :URL opendap-resources)

        _ (when-let [cloud-url (first (filter opendap-util/cloud-url? opendap-urls))]
            (when (seq (get url-map :cloud))
              (errors/throw-service-errors
               :invalid-data
               [(str "Update contains conflict, cannot append Hyrax-in-the-cloud OPeNDAP url when there is one already present: "
                     cloud-url)])))

        _ (when-let [on-prem-url (first (filter opendap-util/on-prem-url? opendap-urls))]
            (when (seq (get url-map :on-prem))
              (errors/throw-service-errors
               :invalid-data
               [(str "Update contains conflict, cannot append on-prem url when there is one already present: "
                     on-prem-url)])))

        other-urls (remove is-opendap? related-urls)
        cloud-url (urls->updated-url :cloud opendap-urls url-map)
        on-prem-url (urls->updated-url :on-prem opendap-urls url-map)
        updated-urls (conj other-urls cloud-url on-prem-url)]
    (remove nil? updated-urls)))

(defn update-opendap-url
  "Takes UMM-G record and grouped OPeNDAP urls in the format of
  {:cloud [<cloud_url>] :on-prem [<on_prem_url>]}.
  The cloud url will overwrite any existing Hyrax-in-the-cloud OPeNDAP url in the UMM-G record;
  the on-prem url will overwrite any existing on-prem OPeNDAP url in the UMM-G record.
  Returns the updated UMM-G record."
  [umm-gran grouped-urls]
  (update umm-gran :RelatedUrls #(updated-related-urls % grouped-urls)))

(defn append-opendap-url
  "Takes UMM-G record and grouped OPeNDAP urls in the format of
  {:cloud [<cloud_url>] :on-prem [<on_prem_url>]}.
  The cloud url will overwrite any existing Hyrax-in-the-cloud OPeNDAP url in the UMM-G record;
  the on-prem url will overwrite any existing on-prem OPeNDAP url in the UMM-G record.
  Returns the updated UMM-G record."
  [umm-gran grouped-urls]
  (update umm-gran :RelatedUrls #(appended-related-urls % grouped-urls)))
