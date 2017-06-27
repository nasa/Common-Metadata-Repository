(ns search-relevancy-test.core
 (:require
  [clojure.java.io :as io]
  [clojure.string :as string]
  [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
  [cmr.system-int-test.utils.ingest-util :as ingest-util]))

(def test-collection-formats
 [:iso-smap])

(defn- provider-guids
  "Return a list of guid/provider pairs given a list of provider strings"
  [providers]
  (apply merge
    (for [provider providers]
      {(str "provguid" provider) provider})))

(defn- create-providers
 "Reset and create a provider for each provider found in the test files"
 [test-files]
 (dev-sys-util/reset)
 (let [providers (keys (group-by :provider test-files))
       provider-guids (provider-guids providers)]
   (ingest-util/setup-providers provider-guids)))

(defn- test-files-for-format
  "Returns a set of test collection files in the given format."
  [metadata-format]
  (seq (.listFiles (io/file (io/resource (str "test_files/" (name metadata-format)))))))

(defn- test-files
 "Loops through test files for each format and extracts the file name, provider id,
 and concept id. Returns a list of files with fields extracted."
 []
 (for [format test-collection-formats
       file (test-files-for-format format)
       :let [concept-id (string/replace (.getName file) #"[.][^.]+$" "")]]
   {:file file
    :concept-id concept-id
    :format format
    :provider (string/replace concept-id #".*-" "")}))

(defn ingest-test-files
 "Given a map of file, format, provider, and concept-id, ingest each file"
 [test-files]
 (doseq [test-file test-files
         :let [{:keys [file concept-id provider format]} test-file]]
   (-> (ingest-util/concept :collection provider concept-id format (slurp file))
       (assoc :concept-id concept-id)
       ingest-util/ingest-concept)))

(defn relevancy-test
  []
  (let [test-files (test-files)]
    (create-providers test-files)
    (ingest-test-files test-files)))

(comment
 (relevancy-test))
