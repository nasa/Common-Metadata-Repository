;; Contains a helper function that will convert the api docs into HTML
(require '[markdown.core :as md])

(def header
"<!DOCTYPE html>
<html>
<head>
  <meta charset=\"UTF-8\" />
  <title>CMR Search</title>
    <!--[if lt IE 9 ]>
      <script src=\"http://html5shiv.googlecode.com/svn/trunk/html5.js\"></script>
      <![endif]-->
      <link rel=\"stylesheet\" href=\"bootstrap.min.css\">
      <script src=\"jquery.min.js\"></script>
      <script src=\"bootstrap.min.js\"></script>
  </head>
  <body lang=\"en-US\">
    <div class=\"container\">
    <h1>CMR Search</h1>")

(def footer "</div></body></html>")

(def docs-source "api_docs.md")
(def docs-target "resources/public/site/api_docs.html")

(defn generate
  []
  (println "Generating" docs-target "from" docs-source)
  (spit docs-target (str header (md/md-to-html-string (slurp docs-source)) footer))
  (println "Done"))


(generate)