;;; run with lein exec create_user.clj

(use '[leiningen.exec :only (deps)])
(deps '[[com.oracle/ojdbc6 "11.2.0.3"]
        [org.clojure/java.jdbc "0.3.3"]]
      :repositories {"releases" "http://devrepo1.dev.echo.nasa.gov/data/dist/projects/echo/mavenrepo/"})
        
(require '[clojure.java.jdbc :as j])

(def db {:classname "oracle.jdbc.driver.OracleDriver"
         :subprotocol "oracle"
         :subname "thin:@localhost:1521:orcl"
         :user "sys as sysdba"
         :password "oracle"})

;(j/query db "select * from dual")

(def drop-user-sql "drop user %%CMR_USER%% cascade")

;;; Values to use in our sql drop statement
(def replacements {"CMR_USER" "METADATA_DB"})

(defn replace-values
  "Given a template string for a sql statement replace the template values with the given values."
  [template key-values]
  (reduce (fn [temp [key,val]]
            (clojure.string/replace temp (str "%%" key "%%") val)) 
          template 
          key-values))

(j/db-do-commands db (replace-values drop-user-sql replacements))