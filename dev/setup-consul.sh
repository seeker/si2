#!/bin/sh

sh /vagrant/dev/install-consul.sh

sudo cp -v  /vagrant/dev/consul-server.service /lib/systemd/system/consul.service
sudo systemctl daemon-reload
sudo systemctl enable consul.service
sudo systemctl restart consul.service
