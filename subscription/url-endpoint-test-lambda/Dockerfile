# Use an official Python runtime as a parent image
FROM python:3.13-slim

# Install zip utility
RUN apt-get update && apt-get install -y zip

# Set the working directory in the container
WORKDIR /app

# Copy the current directory contents into the container at /app
COPY . /app

# Make sure build.sh is executable
RUN chmod +x build.sh

# Run build.sh when the container launches
CMD ["./build.sh"]

