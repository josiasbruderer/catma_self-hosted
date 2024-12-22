# Introduction

CATMA is a web based application for text annotation and analysis.

It has two main components: an annotation component that allows the user to annotate text and an analysis component that supports pattern analysis of text and annotations in combination through a custom but easy query language and a data visualization facility based on [Vega](https://vega.github.io/vega/). 

It also allows the project-centric management of text corpora, annotation collections, tagsets and team members. 

CATMA uses a Gitlab instance as its backend to store and manage the project's resources and team. All resources are versioned and available through either the CATMA web interface or the Gitlab REST API.

## TODO

- [x] search and replace hardcoded links in sourcecode 
- [x] gitlab smtp not working

# Prerequirements

- CPU: 4 vCPU (mind. 2 vCPU)
- Memory: 8GB (mind. 4GB)
- Disk: 60GB (mind. 20GB)
- OS: Debian 11 (bullseye)
- Packages: JDK 11, Tomcat 9
- Firewall: TCP/22, TCP/80, TCP/443
- DNS: app.[your-hostname].[tld]; git.[your-hostname].[tld]

# Installation & Setup

## Summary

CATMA uses a stock Gitlab installation with the following mandatory configuration:

- Default dev ops pipeline needs to be switched off.
- Default branch protection needs to be "partially protected". #WIP: settings not existing anymore?

The Java-based CATMA app can be build with maven and will be run with Tomcat.

## Walkthrough

This guide walks you through the installation of CATMA (inclusive all dependencies).

1. General preparations

```bash
#check swap and create if non existent
cat /proc/swaps
sudo fallocate -l 8G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
sudo cp /etc/fstab /etc/fstab.bak
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'TERM=xterm-256color' | sudo tee -a /etc/environment
```


2. Install requirements

```bash
sudo apt update && sudo apt upgrade
sudo apt install git nano nmon ncdu ca-certificates curl

#Install legacy Java (JDK 8) which is required to build CATMA dependencies...
echo "deb http://ftp.de.debian.org/debian sid main" | sudo tee -a /etc/apt/sources.list 
echo "deb-src http://ftp.de.debian.org/debian sid main" | sudo tee -a /etc/apt/sources.list
sudo apt update
sudo apt install openjdk-8-jdk
sudo mkdir /usr/lib/jvm/lib
sudo ln -s /usr/lib/jvm/java-8-openjdk-amd64/lib/tools.jar /usr/lib/jvm/lib/tools.jar
sudo nano /etc/apt/sources.list
#remove debian sid repositories from /etc/apt/sources.list

#Normal up to date packages
sudo apt update
sudo apt install openjdk-11-jdk openjdk-11-doc maven  nginx certbot python3-certbot-nginx

#clean up
sudo apt autoremove

#verification
java -version #should report 11.x.x
mvn -version #should report 3.x.x

#register java home
sudo echo "JAVA_HOME=$(readlink -f /usr/bin/javac | sed "s:/bin/javac::")" | sudo tee -a /etc/environment
source /etc/environment
echo $JAVA_HOME

#dont ask why, but we need docker to build a dependency
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo echo "DOCKER_HOST=unix:///var/run/docker.sock" | sudo tee -a /etc/environment
source /etc/environment
echo $DOCKER_HOST
sudo usermod -aG docker $USER

#log out and in again! 

docker info
```


3. Build packages

```bash
#dependency: gitlab4j-api
git clone https://github.com/forTEXT/gitlab4j-api.git
cd gitlab4j-api
git checkout gitlab4j-api-5.0.1-CATMA-v2
mvn clean install -DskipTests
cd ~

#dependency: vaadin-maven-plugin
git clone https://github.com/forTEXT/vaadin-maven-plugin.git
cd vaadin-maven-plugin
mvn clean install -DskipTests
cd ~

#dependency: maven-plugin
git clone https://github.com/vaadin/maven-plugin.git
cd maven-plugin
git checkout upstream/gwt-maven-plugin-2.3.0
mvn clean install -DskipTests #this probably fails... but just continue!
cd ~

#dependency: serverside-elements
git clone https://github.com/forTEXT/serverside-elements.git
cd serverside-elements
nano pom.xml
#remove: module "elements-demo"
mvn clean install -DskipTests
cd ~

#application: catma
git clone https://github.com/josiasbruderer/catma_self-hosted.git
cd catma_self-hosted
#WIP: REPLACE ALL [YOUR-DOMAIN] with your domain name!
mvn clean compile package -DskipTests
cd ~
```


4. configure requirements

```bash
#tomcat
sudo useradd -m -d /opt/tomcat -U -s /bin/bash tomcat
sudo usermod -aG tomcat $USER
wget https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.98/bin/apache-tomcat-9.0.98.tar.gz
sudo tar xzvf apache-tomcat-9*tar.gz -C /opt/tomcat --strip-components=1
sudo chown -R tomcat:tomcat /opt/tomcat/
sudo chmod -R u+x /opt/tomcat/bin
rm apache-tomcat-9*tar.gz
sudo cp ~/catma_self-hosted/helpers/tomcat.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable tomcat
sudo systemctl start tomcat

#nginx and letsencrypt
sudo cp ~/catma_self-hosted/helpers/nginx_default /etc/nginx/sites-available/default
sudo nano /etc/nginx/sites-available/default
#replace [YOUR-DOMAIN] with your real domain
sudo systemctl enable nginx
sudo systemctl start nginx
sudo certbot --nginx --email [YOUR-ADMIN-EMAIL] -d [YOUR-DOMAIN] -d app.[YOUR-DOMAIN] -d git.[YOUR-DOMAIN]

#catma-app
sudo mkdir -p /opt/catma-app/repo /opt/catma-app/temp /opt/catma-app/db /opt/catma-app/source /opt/catma-app/backup /opt/catma-app/web
sudo cp ~/catma_self-hosted/target/catma-7.0-SNAPSHOT.war /opt/catma-app/source/
sudo cp ~/catma_self-hosted/helpers/catma.properties /opt/catma-app/source
sudo cp ~/catma_self-hosted/helpers/web/* /opt/catma-app/web/
```


5. setup gitlab

```bash
curl https://packages.gitlab.com/install/repositories/gitlab/gitlab-ce/script.deb.sh | sudo bash
sudo EXTERNAL_URL="http://localhost:8484" apt-get install gitlab-ce
sudo nano /etc/gitlab/gitlab.rb
#modify smtp settings and have a look at gitlab_email_* as well!
#set external url
#set: puma['port'] = 8089
#set: external_url 'https://git.[YOUR-DOMAIN]'
#set: nginx['listen_port'] = 8080  # Or any port GitLab's internal nginx is using
#set: nginx['listen_https'] = false
#set: nginx['proxy_set_headers'] = {
 "Host" => "$http_host_with_default",
 "X-Real-IP" => "$remote_addr",
 "X-Forwarded-For" => "$proxy_add_x_forwarded_for",
 "X-Forwarded-Proto" => "https",
 "X-Forwarded-Ssl" => "on",
 "Upgrade" => "$http_upgrade",
 "Connection" => "$connection_upgrade"
}


sudo gitlab-ctl reconfigure

sudo cat /etc/gitlab/initial_root_password
#store root pw in a safe place

#WIP: generate personal access token for root via cli
#WIP: turn off default dev ops pipeline via cli
#WIP: set default branch protection to partially protected via cli
```


6. prepare config for CATMA

- generate recaptcha keys: https://cloud.google.com/recaptcha/docs/create-key-website?hl=de
- 

```bash
sudo nano /opt/catma-app/source/catma.properties
#modify...
sudo chown -R tomcat:tomcat /opt/catma-app/
```


7. Spin up CATMA App

```bash
sudo mv /opt/tomcat/webapps/ROOT /opt/tomcat/webapps/ROOT_backup
sudo cp /opt/catma-app/source/catma-7.0-SNAPSHOT.war /opt/tomcat/webapps/ROOT.war
sudo cp /opt/catma-app/source/catma.properties /opt/tomcat/webapps/ROOT/
sudo chown -R tomcat:tomcat /opt/tomcat/

sudo systemctl restart tomcat
```
