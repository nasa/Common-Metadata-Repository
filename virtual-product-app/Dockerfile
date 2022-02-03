FROM clojure:openjdk-8-lein

COPY target/cmr-virtual-product-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3009

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-virtual-product-app-0.1.0-SNAPSHOT-standalone.jar" "clojure.main" "-m" "cmr.virtual-product.runner"
