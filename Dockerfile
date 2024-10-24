# Create from ubuntu image
FROM ubuntu:20.04

# Change default shell to bash
SHELL ["/bin/bash", "-c"]

# 设置为中国国内源
RUN sed -i s@/ports.ubuntu.com/@/mirrors.aliyun.com/@g /etc/apt/sources.list
RUN sed -i s@/archive.ubuntu.com/@/mirrors.aliyun.com/@g /etc/apt/sources.list
RUN sed -i s@/security.ubuntu.com/@/mirrors.aliyun.com/@g /etc/apt/sources.list

RUN apt-get clean

# Update the image to the latest packages
RUN apt-get update
RUN apt-get upgrade -y

# Install required packages
RUN apt-get install software-properties-common -y
RUN apt-get install zip unzip curl wget tar -y


# Install python
RUN apt-get install python python3-pip -y

# Install C
RUN apt-get install gcc -y

# Install C++
RUN apt-get install g++ -y

# Install java
RUN apt-get install default-jre -y
RUN apt-get install default-jdk -y

# Install nodejs
RUN curl -fsSL https://deb.nodesource.com/setup_16.x | bash -
RUN apt-get install nodejs -y

# Install golang
RUN apt-get install golang -y
ENV GOCACHE /box
ENV GOTMPDIR /box


# Update packages
RUN apt-get clean -y
RUN apt-get autoclean -y
RUN apt-get autoremove -y

# Set default workdir
WORKDIR /box
