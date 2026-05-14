{:user
 {;; By defining repositories as a vector, you control the lookup order.
  ;; Leiningen will search for dependencies from top to bottom.
  :repositories
  [["earthdata-nexus" {:url "https://maven.earthdata.nasa.gov/repository/maven-public/"}]

   ;; It's crucial to include central and clojars, as this list
   ;; completely replaces the Leiningen defaults.
   ;;["central" {:url "https://repo1.maven.org/maven2/"
               ;; Good practice: Central doesn't host snapshots.
   ;;            :snapshots false}]
   ;;["clojars" {:url "https://repo.clojars.org/"}]

   ;; The other repositories you had defined
   ;;["sonatype-releases" {:url "https://oss.sonatype.org/content/repositories/releases/"}]
   ["apache-releases" {:url "https://repository.apache.org/content/repositories/releases/"}]]}}
