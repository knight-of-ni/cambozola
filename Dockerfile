#
# Oracle Java 6 Dockerfile
#
# https://github.com/dockerfile/java
# https://github.com/dockerfile/java/tree/master/oracle-java6
#

# Pull base image.
FROM dockerfile/ubuntu

RUN mkdir /src
RUN mkdir /src/cambozola

VOLUME /src/cambozola 

# Install Java.
RUN \
  echo oracle-java6-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java6-installer ant && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk6-installer

# Define working directory.
WORKDIR /src/cambozola

# Define commonly used JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-6-oracle

# Define default command.
CMD ["bash"]