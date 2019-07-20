#!/bin/sh

# install required packages
sudo apt-get update
sudo apt-get install mongodb -y

sh /vagrant/dev/install-consul.sh

# setup mongodb configuration
sudo sed -i 's|bind_ip = 127.0.0.1|bind_ip = 0.0.0.0|' /etc/mongodb.conf
sudo systemctl enable mongodb
sudo systemctl restart mongodb

# configure consul
sudo cp /vagrant/dev/mongodb.json /etc/consul.d/
sudo cp /vagrant/dev/consul-mongodb.service /lib/systemd/system/consul.service
sudo systemctl daemon-reload
sudo systemctl enable consul.service
sudo systemctl start consul.service
