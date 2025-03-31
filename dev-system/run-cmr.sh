docker run -p 3011:3011 -p 3006:3006 -p 3004:3004\
           -p 3002:3002 -p 3001:3001 -p 3003:3003\
           -p 3009:3009\
           -e CMR_DB_URL="host.docker.internal:1521"\
           cmr-local