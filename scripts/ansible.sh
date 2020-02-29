#!/bin/sh
echo '=== Bootstrapping VM ==='
sh /vagrant/scripts/bootstrap.sh

# Install ansible
apt-get update

echo '=== Installing prerequisite packages ==='
apt-get install -y software-properties-common dos2unix

echo '=== Adding Ansible repository ==='
apt-add-repository --yes --update ppa:ansible/ansible
apt-get update

echo '=== Installing Ansible ==='
apt-get install -y ansible

echo '=== Copy SSH private key ==='
cp -v /vagrant/ssh/ansible* /home/vagrant/.ssh
dos2unix /home/vagrant/.ssh/ansible*
chmod  u=rw,g=,o= /home/vagrant/.ssh/ansible*
chown  vagrant:vagrant /home/vagrant/.ssh/ansible*