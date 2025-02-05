#zip deployment_package.zip subscription_worker.py sns.py part1_docker part_docker
zip -r deployment_package.zip src Dockerfile -x "src/__pycache__/*"
