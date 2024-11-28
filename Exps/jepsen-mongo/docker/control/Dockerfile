FROM debian:buster
MAINTAINER jake@apache.org

ENV LEIN_ROOT=true

#
# Jepsen dependencies
#

RUN sed -i 's/deb.debian.org/mirrors.ustc.edu.cn/g' /etc/apt/sources.list
RUN sed -i 's/security.debian.org/mirrors.ustc.edu.cn/g' /etc/apt/sources.list

RUN apt-get -y -q update && \
    apt-get install -qy openjdk-11-jdk-headless \
    libjna-java \
    vim \
    emacs \
    git \
    htop \
    screen \
    pssh \
    curl \
    wget \
    gnuplot \
    graphviz \
    dos2unix 

ADD lein /usr/bin
RUN chmod +x /usr/bin/lein

RUN mkdir -p /root/.lein/self-installs
ADD leiningen-2.10.0-standalone.jar /root/.lein/self-installs/leiningen-2.10.0-standalone.jar
# RUN lein self-install # manually add this file

# without --dev flag up.sh copies jepsen to these subfolders
# with --dev flag they are empty until mounted
COPY jepsen/jepsen /jepsen/jepsen/
RUN if [ -f /jepsen/jepsen/project.clj ]; then cd /jepsen/jepsen && lein install; fi
COPY jepsen /jepsen/

ADD ./bashrc /root/.bashrc
ADD ./init.sh /init.sh
RUN dos2unix /init.sh /root/.bashrc \
    && chmod +x /init.sh

CMD /init.sh
