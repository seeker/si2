#!/bin/sh

cat /vagrant/ssh/ansible.pub >> /root/.ssh/authorized_keys
cat /vagrant/ssh/ansible.pub >> /home/vagrant/.ssh/authorized_keys
