(ns cmr.ingest.services.granule-bulk-update.opendap.opendap-util
  "Contains functions to facilitate OPeNDAP url granule bulk update."
  (:require
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]))

(def ^:private cloud-pattern
  "Defines the Hyrax-in-the-cloud pattern for OPeNDAP url"
  (re-pattern "https://opendap.*\\.earthdata\\.nasa\\.gov/.*"))

(defn url->opendap-type
  "Returns the OPeNDAP type of the given url. It would be either :cloud or :on-prem for valid urls."
  [url]
  (when url
    (if (re-matches cloud-pattern url)
      :cloud
      :on-prem)))

(defn cloud-url?
  "Returns true if the URL given matches a Hyrax-in-the-cloud (:cloud) URL pattern "
  [url]
  (= :cloud (url->opendap-type url)))

(def on-prem-url?
  "Returns true if the URL given matches :on-prem URL patterns"
  (complement cloud-url?))

(defn is-opendap?
  "Returns true if the given online resource type is of OPeNDAP.
   An online resource is of OPeNDAP type if the resource type contains OPENDAP (case insensitive)"
  [resource-type]
  (if (string? resource-type)
    (some? (re-find #"(?i)OPENDAP" resource-type))
    false))

(defn validate-url
  "Validate the given OPeNDAP url for granule bulk update. It can be no more than two urls
  separated by comma, and no more than one url matches the pattern
  https://opendap.*.earthdata.nasa.gov/* and no more than one that does not match the pattern.
  Returns the parsed urls in a map under keys :cloud and :on-prem with the corresponding urls."
  [url]
  (when url
    (let [urls (map string/trim (string/split url #","))]
      (doseq [url urls]
        (when (string/starts-with? url "s3://")
          (errors/throw-service-errors
           :invalid-data
           [(str "OPeNDAP URL value cannot start with s3://, but was " url)])))
      (if (> (count urls) 2)
        (errors/throw-service-errors
         :invalid-data [(str "Invalid URL value, no more than two urls can be provided: " url)])
        (let [grouped-urls (group-by url->opendap-type urls)]
          (when (> (count (:cloud grouped-urls)) 1)
            (errors/throw-service-errors
             :invalid-data
             [(str "Invalid URL value, no more than one Hyrax-in-the-cloud OPeNDAP url can be provided: "
                   url)]))
          (when (> (count (:on-prem grouped-urls)) 1)
            (errors/throw-service-errors
             :invalid-data
             [(str "Invalid URL value, no more than one on-prem OPeNDAP url can be provided: "
                   url)]))
          grouped-urls)))))

(defn- validate-append-no-conflicting-on-prem
  "Validate there is no on-prem url already present when appending a new on-prem-url
  See also [[validate-append-no-conflicting-cloud]]"
  [current-urls url-map]
  (when-let [on-prem-url (first (filter on-prem-url? current-urls))]
    (when (get url-map :on-prem)
      (errors/throw-service-errors
       :invalid-data
       [(str "Update contains conflict, cannot append on-prem OPeNDAP urls when there is one already present: " on-prem-url)]))))

(defn- validate-append-no-conflicting-cloud
  "Validate there is no cloud url already present when appending a new cloud-url
  See also [[validate-append-no-conflicting-on-prem]]"
  [current-urls url-map]
  (when-let [cloud-url (first (filter cloud-url? current-urls))]
    (when (get url-map :cloud)
      (errors/throw-service-errors
       :invalid-data
       [(str "Update contains conflict, cannot append Hyrax-in-the-cloud OPeNDAP urls when there is one already present: " cloud-url)]))))

(defn validate-append-no-conflicts
  "Takes the current OPeNDAP urls and the map produced by [[validate-url]]
  and checks whether there are any conflicts when appending.

  * current-urls is expected to be a collection
  * urls-map is expected to be in the form of {:on-prem [<url>] :cloud [<url>]}"
  [current-urls urls-map]
  (validate-append-no-conflicting-on-prem current-urls urls-map)
  (validate-append-no-conflicting-cloud current-urls urls-map))
