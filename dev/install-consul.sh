#!/bin/sh

# install required package
sudo apt-get update
sudo apt-get install unzip -y

# install consul
wget -nv https://releases.hashicorp.com/consul/1.5.1/consul_1.5.1_linux_amd64.zip
unzip consul*
rm consul_1.5.1_linux_amd64.zip
sudo mv consul /usr/local/bin
sudo mkdir -p /etc/consul.d/
sudo mkdir -p /var/consul/