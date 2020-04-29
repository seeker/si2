#!/bin/bash

for vm in 'vault' 'consul' 'mongodb' 'rabbitmq' 'nomad'
do
vagrant $1 $vm&
done
