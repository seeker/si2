#!/bin/sh

# install required packages
sudo apt-get update
sudo apt-get install rabbitmq-server -y

sh /vagrant/dev/install-consul.sh

# fix ubuntu systemctl bug for rabbitmq
sudo sed -i 's/rabbitmqctl stop/rabbitmqctl shutdown/' /lib/systemd/system/rabbitmq-server.service
sudo systemctl daemon-reload

# configure consul
sudo cp -v /vagrant/dev/rabbitmq.json /etc/consul.d/
sudo cp -v /vagrant/dev/consul-rabbitmq.service /lib/systemd/system/consul.service
sudo systemctl daemon-reload
sudo systemctl enable consul.service
sudo systemctl restart consul.service

sleep 5

consul kv put config/rabbitmq/users/integration GG0H3dwaykUGDjJfYXhm
sudo rabbitmqctl -n rabbit delete_user guest
sudo rabbitmqctl -n rabbit delete_user integration
sudo rabbitmqctl -n rabbit add_user integration $(consul kv get config/rabbitmq/users/integration)
sudo rabbitmqctl -n rabbit set_permissions integration ".*" ".*" ".*"
sudo rabbitmqctl -n rabbit set_user_tags integration administrator

consul kv put config/rabbitmq/users/si2 m6dtSqijKqldu6zHE9hK
sudo rabbitmqctl -n rabbit delete_user si2
sudo rabbitmqctl -n rabbit add_user si2 $(consul kv get config/rabbitmq/users/si2)
sudo rabbitmqctl -n rabbit set_permissions si2 ".*" ".*" ".*"

sudo rabbitmq-plugins enable rabbitmq_management
