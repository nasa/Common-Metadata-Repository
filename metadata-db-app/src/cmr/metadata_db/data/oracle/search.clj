(ns cmr.metadata-db.data.oracle.search

(:require [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.services.errors :as errors]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [cmr.metadata-db.services.util :as util]
            [cmr.metadata-db.data.oracle.sql-helper :as sh]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.common.date-time-parser :as p]
            [clj-time.format :as f]
            [clj-time.coerce :as cr]
            [clj-time.core :as t]
            [cmr.common.concepts :as cc]
            [cmr.oracle.connection :as oracle]
            [cmr.metadata-db.data.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import cmr.oracle.connection.OracleStore
           java.util.zip.GZIPInputStream
           java.util.zip.GZIPOutputStream
           java.io.ByteArrayOutputStream
           java.sql.Blob
           oracle.sql.TIMESTAMPTZ))

