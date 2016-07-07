# This script will install Elasticsearch marvel locally. This is mostly so that you can use the sense
# plugin on local data. Visit http://localhost:9210/_plugin/marvel/sense/index.html after running this.
# Note that this disables running marvel statistics gathering so that only the sense part of marvel works.
# Run this inside the dev-system folder
mkdir -p plugins/marvel
cd plugins/marvel
curl -O https://download.elasticsearch.org/elasticsearch/marvel/marvel-1.3.0.tar.gz
tar -zxvf marvel-1.3.0.tar.gz

# Rename the jar file so that marvel won't attempt to run. This prevents exceptions with "failed to load marvel_index_template.json"
mv marvel-1.3.0.jar marvel-1.3.0.jar_ignore