mkdir package

pip3 install --target ./package boto3 Flask
cd package

zip -r ../deployment_package.zip .
cd ..

zip deployment_package.zip subscription_worker.py
